package com.z.liveconfig.service;

import com.z.liveconfig.dto.CreateEnvDTO;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.vo.CreatedEnvVO;
import com.z.liveconfig.vo.EnvVO;

import java.util.List;

public interface IEnvService {

    List<EnvVO> list(String appKey);

    /** 创建环境并生成该环境的 SERVER / CLIENT 两把 Key，明文只在这一次返回 */
    CreatedEnvVO create(String appKey, CreateEnvDTO dto);

    void createIn(App app, CreateEnvDTO dto);

    void delete(String appKey, String envKey);

    /** 重置 SDK Key：作废旧 Key，生成新 Key（明文同样只返回一次） */
    CreatedEnvVO resetKeys(String appKey, String envKey);
}
