package com.erp.portal.administration;

import com.erp.portal.administration.UserDtos.CreateUserRequest;
import com.erp.portal.administration.UserDtos.UpdateUserRequest;
import com.erp.portal.administration.UserDtos.UserDto;
import com.erp.portal.administration.UserDtos.UserPageResponse;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class UserManagementService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserManagementService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public UserDto createUser(CreateUserRequest request, SecurityPrincipal actor, HttpServletRequest httpRequest) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (exists("SELECT COUNT(*) FROM users WHERE LOWER(username) = LOWER(?)", username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        if (exists("SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)", email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        String hashedPassword = passwordEncoder.encode(request.password());
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO users (username, email, password_hash, full_name, phone, status, failed_attempts, created_by, updated_by) " +
                            "VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, hashedPassword);
            ps.setString(4, request.fullName() == null ? null : request.fullName().trim());
            ps.setString(5, request.phone() == null ? null : request.phone().trim());
            ps.setObject(6, actor == null ? null : actor.userId());
            ps.setObject(7, actor == null ? null : actor.userId());
            return ps;
        }, keyHolder);

        Number key = null;
        if (keyHolder.getKeys() != null) {
            Object idVal = keyHolder.getKeys().get("ID");
            if (idVal == null) idVal = keyHolder.getKeys().get("id");
            if (idVal instanceof Number n) key = n;
        }
        if (key == null) {
            key = keyHolder.getKey();
        }
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated user id");
        }
        long userId = key.longValue();

        List<String> assignedRoles = new ArrayList<>();
        if (request.roleCodes() != null && !request.roleCodes().isEmpty()) {
            requireRoleAssignmentPermission(actor);
            Set<String> requestedRoles = new LinkedHashSet<>();
            for (String roleCode : request.roleCodes()) {
                requestedRoles.add(roleCode.trim().toUpperCase(Locale.ROOT));
            }
            List<String> unknownRoles = requestedRoles.stream().filter(code -> queryRole(code) == null).toList();
            if (!unknownRoles.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role code(s): " + String.join(", ", unknownRoles));
            }
            for (String roleCode : requestedRoles) {
                Long roleId = queryRole(roleCode);
                jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
                assignedRoles.add(roleCode);
            }
        }

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("username", username);
        auditDetails.put("email", email);
        auditDetails.put("roles", assignedRoles);

        auditService.record("USER_CREATED", "USER", String.valueOf(userId), actor,
                actor == null ? null : actor.username(), null, auditDetails, httpRequest);

        return getUserById(userId);
    }

    public UserPageResponse listUsers(String search, String status, String role, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = (size <= 0 || size > 100) ? 20 : size;
        int offset = safePage * safeSize;

        StringBuilder whereSql = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (search != null && !search.isBlank()) {
            whereSql.append(" AND (LOWER(u.username) LIKE ? OR LOWER(u.email) LIKE ? OR LOWER(u.full_name) LIKE ?) ");
            String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }

        if (status != null && !status.isBlank()) {
            whereSql.append(" AND u.status = ? ");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }

        if (role != null && !role.isBlank()) {
            whereSql.append(" AND EXISTS (SELECT 1 FROM user_roles ur2 JOIN roles r2 ON r2.id = ur2.role_id WHERE ur2.user_id = u.id AND r2.code = ?) ");
            params.add(role.trim().toUpperCase(Locale.ROOT));
        }

        String countSql = "SELECT COUNT(DISTINCT u.id) FROM users u " + whereSql;
        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());
        long totalElements = total == null ? 0 : total;

        String selectSql = "SELECT u.id, u.username, u.email, u.full_name, u.phone, u.status, u.failed_attempts, " +
                "u.last_login_at, u.created_at, u.updated_at FROM users u " +
                whereSql + " ORDER BY u.id DESC LIMIT ? OFFSET ?";
        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add(safeSize);
        selectParams.add(offset);

        List<UserDto> users = jdbc.query(selectSql, (rs, rowNum) -> {
            long uid = rs.getLong("id");
            Timestamp lastLoginTs = rs.getTimestamp("last_login_at");
            Timestamp createdTs = rs.getTimestamp("created_at");
            Timestamp updatedTs = rs.getTimestamp("updated_at");

            List<String> roles = findUserRoles(uid);
            return new UserDto(
                    uid,
                    rs.getString("username"),
                    rs.getString("email"),
                    rs.getString("full_name"),
                    rs.getString("phone"),
                    rs.getString("status"),
                    rs.getInt("failed_attempts"),
                    lastLoginTs == null ? null : lastLoginTs.toInstant(),
                    createdTs == null ? Instant.now() : createdTs.toInstant(),
                    updatedTs == null ? Instant.now() : updatedTs.toInstant(),
                    roles
            );
        }, selectParams.toArray());

        int totalPages = (int) Math.ceil((double) totalElements / safeSize);
        return new UserPageResponse(users, safePage, safeSize, totalElements, totalPages);
    }

    public UserDto getUserById(long id) {
        List<UserDto> results = jdbc.query(
                "SELECT id, username, email, full_name, phone, status, failed_attempts, last_login_at, created_at, updated_at " +
                        "FROM users WHERE id = ?",
                (rs, rowNum) -> {
                    Timestamp lastLoginTs = rs.getTimestamp("last_login_at");
                    Timestamp createdTs = rs.getTimestamp("created_at");
                    Timestamp updatedTs = rs.getTimestamp("updated_at");
                    return new UserDto(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("full_name"),
                            rs.getString("phone"),
                            rs.getString("status"),
                            rs.getInt("failed_attempts"),
                            lastLoginTs == null ? null : lastLoginTs.toInstant(),
                            createdTs == null ? Instant.now() : createdTs.toInstant(),
                            updatedTs == null ? Instant.now() : updatedTs.toInstant(),
                            findUserRoles(rs.getLong("id"))
                    );
                }, id
        );
        if (results.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found with id: " + id);
        }
        return results.getFirst();
    }

    @Transactional
    public UserDto updateUser(long id, UpdateUserRequest request, SecurityPrincipal actor, HttpServletRequest httpRequest) {
        UserDto existing = getUserById(id);

        String email = request.email() == null ? existing.email() : request.email().trim().toLowerCase(Locale.ROOT);
        if (!email.equalsIgnoreCase(existing.email()) &&
                exists("SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?) AND id <> ?", email, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already taken by another user");
        }

        String fullName = request.fullName() == null ? existing.fullName() : request.fullName().trim();
        String phone = request.phone() == null ? existing.phone() : request.phone().trim();

        if (request.password() != null && !request.password().isBlank()) {
            String newPasswordHash = passwordEncoder.encode(request.password());
            jdbc.update("UPDATE users SET email = ?, full_name = ?, phone = ?, password_hash = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    email, fullName, phone, newPasswordHash, actor == null ? null : actor.userId(), id);
        } else {
            jdbc.update("UPDATE users SET email = ?, full_name = ?, phone = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    email, fullName, phone, actor == null ? null : actor.userId(), id);
        }

        Map<String, Object> oldValues = Map.of("email", existing.email(), "fullName", existing.fullName() == null ? "" : existing.fullName());
        Map<String, Object> newValues = Map.of("email", email, "fullName", fullName);

        auditService.record("USER_UPDATED", "USER", String.valueOf(id), actor,
                actor == null ? null : actor.username(), oldValues, newValues, httpRequest);

        return getUserById(id);
    }

    @Transactional
    public UserDto updateUserStatus(long id, String rawStatus, SecurityPrincipal actor, HttpServletRequest httpRequest) {
        UserDto existing = getUserById(id);
        String targetStatus = rawStatus.trim().toUpperCase(Locale.ROOT);

        if (!Set.of("ACTIVE", "LOCKED", "DISABLED").contains(targetStatus)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status. Allowed values: ACTIVE, LOCKED, DISABLED");
        }

        if (targetStatus.equals(existing.status())) {
            return existing;
        }

        if ("ACTIVE".equals(targetStatus)) {
            // Unlocking / activating resets failed attempts
            jdbc.update("UPDATE users SET status = 'ACTIVE', failed_attempts = 0, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    actor == null ? null : actor.userId(), id);
        } else {
            // Locking or disabling revokes all active sessions for this account immediately
            jdbc.update("UPDATE users SET status = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    targetStatus, actor == null ? null : actor.userId(), id);
            jdbc.update("UPDATE auth_sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL", id);
        }

        auditService.record("USER_STATUS_CHANGED", "USER", String.valueOf(id), actor,
                actor == null ? null : actor.username(),
                Map.of("status", existing.status()),
                Map.of("status", targetStatus), httpRequest);

        return getUserById(id);
    }

    public List<String> findUserRoles(long userId) {
        return jdbc.queryForList(
                "SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id = r.id WHERE ur.user_id = ? ORDER BY r.code",
                String.class, userId
        );
    }

    private Long queryRole(String code) {
        List<Long> ids = jdbc.query("SELECT id FROM roles WHERE code = ?", (rs, rowNum) -> rs.getLong("id"), code);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private boolean exists(String sql, Object... params) {
        Integer count = jdbc.queryForObject(sql, Integer.class, params);
        return count != null && count > 0;
    }

    private void requireRoleAssignmentPermission(SecurityPrincipal actor) {
        if (actor == null || !actor.permissions().contains("RBAC:USER_ROLE_UPDATE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Role assignment requires RBAC:USER_ROLE_UPDATE");
        }
    }
}
