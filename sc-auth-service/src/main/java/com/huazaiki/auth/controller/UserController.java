package com.huazaiki.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huazaiki.auth.entity.SysUser;
import com.huazaiki.auth.entity.SysUserRole;
import com.huazaiki.auth.mapper.SysUserMapper;
import com.huazaiki.auth.mapper.SysUserRoleMapper;
import com.huazaiki.common.api.ApiResponse;
import com.huazaiki.common.security.RequirePermission;
import com.huazaiki.common.exception.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserController(SysUserMapper userMapper, SysUserRoleMapper userRoleMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @RequirePermission("user:list")
    @GetMapping
    public ApiResponse<List<SysUser>> list() {
        return ApiResponse.success(userMapper.selectList(null));
    }

    @RequirePermission("user:create")
    @PostMapping
    public ApiResponse<Void> create(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)) > 0) {
            throw new BusinessException(() -> 400, "Username already exists: " + username);
        }
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(body.get("password")));
        user.setDeptId(body.get("deptId") != null ? Long.valueOf(body.get("deptId")) : null);
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        return ApiResponse.success();
    }

    @RequirePermission("user:update")
    @PutMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(() -> 404, "User not found: " + id);
        }
        user.setStatus(body.get("status"));
        userMapper.updateById(user);
        return ApiResponse.success();
    }

    @RequirePermission("user:update")
    @Transactional
    @PostMapping("/{id}/roles")
    public ApiResponse<Void> assignRoles(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Object> roleIds = (List<Object>) body.get("roleIds");
        if (roleIds == null) {
            throw new BusinessException(() -> 400, "roleIds required");
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        for (Object rid : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(id);
            ur.setRoleId(Long.valueOf(rid.toString()));
            userRoleMapper.insert(ur);
        }
        return ApiResponse.success();
    }
}