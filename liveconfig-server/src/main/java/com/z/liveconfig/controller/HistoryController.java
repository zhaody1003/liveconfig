package com.z.liveconfig.controller;

import com.z.common.response.Result;
import com.z.liveconfig.service.IHistoryService;
import com.z.liveconfig.vo.ChangeLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final IHistoryService historyService;

    @GetMapping
    public Result<Map<String, Object>> page(@RequestParam String appKey,
                                            @RequestParam String envKey,
                                            @RequestParam(required = false) String flagKey,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        List<ChangeLogVO> records = historyService.page(appKey, envKey, flagKey, pageNum, pageSize);
        long total = historyService.total(appKey, envKey, flagKey);
        return Result.success(Map.of("records", records, "total", total,
                "pageNum", pageNum, "pageSize", pageSize));
    }

    @PostMapping("/{logId}/rollback")
    public Result<ChangeLogVO> rollback(@PathVariable Long logId) {
        return Result.success(historyService.rollback(logId));
    }
}
