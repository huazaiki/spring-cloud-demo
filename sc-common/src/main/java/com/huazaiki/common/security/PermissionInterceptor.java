package com.huazaiki.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 基于 X-User-Permissions 请求头的方法级权限校验（网关在 JWT 校验后注入该头）。
 */
public class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePermission required = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);
        }
        if (required == null) {
            return true;
        }
        String permHeader = request.getHeader("X-User-Permissions");
        if (permHeader == null || permHeader.isBlank()) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        Set<String> perms = Arrays.stream(permHeader.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (!perms.contains(required.value())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        return true;
    }
}