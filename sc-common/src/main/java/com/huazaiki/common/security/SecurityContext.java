package com.huazaiki.common.security;

/**
 * 当前请求用户上下文（由 PermissionInterceptor 从 X-User-Id 头填充）。
 */
public final class SecurityContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private SecurityContext() {}

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}