package com.dfwl.fleet.security;

import com.dfwl.fleet.auth.domain.UserAccount;
import com.dfwl.fleet.auth.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TokenAuthenticationService {
    private record CachedSession(AuthenticatedUser user, long authVersion,
                                 long permissionVersion, Instant expiresAt) { }

    private static final Duration WEB_LIFETIME = Duration.ofDays(7);
    private static final Duration MINI_LIFETIME = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    // Keys are digests, never bearer secrets. Database recovery repopulates this cache.
    private final Map<String, CachedSession> sessions = new ConcurrentHashMap<>();
    private final UserAccountRepository accounts;
    private final AuthSessionRepository storedSessions;

    public TokenAuthenticationService(UserAccountRepository accounts, AuthSessionRepository storedSessions) {
        this.accounts = accounts;
        this.storedSessions = storedSessions;
    }

    public String issueToken(AuthenticatedUser user) { return issueToken(user, "WEB"); }

    public String issueToken(AuthenticatedUser user, String terminal) {
        if (!"WEB".equals(terminal) && !"MINI".equals(terminal))
            throw new IllegalArgumentException("Unsupported terminal");
        UserAccountRepository.AuthState state = accounts.authState(user.id())
                .orElseThrow(() -> new AccessDeniedException("账号不存在"));
        if (state.userStatus() != 1 || state.roleStatus() != 1
                || state.roleId() != user.roleId()
                || accounts.isLinkedDriverDisabled(user.id(), user.phone()))
            throw new AccessDeniedException("账号不可登录");
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant now = Instant.now();
        Instant expiry = now.plus("MINI".equals(terminal) ? MINI_LIFETIME : WEB_LIFETIME);
        String hash = digest(token);
        storedSessions.create(hash, user.id(), terminal, now, expiry, state.authVersion());
        sessions.put(hash, new CachedSession(user, state.authVersion(), state.permissionVersion(), expiry));
        return token;
    }

    public Optional<Authentication> authenticate(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) return Optional.empty();
        String hash = digest(bearerToken);
        try {
            CachedSession cached = sessions.get(hash);
            if (cached == null) {
                cached = recover(hash);
                if (cached == null) return Optional.empty();
                CachedSession prior = sessions.putIfAbsent(hash, cached);
                if (prior != null) cached = prior;
            }
            if (!cached.expiresAt().isAfter(Instant.now())) {
                sessions.remove(hash);
                return Optional.empty();
            }
            boolean consistent = false;
            for (int attempt = 0; attempt < 3; attempt++) {
                UserAccountRepository.AuthState state = accounts.authState(cached.user().id()).orElse(null);
                if (state == null || state.userStatus() != 1 || state.roleStatus() != 1
                        || state.authVersion() != cached.authVersion()
                        || state.roleId() != cached.user().roleId()
                        || accounts.isLinkedDriverDisabled(cached.user().id(), cached.user().phone())) {
                    sessions.remove(hash);
                    return Optional.empty();
                }
                if (state.permissionVersion() == cached.permissionVersion()) {
                    consistent = true;
                    break;
                }
                UserAccount account = accounts.findById(cached.user().id()).orElse(null);
                if (account == null || !account.enabled() || !account.roleEnabled()
                        || account.authVersion() != cached.authVersion()
                        || account.roleId() != state.roleId()) {
                    sessions.remove(hash);
                    return Optional.empty();
                }
                if (account.permissionVersion() != state.permissionVersion()) continue;
                cached = new CachedSession(toAuthenticatedUser(account), cached.authVersion(),
                        account.permissionVersion(), cached.expiresAt());
                sessions.put(hash, cached);
            }
            if (!consistent) return Optional.empty();
            AuthenticatedUser active = cached.user();
            AbstractAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                    active, bearerToken,
                    active.permissions().stream().map(SimpleGrantedAuthority::new).toList());
            authentication.setDetails(active);
            return Optional.of(authentication);
        } catch (DataAccessException exception) {
            // An unavailable database can never authorize a stale in-memory grant.
            sessions.remove(hash);
            return Optional.empty();
        }
    }

    private CachedSession recover(String hash) {
        AuthSessionRepository.StoredSession stored = storedSessions.findActive(hash, Instant.now()).orElse(null);
        if (stored == null) return null;
        UserAccount account = accounts.findById(stored.userId()).orElse(null);
        UserAccountRepository.AuthState state = accounts.authState(stored.userId()).orElse(null);
        if (account == null || !account.enabled() || !account.roleEnabled()
                || account.authVersion() != stored.authVersion()
                || state == null || state.userStatus() != 1 || state.roleStatus() != 1
                || state.roleId() != account.roleId()
                || state.permissionVersion() != account.permissionVersion()
                || accounts.isLinkedDriverDisabled(account.id(), account.phone())) return null;
        return new CachedSession(toAuthenticatedUser(account), stored.authVersion(),
                account.permissionVersion(), stored.expiresAt());
    }

    private AuthenticatedUser toAuthenticatedUser(UserAccount account) {
        return new AuthenticatedUser(account.id(), account.phone(), account.roleId(),
                account.roleCode(), account.roleName(), account.permissions());
    }

    public void revokeToken(String bearerToken) {
        String hash = digest(bearerToken);
        storedSessions.revokeToken(hash, Instant.now());
        sessions.remove(hash);
    }

    public void revokeUserTokens(long userId) {
        storedSessions.revokeUser(userId, Instant.now());
        afterCommit(() -> sessions.entrySet().removeIf(entry -> entry.getValue().user().id() == userId));
    }

    public void revokeRoleTokens(long roleId) {
        storedSessions.revokeRole(roleId, Instant.now());
        afterCommit(() -> sessions.entrySet().removeIf(entry -> entry.getValue().user().roleId() == roleId));
    }

    public void revokePhoneTokens(String phone) {
        for (Long userId : accounts.userIdsByPhone(phone)) revokeUserTokens(userId);
    }

    public void invalidateRolePermissions(long roleId) {
        afterCommit(() -> sessions.entrySet().removeIf(entry -> entry.getValue().user().roleId() == roleId));
    }

    public void invalidateUserSnapshot(long userId) {
        afterCommit(() -> sessions.entrySet().removeIf(entry -> entry.getValue().user().id() == userId));
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else action.run();
    }

    public void refreshUserSessions(AuthenticatedUser user) {
        UserAccountRepository.AuthState state = accounts.authState(user.id()).orElse(null);
        if (state == null) return;
        sessions.replaceAll((hash, current) -> current.user().id() == user.id()
                && current.authVersion() == state.authVersion()
                ? new CachedSession(user, state.authVersion(), state.permissionVersion(), current.expiresAt())
                : current);
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
