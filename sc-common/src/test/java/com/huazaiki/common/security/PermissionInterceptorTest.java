package com.huazaiki.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PermissionInterceptorTest {

    private final PermissionInterceptor interceptor = new PermissionInterceptor();

    @Test
    void shouldAllowWhenPermissionPresent() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        HandlerMethod handler = mock(HandlerMethod.class);

        when(handler.getMethodAnnotation(RequirePermission.class))
                .thenReturn(perm("purchase:order:approve"));
        when(req.getHeader("X-User-Permissions")).thenReturn("purchase:order:create,purchase:order:approve");

        assertTrue(interceptor.preHandle(req, resp, handler));
    }

    @Test
    void shouldForbidWhenPermissionMissing() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        HandlerMethod handler = mock(HandlerMethod.class);

        when(handler.getMethodAnnotation(RequirePermission.class))
                .thenReturn(perm("purchase:order:approve"));
        when(req.getHeader("X-User-Permissions")).thenReturn("purchase:order:create");

        assertFalse(interceptor.preHandle(req, resp, handler));
        verify(resp).setStatus(403);
    }

    @Test
    void shouldForbidWhenNoHeader() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        HandlerMethod handler = mock(HandlerMethod.class);

        when(handler.getMethodAnnotation(RequirePermission.class))
                .thenReturn(perm("purchase:order:approve"));
        when(req.getHeader("X-User-Permissions")).thenReturn(null);

        assertFalse(interceptor.preHandle(req, resp, handler));
    }

    private static RequirePermission perm(String code) {
        return new RequirePermission() {
            @Override
            public String value() { return code; }
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return RequirePermission.class;
            }
        };
    }
}