package com.erp.portal.auth.service;

import com.erp.portal.administration.AuditService;
import com.erp.portal.auth.persistence.AuthStore;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {

    @Mock AuthStore authStore;
    @Mock AuditService auditService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock HttpServletRequest request;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authStore, auditService, passwordEncoder, 1800);
    }

    @Test
    void constructorRejectsInvalidSessionTtl() {
        assertThatThrownBy(() -> new AuthService(authStore, auditService, passwordEncoder, 59))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuthService(authStore, auditService, passwordEncoder, 86401))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginSuccessfulReturnsTokenAndExpiresAt() {
        AuthStore.Account account = new AuthStore.Account(1L, "admin", "hash", "Admin User", "ACTIVE", 0);
        when(authStore.findAccount("admin")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        AuthService.LoginResult result = authService.login(" admin ", "secret", request);

        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.expiresAt()).isNotNull();

        verify(authStore).recordSuccessfulLogin(1L);
        verify(authStore).createSession(eq(1L), anyString(), eq(1800L));
        verify(auditService).record(eq("AUTH_LOGIN_SUCCESS"), eq("USER"), eq("1"), isNull(), eq("admin"), isNull(), isNull(), eq(request));
    }

    @Test
    void loginFailsWhenUserNotFoundPerformsDummyCheckAndAudits() {
        when(authStore.findAccount("unknown")).thenReturn(Optional.empty());
        when(passwordEncoder.matches(eq("secret"), anyString())).thenReturn(false);

        AuthService.LoginResult result = authService.login("unknown", "secret", request);

        assertThat(result).isNull();
        verify(authStore, never()).recordSuccessfulLogin(anyLong());
        verify(authStore, never()).recordFailedLogin(anyLong());
        verify(auditService).record(eq("AUTH_LOGIN_FAILED"), eq("USER"), isNull(), isNull(), isNull(), isNull(), isNull(), eq(request));
    }

    @Test
    void loginFailsWhenPasswordIncorrectIncrementsFailedAttemptsAndAudits() {
        AuthStore.Account account = new AuthStore.Account(2L, "teacher", "hash", "Teacher User", "ACTIVE", 0);
        when(authStore.findAccount("teacher")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        AuthService.LoginResult result = authService.login("teacher", "wrong", request);

        assertThat(result).isNull();
        verify(authStore).recordFailedLogin(2L);
        verify(auditService).record(eq("AUTH_LOGIN_FAILED"), eq("USER"), eq("2"), isNull(), eq("teacher"), isNull(), isNull(), eq(request));
    }

    @Test
    void loginFailsWhenAccountIsInactiveOrLocked() {
        AuthStore.Account account = new AuthStore.Account(3L, "locked", "hash", "Locked User", "LOCKED", 0);
        when(authStore.findAccount("locked")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        AuthService.LoginResult result = authService.login("locked", "secret", request);

        assertThat(result).isNull();
        verify(authStore, never()).recordSuccessfulLogin(anyLong());
        verify(authStore, never()).recordFailedLogin(anyLong());
        verify(auditService).record(eq("AUTH_LOGIN_FAILED"), eq("USER"), eq("3"), isNull(), eq("locked"), isNull(), isNull(), eq(request));
    }

    @Test
    void logoutRevokesSessionAndAudits() {
        SecurityPrincipal principal = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "session-digest-123");

        authService.logout(principal, request);

        verify(authStore).revokeSession("session-digest-123");
        verify(auditService).record(eq("AUTH_LOGOUT"), eq("USER"), eq("1"), eq(principal), isNull(), isNull(), isNull(), eq(request));
    }

    @Test
    void changePasswordRejectsUnauthenticated() {
        assertThatThrownBy(() -> authService.changePassword(null, "old", "newPassword123", request))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 401);
    }

    @Test
    void changePasswordRejectsShortOrLongPassword() {
        SecurityPrincipal principal = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");

        assertThatThrownBy(() -> authService.changePassword(principal, "old", "short", request))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 400);

        assertThatThrownBy(() -> authService.changePassword(principal, "old", "a".repeat(101), request))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 400);
    }

    @Test
    void changePasswordFailsWhenAccountNotFound() {
        SecurityPrincipal principal = new SecurityPrincipal(99L, "missing", "Missing", List.of(), Set.of(), "hash");
        when(authStore.findAccountById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.changePassword(principal, "oldPass", "newPassword123", request))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 404);
    }

    @Test
    void changePasswordFailsWhenCurrentPasswordIncorrect() {
        SecurityPrincipal principal = new SecurityPrincipal(1L, "admin", "Admin", List.of(), Set.of(), "hash");
        AuthStore.Account account = new AuthStore.Account(1L, "admin", "stored-hash", "Admin User", "ACTIVE", 0);
        when(authStore.findAccountById(1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-old", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(principal, "wrong-old", "newPassword123", request))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 400);

        verify(auditService).record(eq("AUTH_PASSWORD_CHANGE_FAILED"), eq("USER"), eq("1"), eq(principal), eq("admin"), isNull(), isNull(), eq(request));
    }

    @Test
    void changePasswordFailsWhenNewPasswordSameAsCurrent() {
        SecurityPrincipal principal = new SecurityPrincipal(1L, "admin", "Admin", List.of(), Set.of(), "hash");
        AuthStore.Account account = new AuthStore.Account(1L, "admin", "stored-hash", "Admin User", "ACTIVE", 0);
        when(authStore.findAccountById(1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("samePass", "stored-hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.changePassword(principal, "samePass", "samePass", request))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 400);
    }

    @Test
    void changePasswordSuccessfulUpdatesStoreAndAudits() {
        SecurityPrincipal principal = new SecurityPrincipal(1L, "admin", "Admin", List.of(), Set.of(), "hash");
        AuthStore.Account account = new AuthStore.Account(1L, "admin", "stored-hash", "Admin User", "ACTIVE", 0);
        when(authStore.findAccountById(1L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("oldPass", "stored-hash")).thenReturn(true);
        when(passwordEncoder.matches("newValidPass123", "stored-hash")).thenReturn(false);
        when(passwordEncoder.encode("newValidPass123")).thenReturn("new-hash");

        authService.changePassword(principal, "oldPass", "newValidPass123", request);

        verify(authStore).updatePassword(1L, "new-hash");
        verify(auditService).record(eq("AUTH_PASSWORD_CHANGED"), eq("USER"), eq("1"), eq(principal), eq("admin"), isNull(), isNull(), eq(request));
    }
}
