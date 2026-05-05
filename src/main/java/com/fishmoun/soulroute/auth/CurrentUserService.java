package com.fishmoun.soulroute.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
public class CurrentUserService {

    private final TokenService tokenService;
    private final UserService userService;

    public CurrentUserService(TokenService tokenService, UserService userService) {
        this.tokenService = tokenService;
        this.userService = userService;
    }

    public Optional<AuthUser> currentUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            return Optional.empty();
        }
        return tokenService.parse(header.substring("Bearer ".length()))
                .flatMap(claims -> userService.findById(claims.userId()));
    }
}
