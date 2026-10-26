package com.z.liveconfig.service;

import com.z.liveconfig.entity.App;
import com.z.liveconfig.entity.FeatureFlag;
import com.z.liveconfig.entity.PlatformUser;
import com.z.liveconfig.vo.ChangeLogVO;

import java.util.List;

public interface IHistoryService {

    /** 写历史。before/after 传对象，内部序列化；返回落库后的记录（含 id） */
    ChangeLogVO record(App app, FeatureFlag flag, PlatformUser actor, String action,
                       Object before, Object after, long configVersion);

    List<ChangeLogVO> page(String appKey, String envKey, String flagKey, int pageNum, int pageSize);

    long total(String appKey, String envKey, String flagKey);

    /** 回滚：取该条历史的 after 快照重新执行一次写操作，返回新追加的历史 */
    ChangeLogVO rollback(Long logId);
}
