package com.z.liveconfig.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.z.liveconfig.common.BizException;
import com.z.liveconfig.dto.CreateRoleDTO;
import com.z.liveconfig.entity.App;
import com.z.liveconfig.entity.AppMember;
import com.z.liveconfig.entity.AppRole;
import com.z.liveconfig.entity.AppRolePerm;
import com.z.liveconfig.mapper.AppMemberMapper;
import com.z.liveconfig.mapper.AppRoleMapper;
import com.z.liveconfig.mapper.AppRolePermMapper;
import com.z.liveconfig.security.AccessGuard;
import com.z.liveconfig.security.Perm;
import com.z.liveconfig.service.IRoleService;
import com.z.liveconfig.vo.PermGroupVO;
import com.z.liveconfig.vo.RoleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements IRoleService {

    private final AppRoleMapper roleMapper;
    private final AppRolePermMapper rolePermMapper;
    private final AppMemberMapper memberMapper;
    private final AccessGuard guard;

    @Override
    public List<RoleVO> list(String appKey) {
        App app = guard.requireApp(appKey);
        guard.require(app.getId(), null, Perm.FLAG_READ);

        List<AppRole> roles = roleMapper.selectList(new LambdaQueryWrapper<AppRole>()
                .eq(AppRole::getAppId, app.getId())
                .orderByAsc(AppRole::getId));
        if (roles.isEmpty()) {
            return List.of();
        }

        List<Long> roleIds = roles.stream().map(AppRole::getId).toList();

        Map<Long, List<String>> permsByRole = rolePermMapper.selectList(
                        new LambdaQueryWrapper<AppRolePerm>().in(AppRolePerm::getRoleId, roleIds))
                .stream().collect(Collectors.groupingBy(AppRolePerm::getRoleId,
                        Collectors.mapping(AppRolePerm::getPerm, Collectors.toList())));

        Map<Long, Long> usedByMap = memberMapper.selectList(
                        new LambdaQueryWrapper<AppMember>().in(AppMember::getRoleId, roleIds))
                .stream().collect(Collectors.groupingBy(AppMember::getRoleId,
                        Collectors.mapping(AppMember::getUserId, Collectors.collectingAndThen(
                                Collectors.toSet(), s -> (long) s.size()))));

        return roles.stream().map(r -> {
            RoleVO vo = new RoleVO();
            vo.setId(r.getId());
            vo.setAppId(app.getId());
            vo.setRoleKey(r.getRoleKey());
            vo.setName(r.getName());
            vo.setBuiltin(r.getBuiltin());
            List<String> perms = permsByRole.getOrDefault(r.getId(), List.of());
            vo.setPermCsv(String.join(",", perms));
            vo.setPerms(perms);
            vo.setUsedBy(Math.toIntExact(usedByMap.getOrDefault(r.getId(), 0L)));
            return vo;
        }).toList();
    }

    @Override
    @Transactional
    public RoleVO create(CreateRoleDTO dto) {
        App app = guard.requireApp(dto.getAppKey());
        guard.require(app.getId(), null, Perm.MEMBER_MANAGE);

        if (roleMapper.selectCount(new LambdaQueryWrapper<AppRole>()
                .eq(AppRole::getAppId, app.getId()).eq(AppRole::getRoleKey, dto.getRoleKey())) > 0) {
            throw BizException.conflict("角色标识已存在：" + dto.getRoleKey());
        }
        for (String code : dto.getPerms()) {
            Perm.ofCode(code);
        }

        AppRole role = new AppRole();
        role.setAppId(app.getId());
        role.setRoleKey(dto.getRoleKey());
        role.setName(dto.getName());
        role.setBuiltin(false);
        roleMapper.insert(role);
        dto.getPerms().forEach(p -> {
            AppRolePerm rp = new AppRolePerm();
            rp.setRoleId(role.getId());
            rp.setPerm(p);
            rolePermMapper.insert(rp);
        });

        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setAppId(app.getId());
        vo.setRoleKey(role.getRoleKey());
        vo.setName(role.getName());
        vo.setBuiltin(false);
        vo.setPermCsv(String.join(",", dto.getPerms()));
        vo.setPerms(dto.getPerms());
        vo.setUsedBy(0);
        return vo;
    }

    @Override
    public List<PermGroupVO> permCatalog() {
        return List.of(
                group("开关", Perm.FLAG_READ, Perm.FLAG_WRITE),
                group("发布", Perm.VALUE_NONPROD, Perm.VALUE_PROD),
                group("实例", Perm.INSTANCE_WRITE),
                group("管理", Perm.ENV_MANAGE, Perm.MEMBER_MANAGE));
    }

    private PermGroupVO group(String name, Perm... perms) {
        PermGroupVO vo = new PermGroupVO();
        vo.setGroup(name);
        vo.setItems(Arrays.stream(perms)
                .map(p -> PermGroupVO.Item.of(p.code(), p.label()))
                .toList());
        return vo;
    }
}
