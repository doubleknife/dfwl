package com.dfwl.fleet.auth.service;

import com.dfwl.fleet.auth.dto.request.ChangePasswordRequest;
import com.dfwl.fleet.auth.dto.response.CurrentUserResponse;
import com.dfwl.fleet.auth.dto.request.LoginRequest;
import com.dfwl.fleet.auth.dto.response.LoginResponse;
import com.dfwl.fleet.auth.dto.request.ResetPasswordRequest;
import com.dfwl.fleet.auth.dto.request.UpdateUserStatusRequest;
import com.dfwl.fleet.auth.domain.RoleSummary;
import com.dfwl.fleet.auth.domain.UserAccount;
import com.dfwl.fleet.auth.repository.UserAccountRepository;
import com.dfwl.fleet.common.error.BusinessException;
import com.dfwl.fleet.common.error.ErrorCode;
import com.dfwl.fleet.security.AuthenticatedUser;
import com.dfwl.fleet.security.TokenAuthenticationService;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenAuthenticationService tokenAuthenticationService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            TokenAuthenticationService tokenAuthenticationService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenAuthenticationService = tokenAuthenticationService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserAccount account = userAccountRepository.findByPhone(request.phone())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_001));
        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw new BusinessException(ErrorCode.AUTH_001);
        }
        if (!account.enabled()) {
            throw new BusinessException(ErrorCode.AUTH_002);
        }

        userAccountRepository.updateLastLoginAt(account.id(), LocalDateTime.now());
        AuthenticatedUser user = toAuthenticatedUser(account);
        String token = tokenAuthenticationService.issueToken(user);
        return new LoginResponse("Bearer", token, toCurrentUser(user));
    }

    public CurrentUserResponse currentUser(AuthenticatedUser user) {
        return toCurrentUser(user);
    }

    public void logout(String token) {
        tokenAuthenticationService.revokeToken(token);
    }

    @Transactional
    public void changePassword(AuthenticatedUser user, ChangePasswordRequest request) {
        UserAccount account = userAccountRepository.findById(user.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_001));
        if (!passwordEncoder.matches(request.oldPassword(), account.passwordHash())) {
            throw new BusinessException(ErrorCode.AUTH_001);
        }
        userAccountRepository.updatePassword(user.id(), passwordEncoder.encode(request.newPassword()));
        tokenAuthenticationService.revokeUserTokens(user.id());
    }

    @Transactional
    public void resetPassword(long userId, ResetPasswordRequest request) {
        boolean updated = userAccountRepository.updatePassword(userId, passwordEncoder.encode(request.newPassword()));
        if (!updated) {
            throw new BusinessException(ErrorCode.USER_001);
        }
        tokenAuthenticationService.revokeUserTokens(userId);
    }

    @Transactional
    public void updateStatus(long userId, UpdateUserStatusRequest request) {
        boolean updated = userAccountRepository.updateStatus(userId, request.status());
        if (!updated) {
            throw new BusinessException(ErrorCode.USER_001);
        }
        if (request.status() == 0) {
            tokenAuthenticationService.revokeUserTokens(userId);
        }
    }

    private AuthenticatedUser toAuthenticatedUser(UserAccount account) {
        return new AuthenticatedUser(
                account.id(),
                account.phone(),
                account.roleId(),
                account.roleCode(),
                account.roleName(),
                account.permissions());
    }

    private CurrentUserResponse toCurrentUser(AuthenticatedUser user) {
        UserAccountRepository.DriverIdentity driver = userAccountRepository.findDriverIdentity(user.id(), user.phone()).orElse(null);
        return new CurrentUserResponse(
                user.id(),
                user.phone(),
                new RoleSummary(user.roleId(), user.roleCode(), user.roleName()),
                user.permissions(),
                driver == null ? null : driver.driverId(),
                driver == null ? null : driver.driverType(),
                driver == null ? null : driver.driverName(),
                driver == null ? null : driver.currentVehicleId(),
                driver == null ? null : driver.currentPlateNo());
    }
}
