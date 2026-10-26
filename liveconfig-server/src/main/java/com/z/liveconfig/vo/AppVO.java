package com.z.liveconfig.vo;

import lombok.Data;

@Data
public class AppVO {

    private String appKey;
    private String name;
    private String owner;
    private String color;
    private Integer flagCount;
    private Integer envCount;
}
