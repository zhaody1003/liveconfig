package com.z.liveconfig.service;

import com.z.liveconfig.dto.CreateAppDTO;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.vo.AppVO;

import java.util.List;

public interface IAppService {

    List<AppVO> list(String keyword);

    App create(CreateAppDTO dto);
}
