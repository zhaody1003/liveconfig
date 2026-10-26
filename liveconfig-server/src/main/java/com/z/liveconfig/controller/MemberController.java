package com.z.liveconfig.controller;

import com.z.common.response.Result;
import com.z.liveconfig.dto.AssignRoleDTO;
import com.z.liveconfig.dto.CreateRoleDTO;
import com.z.liveconfig.dto.InviteMemberDTO;
import com.z.liveconfig.service.IMemberService;
import com.z.liveconfig.service.IRoleService;
import com.z.liveconfig.vo.MemberRowVO;
import com.z.liveconfig.vo.PermGroupVO;
import com.z.liveconfig.vo.RoleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MemberController {

    private final IMemberService memberService;
    private final IRoleService roleService;

    @GetMapping("/members")
    public Result<List<MemberRowVO>> matrix(@RequestParam String appKey) {
        return Result.success(memberService.matrix(appKey));
    }

    @PostMapping("/members")
    public Result<Boolean> invite(@Valid @RequestBody InviteMemberDTO dto) {
        memberService.invite(dto);
        return Result.success();
    }

    /** 矩阵里改一格 */
    @PutMapping("/members/role")
    public Result<Boolean> assign(@Valid @RequestBody AssignRoleDTO dto) {
        memberService.assign(dto);
        return Result.success();
    }

    @DeleteMapping("/members")
    public Result<Boolean> remove(@RequestParam String appKey, @RequestParam String email) {
        memberService.remove(appKey, email);
        return Result.success();
    }

    @GetMapping("/roles")
    public Result<List<RoleVO>> roles(@RequestParam String appKey) {
        return Result.success(roleService.list(appKey));
    }

    @PostMapping("/roles")
    public Result<RoleVO> createRole(@Valid @RequestBody CreateRoleDTO dto) {
        return Result.success(roleService.create(dto));
    }

    @GetMapping("/perms")
    public Result<List<PermGroupVO>> perms() {
        return Result.success(roleService.permCatalog());
    }
}
