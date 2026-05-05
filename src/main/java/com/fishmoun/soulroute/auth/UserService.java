package com.fishmoun.soulroute.auth;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
public class UserService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AuthUser register(AuthRequest request) {
        validate(request);
        try {
            Long id = jdbcTemplate.queryForObject("""
                    INSERT INTO app_users(username, password_hash, display_name)
                    VALUES (?, ?, ?)
                    RETURNING id
                    """, Long.class,
                    request.username().trim(),
                    passwordEncoder.encode(request.password()),
                    StringUtils.hasText(request.displayName()) ? request.displayName().trim() : request.username().trim());
            return findById(id).orElseThrow();
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    public AuthUser login(AuthRequest request) {
        if (request == null || !StringUtils.hasText(request.username()) || !StringUtils.hasText(request.password())) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        UserPassword user = jdbcTemplate.query("""
                        SELECT id, username, display_name, password_hash FROM app_users WHERE username = ?
                        """,
                rs -> rs.next()
                        ? new UserPassword(rs.getLong("id"), rs.getString("username"),
                        rs.getString("display_name"), rs.getString("password_hash"))
                        : null,
                request.username().trim());
        if (user == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return new AuthUser(user.id(), user.username(), user.displayName());
    }

    public Optional<AuthUser> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                        SELECT id, username, display_name FROM app_users WHERE id = ?
                        """,
                rs -> rs.next()
                        ? Optional.of(new AuthUser(rs.getLong("id"), rs.getString("username"), rs.getString("display_name")))
                        : Optional.empty(),
                id);
    }

    private void validate(AuthRequest request) {
        if (request == null || !StringUtils.hasText(request.username()) || !StringUtils.hasText(request.password())) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        String username = request.username().trim();
        if (username.length() < 3 || username.length() > 32) {
            throw new IllegalArgumentException("用户名长度需为 3-32 个字符");
        }
        if (request.password().length() < 6 || request.password().length() > 72) {
            throw new IllegalArgumentException("密码长度需为 6-72 个字符");
        }
    }

    private record UserPassword(Long id, String username, String displayName, String passwordHash) {
    }
}
