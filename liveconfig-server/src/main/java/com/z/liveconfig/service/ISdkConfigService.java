package com.z.liveconfig.service;

import com.z.liveconfig.engine.EnvConfigSnapshot;

/** 把 DB 里的环境配置组装成引擎用的不可变快照 */
public interface ISdkConfigService {

    EnvConfigSnapshot snapshot(Long envId);
}
