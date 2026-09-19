package com.dfwl.fleet.auth.controller;

import com.dfwl.fleet.auth.dto.request.ChangePasswordRequest;
import com.dfwl.fleet.auth.dto.response.CurrentUserResponse;
import com.dfwl.fleet.auth.dto.request.LoginRequest;
import com.dfwl.fleet.auth.dto.response.LoginResponse;
import com.dfwl.fleet.auth.dto.request.ResetPasswordRequest;
import com.dfwl.fleet.auth.dto.request.UpdateUserStatusRequest;

import com.dfwl.fleet.auth.service.AuthService;
import com.dfwl.fleet.common.api.ApiResponse;
import com.dfwl.fleet.common.web.RequestIdHolder;
import com.dfwl.fleet.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request), RequestIdHolder.get());
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        authService.logout(extractBearerToken(authorization));
        return ApiResponse.success(RequestIdHolder.get());
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(authService.currentUser(user), RequestIdHolder.get());
    }

    @PutMapping("/auth/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user, request);
        return ApiResponse.success(RequestIdHolder.get());
    }

    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:manage')")
    public ApiResponse<Void> resetPassword(
            @PathVariable("id") long userId,
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(userId, request);
        return ApiResponse.success(RequestIdHolder.get());
    }

    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasAuthority('user:manage')")
    public ApiResponse<Void> updateStatus(
            @PathVariable("id") long userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        authService.updateStatus(userId, request);
        return ApiResponse.success(RequestIdHolder.get());
    }

    private String extractBearerToken(String authorization) {
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return authorization;
    }
}
