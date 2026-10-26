package com.z.liveconfig.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("app")
public class App extends BaseEntity {

    private String appKey;
    private String name;
    private String owner;
    private String color;
}
