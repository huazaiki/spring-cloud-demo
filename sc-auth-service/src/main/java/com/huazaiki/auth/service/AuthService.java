package com.huazaiki.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huazaiki.auth.config.JwtUtil;
import com.huazaiki.auth.entity.SysUser;
import com.huazaiki.auth.mapper.SysUserMapper;
import com.huazaiki.common.exception.BusinessException;
import com.huazaiki.common.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final ErrorCode USERNAME_EXISTS = () -> 400;
    private static final ErrorCode INVALID_CREDENTIALS = () -> 401;

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthService(SysUserMapper userMapper, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    public void register(String username, String password, String role) {
        if (userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)) > 0) {
            throw new BusinessException(USERNAME_EXISTS, "Username already exists: " + username);
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        userMapper.insert(user);
    }

    public String login(String username, String password) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));

        if (user == null) {
            throw new BusinessException(INVALID_CREDENTIALS, "Invalid username or password");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(INVALID_CREDENTIALS, "Invalid username or password");
        }

        return jwtUtil.generateToken(user.getId(), user.getRole());
    }
}
