package com.huazaiki.auth.controller;

import com.huazaiki.auth.entity.SysPermission;
import com.huazaiki.auth.mapper.SysPermissionMapper;
import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final SysPermissionMapper permissionMapper;

    public PermissionController(SysPermissionMapper permissionMapper) {
        this.permissionMapper = permissionMapper;
    }

    @GetMapping
    public ApiResponse<List<SysPermission>> list() {
        return ApiResponse.success(permissionMapper.selectList(null));
    }
}