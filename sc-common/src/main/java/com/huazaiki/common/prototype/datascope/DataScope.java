package com.huazaiki.common.prototype.datascope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** PROTOTYPE — 数据权限注解，标注在 Mapper 方法上自动追加过滤条件 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /** 数据范围类型 */
    ScopeType value() default ScopeType.SELF;

    enum ScopeType {
        /** 仅自己创建的数据（需求申请人） */
        SELF,
        /** 本部门数据（部门审批人） */
        DEPT,
        /** 按供应商/品类过滤（采购员） */
        SUPPLIER,
        /** 不做过滤（仓管/财务全局） */
        ALL
    }

    /** Mapper 参数的别名（用于通过属性名注入过滤值） */
    String paramAlias() default "";
}
