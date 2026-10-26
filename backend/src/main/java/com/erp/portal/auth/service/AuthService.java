package com.erp.portal.auth.service;

import com.erp.portal.administration.AuditService;
import com.erp.portal.auth.persistence.AuthStore;
import com.erp.portal.security.OpaqueToken;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class AuthService {
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final AuthStore authStore;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final long sessionTtlSeconds;

    public AuthService(AuthStore authStore, AuditService auditService, PasswordEncoder passwordEncoder,
                       @Value("${security.session.ttl-seconds:1800}") long sessionTtlSeconds) {
        if (sessionTtlSeconds < 60 || sessionTtlSeconds > 86_400) {
            throw new IllegalArgumentException("security.session.ttl-seconds must be between 60 and 86400");
        }
        this.authStore = authStore;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    @Transactional
    public LoginResult login(String username, String password, HttpServletRequest request) {
        String normalizedUsername = username.trim();
        Optional<AuthStore.Account> accountResult = authStore.findAccount(normalizedUsername);
        AuthStore.Account account = accountResult.orElse(null);
        String hash = account == null ? DUMMY_PASSWORD_HASH : account.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(password, hash);
        boolean active = account != null && "ACTIVE".equals(account.status());

        if (!active || !passwordMatches) {
            if (account != null && active) authStore.recordFailedLogin(account.id());
            auditService.record("AUTH_LOGIN_FAILED", "USER", account == null ? null : Long.toString(account.id()),
                    null, account == null ? null : account.username(), null, null, request);
            return null;
        }

        authStore.recordSuccessfulLogin(account.id());
        String token = OpaqueToken.create();
        Instant expiresAt = Instant.now().plusSeconds(sessionTtlSeconds);
        authStore.createSession(account.id(), OpaqueToken.digest(token), sessionTtlSeconds);
        auditService.record("AUTH_LOGIN_SUCCESS", "USER", Long.toString(account.id()), null,
                account.username(), null, null, request);
        return new LoginResult(token, expiresAt);
    }

    @Transactional
    public void logout(SecurityPrincipal principal, HttpServletRequest request) {
        authStore.revokeSession(principal.sessionHash());
        auditService.record("AUTH_LOGOUT", "USER", Long.toString(principal.userId()), principal,
                null, null, null, request);
    }

    @Transactional
    public void changePassword(SecurityPrincipal principal, String currentPassword, String newPassword, HttpServletRequest request) {
        if (principal == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        if (newPassword == null || newPassword.trim().length() < 8 || newPassword.length() > 100) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "New password must be between 8 and 100 characters");
        }
        AuthStore.Account account = authStore.findAccountById(principal.userId())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "User account not found"));

        if (!passwordEncoder.matches(currentPassword, account.passwordHash())) {
            auditService.record("AUTH_PASSWORD_CHANGE_FAILED", "USER", Long.toString(principal.userId()),
                    principal, account.username(), null, null, request);
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        if (passwordEncoder.matches(newPassword.trim(), account.passwordHash())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "New password cannot be the same as the current password");
        }

        String hashedPassword = passwordEncoder.encode(newPassword.trim());
        authStore.updatePassword(principal.userId(), hashedPassword);
        auditService.record("AUTH_PASSWORD_CHANGED", "USER", Long.toString(principal.userId()),
                principal, account.username(), null, null, request);
    }

    public record LoginResult(String accessToken, Instant expiresAt) {
    }
}
