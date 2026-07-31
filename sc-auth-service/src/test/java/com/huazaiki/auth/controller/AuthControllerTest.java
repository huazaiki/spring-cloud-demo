package com.huazaiki.auth.controller;

import com.huazaiki.auth.dto.LoginRequest;
import com.huazaiki.auth.dto.RegisterRequest;
import com.huazaiki.auth.service.AuthService;
import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock private AuthService authService;
    @InjectMocks private AuthController controller;

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("should return success on valid registration")
        void shouldReturnSuccess() {
            RegisterRequest req = new RegisterRequest();
            req.setUsername("newuser");
            req.setPassword("pass123");
            req.setRole("PURCHASER");

            ApiResponse<Void> response = controller.register(req);
            assertEquals(200, response.getCode());
            assertEquals("success", response.getMessage());
        }

        @Test
        @DisplayName("should propagate BusinessException")
        void shouldPropagateException() {
            doThrow(new BusinessException(() -> 400, "Username exists"))
                    .when(authService).register(anyString(), anyString(), anyString());

            RegisterRequest req = new RegisterRequest();
            req.setUsername("admin");
            req.setPassword("pass");
            req.setRole("ADMIN");

            assertThrows(BusinessException.class, () -> controller.register(req));
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("should return token on successful login")
        void shouldReturnToken() {
            when(authService.login("admin", "pass")).thenReturn("jwt-token");

            LoginRequest req = new LoginRequest();
            req.setUsername("admin");
            req.setPassword("pass");

            ApiResponse<?> response = controller.login(req);
            assertEquals(200, response.getCode());
            assertNotNull(response.getData());
        }
    }
}