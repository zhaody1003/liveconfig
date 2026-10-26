package com.z.liveconfig.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.z.liveconfig.common.BizException;
import com.z.liveconfig.dto.AssignRoleDTO;
import com.z.liveconfig.dto.InviteMemberDTO;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.entity.AppMember;
import com.z.liveconfig.entity.AppRole;
import com.z.liveconfig.entity.Env;
import com.z.liveconfig.entity.PlatformUser;
import com.z.liveconfig.mapper.AppMemberMapper;
import com.z.liveconfig.mapper.AppRoleMapper;
import com.z.liveconfig.mapper.EnvMapper;
import com.z.liveconfig.mapper.PlatformUserMapper;
import com.z.liveconfig.security.AccessGuard;
import com.z.liveconfig.security.Perm;
import com.z.liveconfig.service.IMemberService;
import com.z.liveconfig.vo.MemberRowVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements IMemberService {

    private final AppMemberMapper memberMapper;
    private final PlatformUserMapper userMapper;
    private final AppRoleMapper roleMapper;
    private final EnvMapper envMapper;
    private final AccessGuard guard;

    @Override
    public List<MemberRowVO> matrix(String appKey) {
        App app = guard.requireApp(appKey);
        guard.require(app.getId(), null, Perm.MEMBER_MANAGE);

        // 一张 app_member 同时给出"有哪些成员"和"每个成员在每个环境的角色"，两次单表查询即可
        List<AppMember> cells = memberMapper.selectList(new LambdaQueryWrapper<AppMember>()
                .eq(AppMember::getAppId, app.getId()));
        if (cells.isEmpty()) {
            return List.of();
        }
        Map<Long, Map<Long, Long>> rolesByUser = new LinkedHashMap<>();
        cells.forEach(c -> rolesByUser
                .computeIfAbsent(c.getUserId(), k -> new LinkedHashMap<>())
                .put(c.getEnvId(), c.getRoleId()));

        List<PlatformUser> users = userMapper.selectList(new LambdaQueryWrapper<PlatformUser>()
                        .in(PlatformUser::getId, rolesByUser.keySet())
                        .orderByAsc(PlatformUser::getId));
        return users.stream().map(u -> {
            MemberRowVO row = new MemberRowVO();
            row.setUserId(u.getId());
            row.setEmail(u.getEmail());
            row.setName(u.getName());
            row.setPlatformRole(u.getPlatformRole());
            row.setStatus(u.getStatus());
            row.setRolesByEnv(rolesByUser.getOrDefault(u.getId(), Map.of()));
            return row;
        }).toList();
    }

    @Override
    @Transactional
    public void invite(InviteMemberDTO dto) {
        App app = guard.requireApp(dto.getAppKey());
        guard.require(app.getId(), null, Perm.MEMBER_MANAGE);

        PlatformUser user = userMapper.selectOne(new LambdaQueryWrapper<PlatformUser>()
                .eq(PlatformUser::getEmail, dto.getEmail()).last("LIMIT 1"));
        if (user == null) {
            user = new PlatformUser();
            user.setEmail(dto.getEmail());
            user.setName(dto.getName() == null || dto.getName().isBlank()
                    ? dto.getEmail().split("@")[0] : dto.getName());
            user.setPasswordHash(BCrypt.hashpw(
                    dto.getPassword() == null || dto.getPassword().isBlank() ? "demo1234" : dto.getPassword()));
            user.setPlatformRole("USER");
            user.setStatus("ACTIVE");
            userMapper.insert(user);
        }

        AppRole role = requireRole(app.getId(), dto.getRoleKey());
        for (String envKey : dto.getEnvKeys()) {
            Env env = guard.requireEnv(app.getId(), envKey);
            upsert(app.getId(), user.getId(), env.getId(), role.getId());
        }
    }

    @Override
    @Transactional
    public void assign(AssignRoleDTO dto) {
        App app = guard.requireApp(dto.getAppKey());
        guard.require(app.getId(), null, Perm.MEMBER_MANAGE);

        PlatformUser user = userMapper.selectOne(new LambdaQueryWrapper<PlatformUser>()
                .eq(PlatformUser::getEmail, dto.getEmail()).last("LIMIT 1"));
        if (user == null) {
            throw BizException.notFound("平台账号不存在：" + dto.getEmail());
        }
        Env env = guard.requireEnv(app.getId(), dto.getEnvKey());

        if (dto.getRoleKey() == null || dto.getRoleKey().isBlank()) {
            memberMapper.delete(new LambdaQueryWrapper<AppMember>()
                    .eq(AppMember::getAppId, app.getId())
                    .eq(AppMember::getUserId, user.getId())
                    .eq(AppMember::getEnvId, env.getId()));
            return;
        }
        AppRole role = requireRole(app.getId(), dto.getRoleKey());
        upsert(app.getId(), user.getId(), env.getId(), role.getId());
    }

    @Override
    @Transactional
    public void remove(String appKey, String email) {
        App app = guard.requireApp(appKey);
        guard.require(app.getId(), null, Perm.MEMBER_MANAGE);

        PlatformUser user = userMapper.selectOne(new LambdaQueryWrapper<PlatformUser>()
                .eq(PlatformUser::getEmail, email).last("LIMIT 1"));
        if (user == null) {
            throw BizException.notFound("平台账号不存在：" + email);
        }
        memberMapper.delete(new LambdaQueryWrapper<AppMember>()
                .eq(AppMember::getAppId, app.getId())
                .eq(AppMember::getUserId, user.getId()));
    }

    private void upsert(Long appId, Long userId, Long envId, Long roleId) {
        AppMember existing = memberMapper.selectOne(new LambdaQueryWrapper<AppMember>()
                .eq(AppMember::getAppId, appId)
                .eq(AppMember::getUserId, userId)
                .eq(AppMember::getEnvId, envId)
                .last("LIMIT 1"));
        if (existing == null) {
            AppMember m = new AppMember();
            m.setAppId(appId);
            m.setUserId(userId);
            m.setEnvId(envId);
            m.setRoleId(roleId);
            memberMapper.insert(m);
        } else {
            existing.setRoleId(roleId);
            memberMapper.updateById(existing);
        }
    }

    private AppRole requireRole(Long appId, String roleKey) {
        AppRole role = roleMapper.selectOne(new LambdaQueryWrapper<AppRole>()
                .eq(AppRole::getAppId, appId).eq(AppRole::getRoleKey, roleKey).last("LIMIT 1"));
        if (role == null) {
            throw BizException.notFound("角色不存在：" + roleKey);
        }
        return role;
    }
}
