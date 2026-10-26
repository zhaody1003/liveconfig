package com.z.liveconfig.vo;

import lombok.Data;

/** 成员矩阵的一行：一个成员在各环境持什么角色 */
@Data
public class MemberRowVO {

    private Long userId;
    private String email;
    private String name;
    private String platformRole;
    private String status;

    /** envId → roleId，未分配则不含该键 */
    private java.util.Map<Long, Long> rolesByEnv;
}
