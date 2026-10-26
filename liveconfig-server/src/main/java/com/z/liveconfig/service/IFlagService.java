package com.z.liveconfig.service;

import com.z.liveconfig.dto.CreateFlagDTO;
import com.z.liveconfig.dto.UpdateDefaultDTO;
import com.z.liveconfig.vo.FlagDetailVO;
import com.z.liveconfig.vo.FlagRowVO;

import java.util.List;

public interface IFlagService {

    List<FlagRowVO> list(String appKey, String envKey, String keyword);

    FlagDetailVO detail(Long flagId);

    FlagDetailVO create(CreateFlagDTO dto);

    /** 改默认值：版本 +1、环境 configVersion +1、写历史、SSE 全量推送 */
    FlagDetailVO updateDefault(Long flagId, UpdateDefaultDTO dto);

    FlagDetailVO toggle(Long flagId, boolean enabled);
}
