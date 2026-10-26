package com.z.liveconfig.vo;

import lombok.Data;

@Data
public class CreatedEnvVO {

    private EnvVO env;

    /** 明文 Key 只在创建/重置响应里出现一次，之后库里只有 SHA-256 */
    private String serverKeyPlain;
    private String clientKeyPlain;
}
