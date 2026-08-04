package com.huazaiki.common.audit;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.huazaiki.common.security.SecurityContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充：create_by/update_by（取自 SecurityContext），create_time/update_time。
 * 仅对包含对应字段的实体生效（strict fill 按字段名）。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        Long userId = SecurityContext.getUserId();
        LocalDateTime now = LocalDateTime.now();
        if (userId != null) {
            this.strictInsertFill(metaObject, "createBy", Long.class, userId);
        }
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        Long userId = SecurityContext.getUserId();
        LocalDateTime now = LocalDateTime.now();
        if (userId != null) {
            this.strictUpdateFill(metaObject, "updateBy", Long.class, userId);
        }
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
    }
}