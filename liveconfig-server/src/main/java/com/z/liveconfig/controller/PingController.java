package com.z.liveconfig.controller;

import com.z.common.response.Result;
import com.z.liveconfig.mapper.PingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PingController {

    private final PingMapper pingMapper;

    @GetMapping("/ping")
    public Result<Map<String, String>> ping() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("app", "liveconfig-server");
        data.put("db", pingMapper.dbVersion());
        return Result.success(data);
    }
}
