package com.z.liveconfig.controller;

import com.z.common.response.Result;
import com.z.liveconfig.dto.CreateAppDTO;
import com.z.liveconfig.dto.CreateEnvDTO;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.service.IAppService;
import com.z.liveconfig.service.IEnvService;
import com.z.liveconfig.vo.AppVO;
import com.z.liveconfig.vo.CreatedEnvVO;
import com.z.liveconfig.vo.EnvVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AppController {

    private final IAppService appService;
    private final IEnvService envService;

    @GetMapping("/apps")
    public Result<List<AppVO>> list(@RequestParam(required = false) String q) {
        return Result.success(appService.list(q));
    }

    @PostMapping("/apps")
    public Result<AppVO> create(@Valid @RequestBody CreateAppDTO dto) {
        App app = appService.create(dto);
        AppVO vo = new AppVO();
        vo.setAppKey(app.getAppKey());
        vo.setName(app.getName());
        vo.setOwner(app.getOwner());
        vo.setColor(app.getColor());
        return Result.success(vo);
    }

    @GetMapping("/apps/{appKey}/envs")
    public Result<List<EnvVO>> envs(@PathVariable String appKey) {
        return Result.success(envService.list(appKey));
    }

    /** 明文 SDK Key 只在这个响应里出现一次 */
    @PostMapping("/apps/{appKey}/envs")
    public Result<CreatedEnvVO> createEnv(@PathVariable String appKey,
                                          @Valid @RequestBody CreateEnvDTO dto) {
        return Result.success(envService.create(appKey, dto));
    }

    @DeleteMapping("/apps/{appKey}/envs/{envKey}")
    public Result<Boolean> deleteEnv(@PathVariable String appKey, @PathVariable String envKey) {
        envService.delete(appKey, envKey);
        return Result.success();
    }

    @PostMapping("/apps/{appKey}/envs/{envKey}/reset-keys")
    public Result<CreatedEnvVO> resetKeys(@PathVariable String appKey, @PathVariable String envKey) {
        return Result.success(envService.resetKeys(appKey, envKey));
    }
}
