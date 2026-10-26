package com.z.liveconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.z.liveconfig.common.BizException;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.entity.Env;
import com.z.liveconfig.entity.FeatureFlag;
import com.z.liveconfig.entity.FlagChangeLog;
import com.z.liveconfig.entity.PlatformUser;
import com.z.liveconfig.mapper.EnvMapper;
import com.z.liveconfig.mapper.FeatureFlagMapper;
import com.z.liveconfig.mapper.FlagChangeLogMapper;
import com.z.liveconfig.push.ConfigChangePublisher;
import com.z.liveconfig.security.AccessGuard;
import com.z.liveconfig.service.ConfigVersionService;
import com.z.liveconfig.service.IHistoryService;
import com.z.liveconfig.vo.ChangeLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements IHistoryService {

    private final FlagChangeLogMapper logMapper;
    private final EnvMapper envMapper;
    private final FeatureFlagMapper flagMapper;
    private final AccessGuard guard;
    private final ObjectMapper mapper;
    private final ConfigVersionService versionService;
    private final ConfigChangePublisher publisher;

    @Override
    public ChangeLogVO record(App app, FeatureFlag flag, PlatformUser actor, String action,
                              Object before, Object after, long configVersion) {
        FlagChangeLog log = new FlagChangeLog();
        log.setEnvId(flag.getEnvId());
        log.setFlagKey(flag.getFlagKey());
        log.setOperator(actor == null ? "system" : actor.getEmail());
        log.setAction(action);
        log.setBeforeJson(json(before));
        log.setAfterJson(json(after));
        log.setConfigVersion(configVersion);
        logMapper.insert(log);
        return toVO(log);
    }

    @Override
    public List<ChangeLogVO> page(String appKey, String envKey, String flagKey,
                                  int pageNum, int pageSize) {
        App app = guard.requireApp(appKey);
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        return logMapper.selectPage(new Page<>(safePage, safeSize),
                        briefQuery(guard.requireEnv(app.getId(), envKey).getId(), flagKey)).getRecords()
                .stream().map(this::toVO).toList();
    }

    private static LambdaQueryWrapper<FlagChangeLog> briefQuery(Long envId, String flagKey) {
        return new LambdaQueryWrapper<FlagChangeLog>()
                .eq(FlagChangeLog::getEnvId, envId)
                .eq(flagKey != null && !flagKey.isBlank(), FlagChangeLog::getFlagKey, flagKey)
                .orderByDesc(FlagChangeLog::getId);
    }

    private ChangeLogVO toVO(FlagChangeLog full) {
        ChangeLogVO vo = new ChangeLogVO();
        vo.setId(full.getId());
        vo.setFlagKey(full.getFlagKey());
        vo.setActor(full.getOperator());
        vo.setAction(full.getAction());
        vo.setConfigVersion(full.getConfigVersion());
        vo.setCreatedAt(full.getCreatedAt());
        vo.setBeforeJson(full.getBeforeJson());
        vo.setAfterJson(full.getAfterJson());
        return vo;
    }

    @Override
    public long total(String appKey, String envKey, String flagKey) {
        App app = guard.requireApp(appKey);
        return logMapper.selectCount(briefQuery(guard.requireEnv(app.getId(), envKey).getId(), flagKey));
    }

    /**
     * 回滚不是"删掉后面的历史"，而是把那条快照的值再写一次：
     * 历史只增，configVersion 继续前进，SDK 侧看到的仍然是单调递增。
     */
    @Override
    @Transactional
    public ChangeLogVO rollback(Long logId) {
        FlagChangeLog target = logMapper.selectById(logId);
        if (target == null) {
            throw BizException.notFound("历史记录不存在：" + logId);
        }
        if (target.getAfterJson() == null) {
            throw BizException.badRequest("该记录没有可恢复的快照");
        }
        Env env = envMapper.selectById(target.getEnvId());
        if (env == null) {
            throw BizException.notFound("环境已不存在，无法回滚");
        }
        App app = guard.requireAppById(env.getAppId());
        guard.requireWriteValue(env);

        FeatureFlag flag = flagMapper.selectOne(new LambdaQueryWrapper<FeatureFlag>()
                .eq(FeatureFlag::getEnvId, env.getId())
                .eq(FeatureFlag::getFlagKey, target.getFlagKey())
                .last("LIMIT 1"));
        if (flag == null) {
            throw BizException.notFound("开关已被删除，无法回滚：" + target.getFlagKey());
        }

        JsonNode snapshot = readTree(target.getAfterJson());
        String before = flag.getDefaultValue();
        if (snapshot.hasNonNull("defaultValue")) {
            flag.setDefaultValue(snapshot.get("defaultValue").asText());
        }
        if (snapshot.hasNonNull("enabled")) {
            flag.setEnabled(snapshot.get("enabled").asBoolean());
        }
        flag.setVersion(flag.getVersion() + 1);
        flagMapper.updateById(flag);

        Env bumped = versionService.bump(env);
        ChangeLogVO created = record(app, flag, guard.currentUser(), "ROLLBACK",
                Map.of("defaultValue", before, "fromLogId", logId),
                Map.of("defaultValue", flag.getDefaultValue(), "enabled", flag.getEnabled()),
                bumped.getConfigVersion());
        publisher.published(bumped, app.getAppKey(), List.of(flag.getFlagKey()));
        return created;
    }

    private JsonNode readTree(String raw) {
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            throw BizException.badRequest("历史快照不是合法 JSON：" + e.getMessage());
        }
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw BizException.badRequest("快照序列化失败：" + e.getMessage());
        }
    }
}
