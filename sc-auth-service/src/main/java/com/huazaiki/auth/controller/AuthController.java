package com.huazaiki.auth.controller;

import com.huazaiki.auth.dto.LoginRequest;
import com.huazaiki.auth.dto.LoginResponse;
import com.huazaiki.auth.dto.MeResponse;
import com.huazaiki.auth.dto.RegisterRequest;
import com.huazaiki.auth.service.AuthService;
import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.exception.BusinessException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody RegisterRequest request) {
        authService.register(request.getUsername(), request.getPassword(), request.getRoleCodes(), request.getDeptId());
        return ApiResponse.success();
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest request) {
        String token = authService.login(request.getUsername(), request.getPassword());
        return ApiResponse.success(new LoginResponse(token));
    }

    @GetMapping("/me")
    public ApiResponse<MeResponse> me(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(() -> 401, "Unauthorized");
        }
        return ApiResponse.success(authService.me(Long.valueOf(userId)));
    }
}