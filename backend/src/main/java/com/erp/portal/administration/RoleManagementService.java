package com.erp.portal.administration;

import com.erp.portal.administration.RoleDtos.*;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class RoleManagementService {
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public RoleManagementService(JdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional
    public RoleDto createRole(CreateRoleRequest request, SecurityPrincipal actor, HttpServletRequest httpRequest) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (exists("SELECT COUNT(*) FROM roles WHERE UPPER(code) = ?", code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role code already exists: " + code);
        }

        String name = request.name().trim();
        String description = request.description() == null ? null : request.description().trim();

        Set<String> requestedPermissions = request.permissionCodes() == null
                ? Set.of() : new LinkedHashSet<>(request.permissionCodes().stream().map(String::trim).toList());
        List<String> unknownPermissions = requestedPermissions.stream()
                .filter(permission -> queryPermissionId(permission) == null).toList();
        if (!unknownPermissions.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown permission code(s): " + String.join(", ", unknownPermissions));
        }

        jdbc.update("INSERT INTO roles (code, name, description, is_system) VALUES (?, ?, ?, false)",
                code, name, description);

        Long roleId = jdbc.queryForObject("SELECT id FROM roles WHERE code = ?", Long.class, code);

        List<String> assignedPermissions = new ArrayList<>();
        if (!requestedPermissions.isEmpty()) {
            for (String cleanPerm : requestedPermissions) {
                Long permId = queryPermissionId(cleanPerm);
                jdbc.update("INSERT INTO role_permissions (role_id, permission_id) VALUES (?, ?)", roleId, permId);
                assignedPermissions.add(cleanPerm);
            }
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code", code);
        details.put("name", name);
        details.put("permissions", assignedPermissions);

        auditService.record("ROLE_CREATED", "ROLE", String.valueOf(roleId), actor,
                actor == null ? null : actor.username(), null, details, httpRequest);

        return getRole(code);
    }

    public List<RoleDto> listRoles() {
        return jdbc.query("SELECT id, code, name, description, is_system, created_at, updated_at FROM roles ORDER BY id ASC",
                (rs, rowNum) -> {
                    long roleId = rs.getLong("id");
                    String code = rs.getString("code");
                    Timestamp createdTs = rs.getTimestamp("created_at");
                    Timestamp updatedTs = rs.getTimestamp("updated_at");
                    List<String> permissions = findRolePermissions(roleId);
                    return new RoleDto(
                            roleId,
                            code,
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getBoolean("is_system"),
                            createdTs == null ? Instant.now() : createdTs.toInstant(),
                            updatedTs == null ? Instant.now() : updatedTs.toInstant(),
                            permissions
                    );
                });
    }

    public RoleDto getRole(String code) {
        String cleanCode = code.trim().toUpperCase(Locale.ROOT);
        List<RoleDto> list = jdbc.query(
                "SELECT id, code, name, description, is_system, created_at, updated_at FROM roles WHERE UPPER(code) = ?",
                (rs, rowNum) -> {
                    long roleId = rs.getLong("id");
                    Timestamp createdTs = rs.getTimestamp("created_at");
                    Timestamp updatedTs = rs.getTimestamp("updated_at");
                    return new RoleDto(
                            roleId,
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getString("description"),
                            rs.getBoolean("is_system"),
                            createdTs == null ? Instant.now() : createdTs.toInstant(),
                            updatedTs == null ? Instant.now() : updatedTs.toInstant(),
                            findRolePermissions(roleId)
                    );
                }, cleanCode);

        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found with code: " + cleanCode);
        }
        return list.getFirst();
    }

    @Transactional
    public RoleDto updateRole(String code, UpdateRoleRequest request, SecurityPrincipal actor, HttpServletRequest httpRequest) {
        RoleDto existing = getRole(code);
        String name = request.name().trim();
        String description = request.description() == null ? null : request.description().trim();

        jdbc.update("UPDATE roles SET name = ?, description = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                name, description, existing.id());

        Map<String, Object> oldValues = Map.of("name", existing.name(), "description", existing.description() == null ? "" : existing.description());
        Map<String, Object> newValues = Map.of("name", name, "description", description == null ? "" : description);

        auditService.record("ROLE_UPDATED", "ROLE", String.valueOf(existing.id()), actor,
                actor == null ? null : actor.username(), oldValues, newValues, httpRequest);

        return getRole(code);
    }

    public List<PermissionDto> listPermissions() {
        return jdbc.query("SELECT id, code, description, module FROM permissions ORDER BY module ASC, code ASC",
                (rs, rowNum) -> new PermissionDto(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("description"),
                        rs.getString("module")
                ));
    }

    public List<String> findRolePermissions(long roleId) {
        return jdbc.queryForList(
                "SELECT p.code FROM permissions p JOIN role_permissions rp ON rp.permission_id = p.id WHERE rp.role_id = ? ORDER BY p.code",
                String.class, roleId
        );
    }

    private Long queryPermissionId(String code) {
        List<Long> ids = jdbc.query("SELECT id FROM permissions WHERE code = ?", (rs, rowNum) -> rs.getLong("id"), code);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private boolean exists(String sql, Object... params) {
        Integer count = jdbc.queryForObject(sql, Integer.class, params);
        return count != null && count > 0;
    }
}
