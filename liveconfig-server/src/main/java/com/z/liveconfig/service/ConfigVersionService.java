package com.z.liveconfig.service;

import com.z.liveconfig.entity.Env;

/** 环境配置版本 +1 并返回刷新后的 Env，所有写路径共用 */
public interface ConfigVersionService {

    Env bump(Env env);
}
