package com.erp.portal.auth.service;

import com.erp.portal.administration.AuditService;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Service
public class RbacService {
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public RbacService(JdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional
    public void replaceUserRoles(long targetUserId, Set<String> requestedRoles,
                                 SecurityPrincipal actor, HttpServletRequest request) {
        if (targetUserId == actor.userId()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Users cannot change their own roles");
        }
        if (!exists("SELECT COUNT(*) FROM users WHERE id = ?", targetUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        Set<String> roles = new TreeSet<>(requestedRoles);
        if (countMatching("SELECT COUNT(*) FROM roles WHERE code IN (" + placeholders(roles.size()) + ")", roles)
                != roles.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role code");
        }

        List<String> previous = jdbc.queryForList("SELECT r.code FROM roles r " +
                "JOIN user_roles ur ON ur.role_id = r.id WHERE ur.user_id = ? ORDER BY r.code",
                String.class, targetUserId);
        if (!actor.roles().contains("SUPER_ADMIN") &&
                (roles.contains("SUPER_ADMIN") || previous.contains("SUPER_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only a Super Admin can grant or revoke the SUPER_ADMIN role");
        }
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", targetUserId);
        for (String role : roles) {
            jdbc.update("INSERT INTO user_roles (user_id, role_id) " +
                    "SELECT ?, id FROM roles WHERE code = ?", targetUserId, role);
        }
        auditService.record("RBAC_USER_ROLES_CHANGED", "USER", Long.toString(targetUserId), actor,
                null, previous, roles, request);
    }

    @Transactional
    public void replaceRolePermissions(String roleCode, Set<String> requestedPermissions,
                                      SecurityPrincipal actor, HttpServletRequest request) {
        Long roleId = jdbc.query("SELECT id FROM roles WHERE code = ?", rs ->
                rs.next() ? rs.getLong(1) : null, roleCode);
        if (roleId == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");

        Set<String> permissions = new TreeSet<>(requestedPermissions);
        if (countMatching("SELECT COUNT(*) FROM permissions WHERE code IN (" +
                placeholders(permissions.size()) + ")", permissions) != permissions.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown permission code");
        }
        List<String> previous = jdbc.queryForList("SELECT p.code FROM permissions p " +
                "JOIN role_permissions rp ON rp.permission_id = p.id WHERE rp.role_id = ? ORDER BY p.code",
                String.class, roleId);
        jdbc.update("DELETE FROM role_permissions WHERE role_id = ?", roleId);
        for (String permission : permissions) {
            jdbc.update("INSERT INTO role_permissions (role_id, permission_id) " +
                    "SELECT ?, id FROM permissions WHERE code = ?", roleId, permission);
        }
        auditService.record("RBAC_ROLE_PERMISSIONS_CHANGED", "ROLE", Long.toString(roleId), actor,
                null, previous, permissions, request);
    }

    private boolean exists(String sql, Object argument) {
        return jdbc.queryForObject(sql, Integer.class, argument) > 0;
    }

    private int countMatching(String sql, Set<String> values) {
        if (values.isEmpty()) return 0;
        return jdbc.queryForObject(sql, Integer.class, values.toArray());
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }
}
