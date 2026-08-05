package com.huazaiki.common.prototype.datascope;

import java.util.List;

/** PROTOTYPE — 当前请求的数据权限上下文，Gateway 从 JWT 解析后通过 Header 注入 */
public class DataScopeContext {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_DEPT_ID = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> CURRENT_ROLES = new ThreadLocal<>();

    public static void set(Long userId, Long deptId, List<String> roles) {
        CURRENT_USER_ID.set(userId);
        CURRENT_DEPT_ID.set(deptId);
        CURRENT_ROLES.set(roles);
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
        CURRENT_DEPT_ID.remove();
        CURRENT_ROLES.remove();
    }

    public static Long currentUserId() { return CURRENT_USER_ID.get(); }
    public static Long currentDeptId() { return CURRENT_DEPT_ID.get(); }
    public static List<String> currentRoles() { return CURRENT_ROLES.get(); }

    /** 根据当前角色推断数据范围 */
    public static DataScope.ScopeType inferScope() {
        List<String> roles = currentRoles();
        if (roles == null) return DataScope.ScopeType.SELF;
        if (roles.contains("ROLE_SYS_ADMIN") || roles.contains("ROLE_WAREHOUSE") || roles.contains("ROLE_FINANCE"))
            return DataScope.ScopeType.ALL;
        if (roles.contains("ROLE_DEPT_APPROVER"))
            return DataScope.ScopeType.DEPT;
        if (roles.contains("ROLE_BUYER"))
            return DataScope.ScopeType.SUPPLIER;
        return DataScope.ScopeType.SELF;
    }
}
