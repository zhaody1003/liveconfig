package com.z.liveconfig.service;

import com.z.liveconfig.dto.AssignRoleDTO;
import com.z.liveconfig.dto.InviteMemberDTO;
import com.z.liveconfig.vo.MemberRowVO;

import java.util.List;

public interface IMemberService {

    List<MemberRowVO> matrix(String appKey);

    void invite(InviteMemberDTO dto);

    /** 矩阵里改一格；roleKey 为空表示取消该环境授权 */
    void assign(AssignRoleDTO dto);

    void remove(String appKey, String email);
}
