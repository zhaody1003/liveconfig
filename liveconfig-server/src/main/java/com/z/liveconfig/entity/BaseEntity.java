package com.z.liveconfig.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务表公共列：自增主键 + 创建/更新时间 + 操作人。
 * 四个字段一律由 MetaFieldHandler 填充，Service 里不再手撒时间与操作人。
 */
@Data
public abstract class BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 操作人 email；无登录上下文的写入（SDK 上报、启动种子、定时任务）为 system */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String operator;
}
