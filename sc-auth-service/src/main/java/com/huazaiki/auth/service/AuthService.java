package com.huazaiki.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huazaiki.auth.config.JwtUtil;
import com.huazaiki.auth.dto.MeResponse;
import com.huazaiki.auth.entity.SysDept;
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
import com.huazaiki.common.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private static final ErrorCode USERNAME_EXISTS = () -> 400;
    private static final ErrorCode INVALID_CREDENTIALS = () -> 401;

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthService(SysUserMapper userMapper,
                       SysDeptMapper deptMapper,
                       SysRoleMapper roleMapper,
                       SysPermissionMapper permissionMapper,
                       SysUserRoleMapper userRoleMapper,
                       SysRolePermissionMapper rolePermissionMapper,
                       JwtUtil jwtUtil,
                       PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.deptMapper = deptMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(String username, String password, String role) {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)) > 0) {
            throw new BusinessException(USERNAME_EXISTS, "Username already exists: " + username);
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role == null || role.isBlank() ? "NONE" : role);
        user.setStatus("ACTIVE");
        userMapper.insert(user);
    }

    /**
     * 登录：解析用户角色与权限点，签发含身份/权限 claims 的 JWT。
     */
    public String login(String username, String password) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));

        if (user == null) {
            throw new BusinessException(INVALID_CREDENTIALS, "Invalid username or password");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(INVALID_CREDENTIALS, "User disabled");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(INVALID_CREDENTIALS, "Invalid username or password");
        }

        List<SysRole> roles = findRolesByUserId(user.getId());
        List<String> roleCodes = roles.stream().map(SysRole::getRoleCode).toList();
        List<String> permissions = findPermissionsByRoleIds(
                roles.stream().map(SysRole::getId).toList());
        return jwtUtil.generateToken(user.getId(), user.getDeptId(), roleCodes, permissions);
    }

    /**
     * 当前用户信息（含部门/角色/权限点；前端据此生成动态菜单）。
     */
    public MeResponse me(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(() -> 404, "User not found: " + userId);
        }
        MeResponse resp = new MeResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setDeptId(user.getDeptId());
        if (user.getDeptId() != null) {
            SysDept dept = deptMapper.selectById(user.getDeptId());
            if (dept != null) {
                resp.setDeptName(dept.getDeptName());
            }
        }
        List<SysRole> roles = findRolesByUserId(userId);
        resp.setRoles(roles.stream().map(SysRole::getRoleCode).toList());
        resp.setPermissions(findPermissionsByRoleIds(roles.stream().map(SysRole::getId).toList()));
        return resp;
    }

    private List<SysRole> findRolesByUserId(Long userId) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        return roleMapper.selectBatchIds(roleIds);
    }

    private List<String> findPermissionsByRoleIds(List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<SysRolePermission> rps = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>().in(SysRolePermission::getRoleId, roleIds));
        if (rps.isEmpty()) {
            return List.of();
        }
        List<Long> permIds = rps.stream().map(SysRolePermission::getPermissionId).distinct().toList();
        return permissionMapper.selectBatchIds(permIds).stream()
                .map(SysPermission::getPermCode)
                .toList();
    }
}