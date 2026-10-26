package com.erp.portal.auth.persistence;

import com.erp.portal.security.SecurityPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class AuthStore {
    private final JdbcTemplate jdbc;

    public AuthStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Account> findAccount(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String clean = username.trim();
        return jdbc.query("SELECT id, username, password_hash, full_name, status, failed_attempts " +
                        "FROM users WHERE LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)",
                (rs, row) -> new Account(rs.getLong("id"), rs.getString("username"),
                        rs.getString("password_hash"), rs.getString("full_name"),
                        rs.getString("status"), rs.getInt("failed_attempts")), clean, clean)
                .stream().findFirst();
    }

    public void recordSuccessfulLogin(long userId) {
        jdbc.update("UPDATE users SET failed_attempts = 0, last_login_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                userId);
    }

    public void recordFailedLogin(long userId) {
        jdbc.update("UPDATE users SET failed_attempts = failed_attempts + 1 WHERE id = ? AND status = 'ACTIVE'",
                userId);
        jdbc.update("UPDATE users SET status = 'LOCKED' WHERE id = ? AND status = 'ACTIVE' " +
                "AND failed_attempts >= 5", userId);
    }

    public void createSession(long userId, String tokenHash, long ttlSeconds) {
        // Store expiry relative to the same database clock used by the active-session query.
        // This avoids rejecting a freshly issued token when the JVM and MySQL clocks/timezones differ.
        jdbc.update("INSERT INTO auth_sessions (user_id, token_hash, expires_at) " +
                        "VALUES (?, ?, TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(6)))",
                userId, tokenHash, ttlSeconds);
    }

    public Optional<SecurityPrincipal> findActiveSession(String tokenHash) {
        List<SessionIdentity> identities = jdbc.query(
                "SELECT s.user_id, u.username, u.full_name FROM auth_sessions s " +
                        "JOIN users u ON u.id = s.user_id " +
                        "WHERE s.token_hash = ? AND s.revoked_at IS NULL " +
                        "AND s.expires_at > CURRENT_TIMESTAMP(6) AND u.status = 'ACTIVE'",
                (rs, row) -> new SessionIdentity(rs.getLong("user_id"), rs.getString("username"),
                        rs.getString("full_name")), tokenHash);
        if (identities.isEmpty()) {
            return Optional.empty();
        }

        SessionIdentity identity = identities.getFirst();
        List<String> roles = jdbc.queryForList(
                "SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id = r.id " +
                        "WHERE ur.user_id = ? ORDER BY r.code", String.class, identity.userId());
        Set<String> permissions = new LinkedHashSet<>(jdbc.queryForList(
                "SELECT DISTINCT p.code FROM permissions p " +
                        "JOIN role_permissions rp ON rp.permission_id = p.id " +
                        "JOIN user_roles ur ON ur.role_id = rp.role_id " +
                        "WHERE ur.user_id = ? ORDER BY p.code", String.class, identity.userId()));
        return Optional.of(new SecurityPrincipal(identity.userId(), identity.username(),
                identity.fullName(), roles, permissions, tokenHash));
    }

    public Optional<Account> findAccountById(long userId) {
        return jdbc.query("SELECT id, username, password_hash, full_name, status, failed_attempts " +
                        "FROM users WHERE id = ?",
                (rs, row) -> new Account(rs.getLong("id"), rs.getString("username"),
                        rs.getString("password_hash"), rs.getString("full_name"),
                        rs.getString("status"), rs.getInt("failed_attempts")), userId)
                .stream().findFirst();
    }

    public void updatePassword(long userId, String hashedPassword) {
        jdbc.update("UPDATE users SET password_hash = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE id = ?",
                hashedPassword, userId);
    }

    public void revokeSession(String tokenHash) {
        jdbc.update("UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP(6) " +
                "WHERE token_hash = ? AND revoked_at IS NULL", tokenHash);
    }

    public record Account(long id, String username, String passwordHash, String fullName,
                          String status, int failedAttempts) {
    }

    private record SessionIdentity(long userId, String username, String fullName) {
    }
}
