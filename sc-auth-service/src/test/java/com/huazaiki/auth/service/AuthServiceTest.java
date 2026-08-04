package com.huazaiki.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huazaiki.auth.config.JwtUtil;
import com.huazaiki.auth.dto.MeResponse;
import com.huazaiki.auth.entity.SysPermission;
import com.huazaiki.auth.entity.SysRole;
import com.huazaiki.auth.entity.SysRolePermission;
import com.huazaiki.auth.entity.SysUser;
import com.huazaiki.auth.entity.SysUserRole;
import com.huazaiki.auth.mapper.SysDeptMapper;
import com.huazaiki.auth.mapper.SysPermissionMapper;
import com.huazaiki.auth.mapper.SysRoleMapper;
import com.huazaiki.auth.mapper.SysRolePermissionMapper;
import com.huazaiki.auth.mapper.SysUserMapper;
import com.huazaiki.auth.mapper.SysUserRoleMapper;
import com.huazaiki.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private SysUserMapper userMapper;
    @Mock private SysDeptMapper deptMapper;
    @Mock private SysRoleMapper roleMapper;
    @Mock private SysPermissionMapper permissionMapper;
    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private SysRolePermissionMapper rolePermissionMapper;
    @Mock private JwtUtil jwtUtil;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("should throw when username already exists")
        void shouldThrowWhenUsernameExists() {
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            assertThrows(BusinessException.class, () ->
                    authService.register("admin", "pass123", "ADMIN"));
        }

        @Test
        @DisplayName("should encode password and save user")
        void shouldEncodePasswordAndSave() {
            when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
            when(passwordEncoder.encode("pass123")).thenReturn("hashed");
            when(userMapper.insert(any(SysUser.class))).thenReturn(1);

            authService.register("newuser", "pass123", "PURCHASER");
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should return token with roles and permissions on successful login")
        void shouldReturnToken() {
            SysUser user = new SysUser();
            user.setId(1L);
            user.setUsername("admin");
            user.setPasswordHash("hashed");
            user.setRole("ADMIN");
            user.setStatus("ACTIVE");

            SysRole role = new SysRole();
            role.setId(1L);
            role.setRoleCode("ADMIN");

            SysPermission perm = new SysPermission();
            perm.setId(1001L);
            perm.setPermCode("user:list");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches("pass123", "hashed")).thenReturn(true);
            when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(1L)));
            when(roleMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(role));
            when(rolePermissionMapper.selectList(any())).thenReturn(List.of(rolePermission(1L, 1001L)));
            when(permissionMapper.selectBatchIds(List.of(1001L))).thenReturn(List.of(perm));
            when(jwtUtil.generateToken(1L, null, List.of("ADMIN"), List.of("user:list")))
                    .thenReturn("jwt-token-here");

            String token = authService.login("admin", "pass123");
            assertEquals("jwt-token-here", token);
        }

        @Test
        @DisplayName("should throw when user disabled")
        void shouldThrowWhenDisabled() {
            SysUser user = new SysUser();
            user.setStatus("DISABLED");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

            assertThrows(BusinessException.class, () -> authService.login("admin", "pass"));
        }

        @Test
        @DisplayName("should throw when user not found")
        void shouldThrowWhenUserNotFound() {
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            assertThrows(BusinessException.class, () ->
                    authService.login("nobody", "pass"));
        }

        @Test
        @DisplayName("should throw when password mismatch")
        void shouldThrowWhenPasswordMismatch() {
            SysUser user = new SysUser();
            user.setPasswordHash("hashed");
            user.setStatus("ACTIVE");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

            assertThrows(BusinessException.class, () ->
                    authService.login("admin", "wrong"));
        }
    }

    @Nested
    @DisplayName("me")
    class Me {

        @Test
        @DisplayName("should return user info with roles and permissions")
        void shouldReturnMe() {
            SysUser user = new SysUser();
            user.setId(1L);
            user.setUsername("admin");
            user.setDeptId(10L);
            user.setStatus("ACTIVE");

            SysRole role = new SysRole();
            role.setId(2L);
            role.setRoleCode("ADMIN");

            when(userMapper.selectById(1L)).thenReturn(user);
            when(deptMapper.selectById(10L)).thenReturn(null);
            when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(1L)));
            when(roleMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(role));
            when(rolePermissionMapper.selectList(any())).thenReturn(List.of());

            MeResponse resp = authService.me(1L);

            assertEquals("admin", resp.getUsername());
            assertEquals(List.of("ADMIN"), resp.getRoles());
        }
    }

    private static SysUserRole userRole(Long roleId) {
        SysUserRole ur = new SysUserRole();
        ur.setUserId(1L);
        ur.setRoleId(roleId);
        return ur;
    }

    private static SysRolePermission rolePermission(Long roleId, Long permissionId) {
        SysRolePermission rp = new SysRolePermission();
        rp.setRoleId(roleId);
        rp.setPermissionId(permissionId);
        return rp;
    }
}