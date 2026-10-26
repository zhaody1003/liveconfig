package com.z.liveconfig.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.z.liveconfig.entity.PlatformUser;
import com.z.liveconfig.security.CurrentUser;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * created_at / updated_at / operator 由这里填，Service 里不再手撒时间。
 *
 * insert 走 strict*：MP 的 strictFillStrategy 是"有值不覆盖"，
 * 所以种子数据里显式指定的操作人能保留下来。
 *
 * update 必须强制覆盖：updateById 传的是从库里查出来的实体，这几个字段本就非空，
 * 再用 strict* 会被当成"已有值"跳过——updatedAt 和 lastSeenAt 会永远停在插入那一刻。
 */
@Component
public class MetaFieldHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "lastSeenAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "operator", String.class, currentOperator());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        forceSet(metaObject, "updatedAt", now);
        forceSet(metaObject, "lastSeenAt", now);
        forceSet(metaObject, "operator", currentOperator());
    }

    private void forceSet(MetaObject metaObject, String field, Object value) {
        if (metaObject.hasSetter(field)) {
            metaObject.setValue(field, value);
        }
    }

    /** 无登录上下文（SDK 上报、启动种子、定时任务）落 system */
    private String currentOperator() {
        PlatformUser user = CurrentUser.get();
        return user == null || user.getEmail() == null ? "system" : user.getEmail();
    }
}
