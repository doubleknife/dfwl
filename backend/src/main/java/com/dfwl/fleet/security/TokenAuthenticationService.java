package com.dfwl.fleet.security;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class TokenAuthenticationService {

    private final Map<String, AuthenticatedUser> sessions = new ConcurrentHashMap<>();

    public String issueToken(AuthenticatedUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, user);
        return token;
    }

    public Optional<Authentication> authenticate(String bearerToken) {
        return Optional.ofNullable(sessions.get(bearerToken))
                .map(user -> {
                    AbstractAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                            user,
                            bearerToken,
                            user.permissions().stream().map(SimpleGrantedAuthority::new).toList());
                    authentication.setDetails(user);
                    return authentication;
                });
    }

    public void revokeToken(String bearerToken) {
        sessions.remove(bearerToken);
    }

    public void revokeUserTokens(long userId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().id() == userId);
    }
}
