package com.z.liveconfig.service;

import com.z.liveconfig.dto.CreateRoleDTO;
import com.z.liveconfig.vo.PermGroupVO;
import com.z.liveconfig.vo.RoleVO;

import java.util.List;

public interface IRoleService {

    List<RoleVO> list(String appKey);

    RoleVO create(CreateRoleDTO dto);

    /** 权限点清单给前端渲染勾选框 */
    List<PermGroupVO> permCatalog();
}
