package com.huazaiki.auth.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            String token = jwtUtil.generateToken(1L, "purchaser");
            assertNotNull(token);
            assertFalse(token.isBlank());
        }

        @Test
        @DisplayName("should embed userId and role in token claims")
        void shouldEmbedClaims() {
            String token = jwtUtil.generateToken(42L, "warehouse");
            assertEquals("42", jwtUtil.getUserId(token));
            assertEquals("warehouse", jwtUtil.getRole(token));
        }
    }

    @Nested
    @DisplayName("validateToken")
    class Validate {

        @Test
        @DisplayName("should return true for a valid token")
        void shouldAcceptValidToken() {
            String token = jwtUtil.generateToken(1L, "admin");
            assertTrue(jwtUtil.validateToken(token));
        }

        @Test
        @DisplayName("should return false for a tampered token")
        void shouldRejectTamperedToken() {
            String token = jwtUtil.generateToken(1L, "admin");
            assertFalse(jwtUtil.validateToken(token + "tampered"));
        }
    }
}
