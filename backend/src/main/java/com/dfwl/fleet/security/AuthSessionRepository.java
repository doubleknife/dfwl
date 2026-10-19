package com.dfwl.fleet.security;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthSessionRepository {
    public record StoredSession(long userId, long authVersion, String terminal, Instant expiresAt) { }

    private final JdbcTemplate jdbc;

    public AuthSessionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void create(String tokenHash, long userId, String terminal, Instant issuedAt,
                       Instant expiresAt, long authVersion) {
        jdbc.update("""
                INSERT INTO auth_session(token_hash,user_id,terminal,issued_at,expires_at,auth_version)
                VALUES(?,?,?,?,?,?)
                """, tokenHash, userId, terminal, Timestamp.from(issuedAt),
                Timestamp.from(expiresAt), authVersion);
    }

    public Optional<StoredSession> findActive(String tokenHash, Instant now) {
        return jdbc.query("""
                SELECT user_id,auth_version,terminal,expires_at
                FROM auth_session
                WHERE token_hash=? AND revoked_at IS NULL AND expires_at>?
                """, (rs, row) -> new StoredSession(rs.getLong("user_id"),
                rs.getLong("auth_version"), rs.getString("terminal"),
                rs.getTimestamp("expires_at").toInstant()), tokenHash,
                Timestamp.from(now)).stream().findFirst();
    }

    public void revokeToken(String tokenHash, Instant now) {
        jdbc.update("UPDATE auth_session SET revoked_at=? WHERE token_hash=? AND revoked_at IS NULL",
                Timestamp.from(now), tokenHash);
    }

    public void revokeUser(long userId, Instant now) {
        jdbc.update("UPDATE auth_session SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL",
                Timestamp.from(now), userId);
    }

    public void revokeRole(long roleId, Instant now) {
        jdbc.update("""
                UPDATE auth_session SET revoked_at=?
                WHERE user_id IN (SELECT id FROM sys_user WHERE role_id=?) AND revoked_at IS NULL
                """, Timestamp.from(now), roleId);
    }
}
