package com.fishmoun.soulroute.controller;

import com.fishmoun.soulroute.auth.AuthUser;
import com.fishmoun.soulroute.auth.CurrentUserService;
import com.fishmoun.soulroute.preference.TravelPreference;
import com.fishmoun.soulroute.preference.TravelPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/me/preferences")
@Tag(name = "用户旅行偏好接口", description = "保存和读取当前用户旅行偏好")
public class PreferenceController {

    private final CurrentUserService currentUserService;
    private final TravelPreferenceService preferenceService;

    public PreferenceController(CurrentUserService currentUserService,
                                TravelPreferenceService preferenceService) {
        this.currentUserService = currentUserService;
        this.preferenceService = preferenceService;
    }

    @GetMapping
    @Operation(summary = "获取当前用户旅行偏好")
    public ResponseEntity<?> get(HttpServletRequest request) {
        return currentUserService.currentUser(request)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(preferenceService.findByUserId(user.id()).orElse(emptyPreference())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new TravelController.ErrorResponse("请先登录")));
    }

    @PutMapping
    @Operation(summary = "保存当前用户旅行偏好")
    public ResponseEntity<?> save(HttpServletRequest request, @RequestBody TravelPreference preference) {
        return currentUserService.currentUser(request)
                .<ResponseEntity<?>>map((AuthUser user) -> ResponseEntity.ok(preferenceService.save(user.id(), preference)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new TravelController.ErrorResponse("请先登录")));
    }

    private TravelPreference emptyPreference() {
        return new TravelPreference(null, null, null, null, null, null, null, null, null);
    }
}
