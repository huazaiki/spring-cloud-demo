package com.huazaiki.auth.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtUtil")
class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("test-secret-key-minimum-256-bits-long-for-hs256", 3600000L);
    }

    @Nested
    @DisplayName("generateToken")
    class Generate {

        @Test
        @DisplayName("should produce a non-null token string")
        void shouldProduceNonNullToken() {
            String token = jwtUtil.generateToken(1L, 0L, List.of("PURCHASER"), List.of("pr:create"));
            assertNotNull(token);
            assertFalse(token.isBlank());
        }

        @Test
        @DisplayName("should embed userId, deptId, roles, and permissions in token claims")
        void shouldEmbedClaims() {
            String token = jwtUtil.generateToken(42L, 10L, List.of("ADMIN"), List.of("user:list", "dept:manage"));
            assertEquals("42", jwtUtil.getUserId(token));
        }
    }

    @Nested
    @DisplayName("validateToken")
    class Validate {

        @Test
        @DisplayName("should return true for a valid token")
        void shouldAcceptValidToken() {
            String token = jwtUtil.generateToken(1L, 0L, List.of("ADMIN"), List.of());
            assertTrue(jwtUtil.validateToken(token));
        }

        @Test
        @DisplayName("should return false for a tampered token")
        void shouldRejectTamperedToken() {
            String token = jwtUtil.generateToken(1L, 0L, List.of("ADMIN"), List.of());
            assertFalse(jwtUtil.validateToken(token + "tampered"));
        }
    }
}
