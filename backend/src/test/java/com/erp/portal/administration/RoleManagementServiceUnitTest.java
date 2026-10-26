package com.erp.portal.administration;

import com.erp.portal.administration.RoleDtos.CreateRoleRequest;
import com.erp.portal.administration.RoleDtos.RoleDto;
import com.erp.portal.administration.RoleDtos.UpdateRoleRequest;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleManagementServiceUnitTest {

    @Mock JdbcTemplate jdbc;
    @Mock AuditService auditService;
    @Mock HttpServletRequest httpRequest;

    private RoleManagementService service;

    @BeforeEach
    void setUp() {
        service = new RoleManagementService(jdbc, auditService);
    }

    @Test
    void createRoleRejectsDuplicateCode() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        CreateRoleRequest req = new CreateRoleRequest("TEACHER", "Teacher Role", "Description", List.of());

        when(jdbc.queryForObject(contains("UPPER(code) = ?"), eq(Integer.class), eq("TEACHER"))).thenReturn(1);

        assertThatThrownBy(() -> service.createRole(req, actor, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 409)
                .hasMessageContaining("Role code already exists");
    }

    @Test
    void createRoleRejectsUnknownPermissionCode() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        CreateRoleRequest req = new CreateRoleRequest("LIBRARIAN", "Librarian Role", "Description", List.of("UNKNOWN:PERM"));

        when(jdbc.queryForObject(contains("UPPER(code) = ?"), eq(Integer.class), eq("LIBRARIAN"))).thenReturn(0);
        when(jdbc.query(contains("FROM permissions WHERE code = ?"), any(RowMapper.class), eq("UNKNOWN:PERM")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.createRole(req, actor, httpRequest))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 400)
                .hasMessageContaining("Unknown permission code");
    }

    @Test
    void getRoleThrows404WhenNotFound() {
        when(jdbc.query(contains("FROM roles WHERE UPPER(code) = ?"), any(RowMapper.class), eq("NON_EXISTENT")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getRole("NON_EXISTENT"))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 404);
    }

    @Test
    void updateRoleUpdatesNameAndDescriptionAndAudits() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        RoleDto existing = new RoleDto(5L, "TEACHER", "Old Name", "Old Desc", false, Instant.now(), Instant.now(), List.of("STUDENT:READ"));
        RoleDto updated = new RoleDto(5L, "TEACHER", "New Name", "New Desc", false, Instant.now(), Instant.now(), List.of("STUDENT:READ"));

        when(jdbc.query(contains("FROM roles WHERE UPPER(code) = ?"), any(RowMapper.class), eq("TEACHER")))
                .thenReturn(List.of(existing))
                .thenReturn(List.of(updated));

        UpdateRoleRequest req = new UpdateRoleRequest("New Name", "New Desc");
        RoleDto result = service.updateRole("TEACHER", req, actor, httpRequest);

        assertThat(result.name()).isEqualTo("New Name");
        verify(jdbc).update(contains("UPDATE roles SET name = ?, description = ?"), eq("New Name"), eq("New Desc"), eq(5L));
        verify(auditService).record(eq("ROLE_UPDATED"), eq("ROLE"), eq("5"), eq(actor), eq("admin"), anyMap(), anyMap(), eq(httpRequest));
    }
}
