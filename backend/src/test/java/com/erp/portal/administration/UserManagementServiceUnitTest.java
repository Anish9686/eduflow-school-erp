package com.erp.portal.administration;

import com.erp.portal.administration.UserDtos.CreateUserRequest;
import com.erp.portal.administration.UserDtos.UpdateUserRequest;
import com.erp.portal.administration.UserDtos.UserDto;
import com.erp.portal.administration.UserDtos.UserPageResponse;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceUnitTest {

    @Mock JdbcTemplate jdbc;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;
    @Mock HttpServletRequest httpRequest;

    private UserManagementService service;

    @BeforeEach
    void setUp() {
        service = new UserManagementService(jdbc, passwordEncoder, auditService);
    }

    @Test
    void createUserRejectsDuplicateUsername() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of("USER:CREATE"), "hash");
        CreateUserRequest req = new CreateUserRequest("duplicateUser", "user@example.com", "Password123!", "Full Name", "+123456", List.of());

        when(jdbc.queryForObject(contains("LOWER(username) = LOWER(?)"), eq(Integer.class), eq("duplicateUser")))
                .thenReturn(1);

        assertThatThrownBy(() -> service.createUser(req, actor, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 409)
                .hasMessageContaining("Username already exists");
    }

    @Test
    void createUserRejectsDuplicateEmail() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of("USER:CREATE"), "hash");
        CreateUserRequest req = new CreateUserRequest("newUser", "dup@example.com", "Password123!", "Full Name", "+123456", List.of());

        when(jdbc.queryForObject(contains("LOWER(username) = LOWER(?)"), eq(Integer.class), eq("newUser")))
                .thenReturn(0);
        when(jdbc.queryForObject(contains("LOWER(email) = LOWER(?)"), eq(Integer.class), eq("dup@example.com")))
                .thenReturn(1);

        assertThatThrownBy(() -> service.createUser(req, actor, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 409)
                .hasMessageContaining("Email already exists");
    }

    @Test
    void createUserRejectsRoleAssignmentWithoutPermission() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of("USER:CREATE"), "hash"); // missing RBAC:USER_ROLE_UPDATE
        CreateUserRequest req = new CreateUserRequest("validUser", "valid@example.com", "Password123!", "Full Name", "+123456", List.of("TEACHER"));

        when(jdbc.queryForObject(contains("LOWER(username) = LOWER(?)"), eq(Integer.class), eq("validUser"))).thenReturn(0);
        when(jdbc.queryForObject(contains("LOWER(email) = LOWER(?)"), eq(Integer.class), eq("valid@example.com"))).thenReturn(0);
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedHash");

        // mock keyholder
        doAnswer(invocation -> {
            KeyHolder kh = invocation.getArgument(1);
            kh.getKeyList().add(Map.of("id", 10L));
            return 1;
        }).when(jdbc).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        assertThatThrownBy(() -> service.createUser(req, actor, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 403)
                .hasMessageContaining("Role assignment requires RBAC:USER_ROLE_UPDATE");
    }

    @Test
    void getUserByIdThrows404WhenNotFound() {
        when(jdbc.query(contains("FROM users WHERE id = ?"), any(RowMapper.class), eq(99L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getUserById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 404);
    }

    @Test
    void updateUserRejectsTakenEmail() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        UserDto existing = new UserDto(10L, "john", "john@old.com", "John Doe", "123", "ACTIVE", 0, null, Instant.now(), Instant.now(), List.of());

        when(jdbc.query(contains("FROM users WHERE id = ?"), any(RowMapper.class), eq(10L)))
                .thenReturn(List.of(existing));
        when(jdbc.queryForObject(contains("LOWER(email) = LOWER(?) AND id <> ?"), eq(Integer.class), eq("taken@new.com"), eq(10L)))
                .thenReturn(1);

        UpdateUserRequest req = new UpdateUserRequest("taken@new.com", "John Doe", "123", null);

        assertThatThrownBy(() -> service.updateUser(10L, req, actor, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 409);
    }

    @Test
    void updateUserStatusRejectsInvalidStatus() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        UserDto existing = new UserDto(10L, "john", "john@old.com", "John Doe", "123", "ACTIVE", 0, null, Instant.now(), Instant.now(), List.of());
        when(jdbc.query(contains("FROM users WHERE id = ?"), any(RowMapper.class), eq(10L))).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.updateUserStatus(10L, "INVALID_STATUS", actor, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 400);
    }

    @Test
    void updateUserStatusLockingRevokesSessions() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        UserDto activeUser = new UserDto(10L, "john", "john@example.com", "John Doe", "123", "ACTIVE", 0, null, Instant.now(), Instant.now(), List.of());
        UserDto lockedUser = new UserDto(10L, "john", "john@example.com", "John Doe", "123", "LOCKED", 0, null, Instant.now(), Instant.now(), List.of());

        when(jdbc.query(contains("FROM users WHERE id = ?"), any(RowMapper.class), eq(10L)))
                .thenReturn(List.of(activeUser))
                .thenReturn(List.of(lockedUser));

        UserDto result = service.updateUserStatus(10L, "LOCKED", actor, httpRequest);

        assertThat(result.status()).isEqualTo("LOCKED");
        verify(jdbc).update(contains("UPDATE users SET status = ?"), eq("LOCKED"), eq(1L), eq(10L));
        verify(jdbc).update(contains("UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ?"), eq(10L));
        verify(auditService).record(eq("USER_STATUS_CHANGED"), eq("USER"), eq("10"), eq(actor), eq("admin"), anyMap(), anyMap(), eq(httpRequest));
    }

    @Test
    void updateUserStatusActiveResetsFailedAttempts() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        UserDto lockedUser = new UserDto(10L, "john", "john@example.com", "John Doe", "123", "LOCKED", 3, null, Instant.now(), Instant.now(), List.of());
        UserDto activeUser = new UserDto(10L, "john", "john@example.com", "John Doe", "123", "ACTIVE", 0, null, Instant.now(), Instant.now(), List.of());

        when(jdbc.query(contains("FROM users WHERE id = ?"), any(RowMapper.class), eq(10L)))
                .thenReturn(List.of(lockedUser))
                .thenReturn(List.of(activeUser));

        UserDto result = service.updateUserStatus(10L, "ACTIVE", actor, httpRequest);

        assertThat(result.status()).isEqualTo("ACTIVE");
        verify(jdbc).update(contains("UPDATE users SET status = 'ACTIVE', failed_attempts = 0"), eq(1L), eq(10L));
    }
}
