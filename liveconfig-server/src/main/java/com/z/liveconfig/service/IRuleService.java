package com.z.liveconfig.service;

import com.z.liveconfig.dto.SaveRulesDTO;
import com.z.liveconfig.vo.FlagDetailVO;

public interface IRuleService {

    FlagDetailVO save(Long flagId, SaveRulesDTO dto);
}
