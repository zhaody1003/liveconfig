package com.z.liveconfig.service;

import com.z.liveconfig.dto.SetInstanceDTO;
import com.z.liveconfig.vo.InstanceRowVO;

import java.util.List;

public interface IInstanceService {

    /** 展开行：候选服务器来自该环境连过的 SDK 实例，值来自 override 表 */
    List<InstanceRowVO> list(Long flagId);

    void set(Long flagId, SetInstanceDTO dto);

    void clear(Long flagId, String privateIp);
}
