package com.erp.portal.auth.service;

import com.erp.portal.administration.AuditService;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RbacServiceUnitTest {

    @Mock JdbcTemplate jdbc;
    @Mock AuditService auditService;
    @Mock HttpServletRequest request;

    private RbacService rbacService;

    @BeforeEach
    void setUp() {
        rbacService = new RbacService(jdbc, auditService);
    }

    @Test
    void cannotChangeOwnRoles() {
        SecurityPrincipal actor = new SecurityPrincipal(5L, "user5", "User Five", List.of("ADMIN"), Set.of(), "hash");

        assertThatThrownBy(() -> rbacService.replaceUserRoles(5L, Set.of("TEACHER"), actor, request))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 400)
                .hasMessageContaining("Users cannot change their own roles");
    }

    @Test
    void targetUserNotFoundThrows404() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        when(jdbc.queryForObject(contains("FROM users WHERE id = ?"), eq(Integer.class), eq(99L))).thenReturn(0);

        assertThatThrownBy(() -> rbacService.replaceUserRoles(99L, Set.of("TEACHER"), actor, request))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 404);
    }

    @Test
    void unknownRoleCodeThrows400() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        when(jdbc.queryForObject(contains("FROM users WHERE id = ?"), eq(Integer.class), eq(2L))).thenReturn(1);
        when(jdbc.queryForObject(contains("FROM roles WHERE code IN"), eq(Integer.class), any())).thenReturn(0);

        assertThatThrownBy(() -> rbacService.replaceUserRoles(2L, Set.of("NON_EXISTENT"), actor, request))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 400)
                .hasMessageContaining("Unknown role code");
    }

    @Test
    void nonSuperAdminCannotGrantSuperAdminRole() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        when(jdbc.queryForObject(contains("FROM users WHERE id = ?"), eq(Integer.class), eq(2L))).thenReturn(1);
        when(jdbc.queryForObject(contains("FROM roles WHERE code IN"), eq(Integer.class), any())).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(2L))).thenReturn(List.of("TEACHER"));

        assertThatThrownBy(() -> rbacService.replaceUserRoles(2L, Set.of("SUPER_ADMIN"), actor, request))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 403)
                .hasMessageContaining("Only a Super Admin can grant or revoke the SUPER_ADMIN role");
    }

    @Test
    void nonSuperAdminCannotRevokeSuperAdminRole() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        when(jdbc.queryForObject(contains("FROM users WHERE id = ?"), eq(Integer.class), eq(2L))).thenReturn(1);
        when(jdbc.queryForObject(contains("FROM roles WHERE code IN"), eq(Integer.class), any())).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(2L))).thenReturn(List.of("SUPER_ADMIN"));

        assertThatThrownBy(() -> rbacService.replaceUserRoles(2L, Set.of("TEACHER"), actor, request))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 403)
                .hasMessageContaining("Only a Super Admin can grant or revoke the SUPER_ADMIN role");
    }

    @Test
    void superAdminSuccessfullyUpdatesRolesAndAudits() {
        SecurityPrincipal superAdmin = new SecurityPrincipal(1L, "super", "Super", List.of("SUPER_ADMIN"), Set.of(), "hash");
        when(jdbc.queryForObject(contains("FROM users WHERE id = ?"), eq(Integer.class), eq(2L))).thenReturn(1);
        when(jdbc.queryForObject(contains("FROM roles WHERE code IN"), eq(Integer.class), any())).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(2L))).thenReturn(List.of("TEACHER"));

        rbacService.replaceUserRoles(2L, Set.of("ADMIN"), superAdmin, request);

        verify(jdbc).update("DELETE FROM user_roles WHERE user_id = ?", 2L);
        verify(jdbc).update(contains("INSERT INTO user_roles"), eq(2L), eq("ADMIN"));
        verify(auditService).record(eq("RBAC_USER_ROLES_CHANGED"), eq("USER"), eq("2"), eq(superAdmin), isNull(), eq(List.of("TEACHER")), eq(Set.of("ADMIN")), eq(request));
    }

    @Test
    void replaceRolePermissionsThrows404WhenRoleMissing() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq("UNKNOWN_ROLE"))).thenReturn(null);

        assertThatThrownBy(() -> rbacService.replaceRolePermissions("UNKNOWN_ROLE", Set.of("USER:READ"), actor, request))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 404);
    }

    @Test
    void replaceRolePermissionsThrows400WhenPermissionUnknown() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq("TEACHER"))).thenReturn(10L);
        when(jdbc.queryForObject(contains("FROM permissions WHERE code IN"), eq(Integer.class), any())).thenReturn(0);

        assertThatThrownBy(() -> rbacService.replaceRolePermissions("TEACHER", Set.of("UNKNOWN:PERM"), actor, request))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 400);
    }

    @Test
    void replaceRolePermissionsSuccessfullyReplacesAndAudits() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq("TEACHER"))).thenReturn(10L);
        when(jdbc.queryForObject(contains("FROM permissions WHERE code IN"), eq(Integer.class), any())).thenReturn(1);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(10L))).thenReturn(List.of("STUDENT:READ"));

        rbacService.replaceRolePermissions("TEACHER", Set.of("STUDENT:READ"), actor, request);

        verify(jdbc).update("DELETE FROM role_permissions WHERE role_id = ?", 10L);
        verify(jdbc).update(contains("INSERT INTO role_permissions"), eq(10L), eq("STUDENT:READ"));
        verify(auditService).record(eq("RBAC_ROLE_PERMISSIONS_CHANGED"), eq("ROLE"), eq("10"), eq(actor), isNull(), eq(List.of("STUDENT:READ")), eq(Set.of("STUDENT:READ")), eq(request));
    }
}
