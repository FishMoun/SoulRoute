package com.fishmoun.soulroute.controller;

import com.fishmoun.soulroute.auth.AuthRequest;
import com.fishmoun.soulroute.auth.AuthResponse;
import com.fishmoun.soulroute.auth.AuthUser;
import com.fishmoun.soulroute.auth.TokenService;
import com.fishmoun.soulroute.auth.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "用户认证接口", description = "注册、登录和登录态创建")
public class AuthController {

    private final UserService userService;
    private final TokenService tokenService;

    public AuthController(UserService userService, TokenService tokenService) {
        this.userService = userService;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    @Operation(summary = "注册用户")
    public ResponseEntity<?> register(@RequestBody AuthRequest request) {
        try {
            AuthUser user = userService.register(request);
            return ResponseEntity.ok(new AuthResponse(tokenService.create(user), user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new TravelController.ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
            AuthUser user = userService.login(request);
            return ResponseEntity.ok(new AuthResponse(tokenService.create(user), user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new TravelController.ErrorResponse(e.getMessage()));
        }
    }
}
