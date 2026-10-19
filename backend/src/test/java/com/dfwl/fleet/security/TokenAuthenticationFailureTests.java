package com.dfwl.fleet.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dfwl.fleet.auth.repository.UserAccountRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class TokenAuthenticationFailureTests {
    @Test
    void cachedSessionAndDatabaseRecoveryBothFailClosedWhenMysqlIsUnavailable() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        AuthSessionRepository sessions = mock(AuthSessionRepository.class);
        when(accounts.authState(1L)).thenReturn(Optional.of(
                new UserAccountRepository.AuthState(1, 1, 1, 1, 0, 0)));
        TokenAuthenticationService service = new TokenAuthenticationService(accounts, sessions);
        String token = service.issueToken(new AuthenticatedUser(1L, "13900000001", 1L,
                "ADMIN", "管理员", Set.of("PAGE_DASHBOARD")));
        when(accounts.authState(1L)).thenThrow(new DataAccessResourceFailureException("database unavailable"));
        assertThat(service.authenticate(token)).isEmpty();
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(sessions).findActive(anyString(), any());
        assertThat(new TokenAuthenticationService(accounts, sessions).authenticate(token)).isEmpty();
    }
}
