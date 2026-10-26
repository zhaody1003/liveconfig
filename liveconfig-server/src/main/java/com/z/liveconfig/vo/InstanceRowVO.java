package com.z.liveconfig.vo;

import lombok.Data;

import java.util.List;

@Data
public class InstanceRowVO {

    private String privateIp;
    private String publicIp;

    /** null = 跟随默认值 */
    private String overrideValue;

    private List<String> candidates;
    private String valueType;
}
