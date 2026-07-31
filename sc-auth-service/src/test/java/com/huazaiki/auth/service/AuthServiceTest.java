package com.huazaiki.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huazaiki.auth.config.JwtUtil;
import com.huazaiki.auth.entity.SysUser;
import com.huazaiki.auth.mapper.SysUserMapper;
import com.huazaiki.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private SysUserMapper userMapper;
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

            // Verify no exception thrown
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should return token on successful login")
        void shouldReturnToken() {
            SysUser user = new SysUser();
            user.setId(1L);
            user.setUsername("admin");
            user.setPasswordHash("hashed");
            user.setRole("ADMIN");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches("pass123", "hashed")).thenReturn(true);
            when(jwtUtil.generateToken(1L, "ADMIN")).thenReturn("jwt-token-here");

            String token = authService.login("admin", "pass123");
            assertEquals("jwt-token-here", token);
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

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

            assertThrows(BusinessException.class, () ->
                    authService.login("admin", "wrong"));
        }
    }
}
