package com.huazaiki.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huazaiki.auth.entity.SysRole;
import com.huazaiki.auth.entity.SysRolePermission;
import com.huazaiki.auth.mapper.SysRoleMapper;
import com.huazaiki.auth.mapper.SysRolePermissionMapper;
import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.security.RequirePermission;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    public RoleController(SysRoleMapper roleMapper, SysRolePermissionMapper rolePermissionMapper) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @RequirePermission("role:manage")
    @GetMapping
    public ApiResponse<List<SysRole>> list() {
        return ApiResponse.success(roleMapper.selectList(null));
    }

    @RequirePermission("role:manage")
    @PostMapping
    public ApiResponse<Void> create(@RequestBody Map<String, String> body) {
        SysRole role = new SysRole();
        role.setRoleCode(body.get("roleCode"));
        role.setRoleName(body.get("roleName"));
        role.setDescription(body.get("description"));
        role.setStatus("ACTIVE");
        roleMapper.insert(role);
        return ApiResponse.success();
    }

    @RequirePermission("role:manage")
    @Transactional
    @PutMapping("/{id}/permissions")
    public ApiResponse<Void> assignPermissions(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> permissionIds = (List<Object>) body.get("permissionIds");
        if (permissionIds == null) {
            return ApiResponse.fail(400, "permissionIds required");
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, id));
        for (Object pid : permissionIds) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(id);
            rp.setPermissionId(Long.valueOf(pid.toString()));
            rolePermissionMapper.insert(rp);
        }
        return ApiResponse.success();
    }
}