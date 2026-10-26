package com.erp.portal.administration;

import com.erp.portal.administration.ImportExportDtos.ImportResult;
import com.erp.portal.administration.ImportExportDtos.RowFailure;
import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.common.export.CsvSafety;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ImportExportService {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final UserManagementService userService;
    private final TransactionTemplate transactionTemplate;

    public ImportExportService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                               AuditService auditService, UserManagementService userService,
                               PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.userService = userService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public ImportResult importUsers(InputStream inputStream, SecurityPrincipal actor, HttpServletRequest httpRequest) {
        List<RowFailure> failures = new ArrayList<>();
        int rowNumber = 1; // 1 is header
        int totalRows = 0;
        int successCount = 0;
        Set<String> seenUsernames = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            if (line == null) {
                return new ImportResult(0, 0, 0, List.of(new RowFailure(0, "file", "File is empty")));
            }

            // Detect header columns
            List<String> headers = parseCsvLine(line);
            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                colIndex.put(headers.get(i).trim().toLowerCase(Locale.ROOT), i);
            }

            while ((line = reader.readLine()) != null) {
                rowNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                totalRows++;

                List<String> values = parseCsvLine(line);
                String username = getCol(values, colIndex, "username");
                String email = getCol(values, colIndex, "email");
                String password = getCol(values, colIndex, "password");
                String fullName = getCol(values, colIndex, "fullname", "full_name", "name");
                String phone = getCol(values, colIndex, "phone", "phonenumber");
                String rolesRaw = getCol(values, colIndex, "roles", "role");

                String identifier = username != null && !username.isBlank() ? username : (email != null ? email : "Row #" + rowNumber);

                // Row-level validation
                if (username == null || username.trim().length() < 3 || username.trim().length() > 50) {
                    failures.add(new RowFailure(rowNumber, identifier, "Username must be between 3 and 50 characters"));
                    continue;
                }
                username = username.trim();

                if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches() || email.trim().length() > 254) {
                    failures.add(new RowFailure(rowNumber, identifier, "Valid email address is required"));
                    continue;
                }
                email = email.trim().toLowerCase(Locale.ROOT);

                if (password == null || password.trim().length() < 8) {
                    failures.add(new RowFailure(rowNumber, identifier, "Password must be at least 8 characters"));
                    continue;
                }

                // Check duplicates against current database
                if (exists("SELECT COUNT(*) FROM users WHERE LOWER(username) = LOWER(?)", username)) {
                    failures.add(new RowFailure(rowNumber, identifier, "Username '" + username + "' already exists in database"));
                    continue;
                }
                if (seenUsernames.contains(username.toLowerCase(Locale.ROOT))) {
                    failures.add(new RowFailure(rowNumber, identifier, "Username is duplicated in this file"));
                    continue;
                }
                if (exists("SELECT COUNT(*) FROM users WHERE LOWER(email) = LOWER(?)", email)) {
                    failures.add(new RowFailure(rowNumber, identifier, "Email '" + email + "' already exists in database"));
                    continue;
                }
                if (seenEmails.contains(email.toLowerCase(Locale.ROOT))) {
                    failures.add(new RowFailure(rowNumber, identifier, "Email is duplicated in this file"));
                    continue;
                }

                // Parse and validate roles
                List<String> roleCodes = new ArrayList<>();
                boolean roleError = false;
                if (rolesRaw != null && !rolesRaw.isBlank()) {
                    String[] tokens = rolesRaw.split("[;|]");
                    for (String t : tokens) {
                        String r = t.trim().toUpperCase(Locale.ROOT);
                        if (!r.isEmpty()) {
                            Long roleId = queryRole(r);
                            if (roleId == null) {
                                failures.add(new RowFailure(rowNumber, identifier, "Unknown role code: '" + r + "'"));
                                roleError = true;
                                break;
                            }
                            roleCodes.add(r);
                        }
                    }
                }
                if (!roleCodes.isEmpty() && (actor == null ||
                        !actor.permissions().contains("RBAC:USER_ROLE_UPDATE"))) {
                    failures.add(new RowFailure(rowNumber, identifier,
                            "Assigning roles requires RBAC:USER_ROLE_UPDATE"));
                    continue;
                }
                if (roleError) {
                    continue;
                }
                seenUsernames.add(username.toLowerCase(Locale.ROOT));
                seenEmails.add(email.toLowerCase(Locale.ROOT));

                // Insert valid row safely
                try {
                    String hashed = passwordEncoder.encode(password.trim());
                    String finalUsername = username;
                    String finalEmail = email;
                    Long userId = transactionTemplate.execute(status -> insertImportedUser(
                            finalUsername, finalEmail, hashed, fullName, phone, roleCodes, actor));
                    if (userId == null) throw new IllegalStateException("User insert did not return an id");
                    successCount++;
                } catch (Exception ex) {
                    failures.add(new RowFailure(rowNumber, identifier, "Failed to insert record"));
                }
            }
        } catch (Exception e) {
            failures.add(new RowFailure(0, "file", "Error reading CSV file: " + e.getMessage()));
        }

        auditService.record("USER_IMPORT_COMPLETED", "USER", "BATCH", actor,
                actor == null ? null : actor.username(),
                null,
                Map.of("totalRows", totalRows, "successCount", successCount, "failureCount", failures.size()),
                httpRequest);

        return new ImportResult(totalRows, successCount, failures.size(), failures);
    }

    private Long insertImportedUser(String username, String email, String hashedPassword, String fullName,
                                    String phone, List<String> roleCodes, SecurityPrincipal actor) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (username, email, password_hash, full_name, phone, status, failed_attempts, created_by, updated_by) " +
                            "VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, email);
            ps.setString(3, hashedPassword);
            ps.setString(4, fullName == null ? null : fullName.trim());
            ps.setString(5, phone == null ? null : phone.trim());
            ps.setObject(6, actor == null ? null : actor.userId());
            ps.setObject(7, actor == null ? null : actor.userId());
            return ps;
        }, keyHolder);
        Number key = null;
        if (keyHolder.getKeys() != null) {
            Object idValue = keyHolder.getKeys().get("ID");
            if (idValue == null) idValue = keyHolder.getKeys().get("id");
            if (idValue instanceof Number number) key = number;
        }
        if (key == null) key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("User insert did not return an id");
        long userId = key.longValue();
        for (String roleCode : roleCodes) {
            Long roleId = queryRole(roleCode);
            if (roleId == null) throw new IllegalStateException("Role no longer exists");
            jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
        }
        return userId;
    }

    public String exportUsersCsv() {
        StringBuilder csv = new StringBuilder();
        csv.append("id,username,email,fullName,phone,status,roles,createdAt\n");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, username, email, full_name, phone, status, created_at FROM users ORDER BY id ASC"
        );

        for (Map<String, Object> r : rows) {
            long uid = ((Number) r.get("id")).longValue();
            List<String> roles = userService.findUserRoles(uid);
            String rolesJoined = String.join(";", roles);

            csv.append(uid).append(",")
                    .append(escapeCsv((String) r.get("username"))).append(",")
                    .append(escapeCsv((String) r.get("email"))).append(",")
                    .append(escapeCsv((String) r.get("full_name"))).append(",")
                    .append(escapeCsv((String) r.get("phone"))).append(",")
                    .append(escapeCsv((String) r.get("status"))).append(",")
                    .append(escapeCsv(rolesJoined)).append(",")
                    .append(r.get("created_at")).append("\n");
        }

        return csv.toString();
    }

    public String exportAuditLogsCsv(String actor, String entityType, String action, String dateFrom, String dateTo) {
        StringBuilder csv = new StringBuilder();
        csv.append("id,timestamp,actorUsername,action,entityType,entityId,traceId,ipAddress,oldValue,newValue\n");

        StringBuilder whereSql = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (actor != null && !actor.isBlank()) {
            whereSql.append(" AND LOWER(actor_username) LIKE ? ");
            params.add("%" + actor.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (entityType != null && !entityType.isBlank()) {
            whereSql.append(" AND UPPER(entity_type) = ? ");
            params.add(entityType.trim().toUpperCase(Locale.ROOT));
        }
        if (action != null && !action.isBlank()) {
            whereSql.append(" AND UPPER(action) LIKE ? ");
            params.add("%" + action.trim().toUpperCase(Locale.ROOT) + "%");
        }
        if (dateFrom != null && !dateFrom.isBlank()) {
            Instant from = parseDateToInstant(dateFrom.trim(), false);
            if (from != null) {
                whereSql.append(" AND created_at >= ? ");
                params.add(Timestamp.from(from));
            }
        }
        if (dateTo != null && !dateTo.isBlank()) {
            Instant to = parseDateToInstant(dateTo.trim(), true);
            if (to != null) {
                whereSql.append(" AND created_at <= ? ");
                params.add(Timestamp.from(to));
            }
        }

        String sql = "SELECT id, actor_username, action, entity_type, entity_id, old_value, new_value, ip_address, trace_id, created_at " +
                "FROM audit_logs " + whereSql + " ORDER BY id DESC LIMIT 5000";

        List<Map<String, Object>> rows = jdbc.queryForList(sql, params.toArray());
        for (Map<String, Object> r : rows) {
            csv.append(r.get("id")).append(",")
                    .append(r.get("created_at")).append(",")
                    .append(escapeCsv((String) r.get("actor_username"))).append(",")
                    .append(escapeCsv((String) r.get("action"))).append(",")
                    .append(escapeCsv((String) r.get("entity_type"))).append(",")
                    .append(escapeCsv((String) r.get("entity_id"))).append(",")
                    .append(escapeCsv((String) r.get("trace_id"))).append(",")
                    .append(escapeCsv((String) r.get("ip_address"))).append(",")
                    .append(escapeCsv((String) r.get("old_value"))).append(",")
                    .append(escapeCsv((String) r.get("new_value"))).append("\n");
        }

        return csv.toString();
    }

    private String getCol(List<String> values, Map<String, Integer> colIndex, String... aliases) {
        for (String alias : aliases) {
            Integer idx = colIndex.get(alias.toLowerCase(Locale.ROOT));
            if (idx != null && idx < values.size()) {
                String val = values.get(idx);
                return (val == null || val.isBlank()) ? null : val;
            }
        }
        return null;
    }

    private List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }

    private String escapeCsv(String value) {
        return CsvSafety.cell(value);
    }

    private Long queryRole(String code) {
        List<Long> ids = jdbc.query("SELECT id FROM roles WHERE code = ?", (rs, rowNum) -> rs.getLong("id"), code);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private boolean exists(String sql, Object... params) {
        Integer count = jdbc.queryForObject(sql, Integer.class, params);
        return count != null && count > 0;
    }

    private Instant parseDateToInstant(String dateStr, boolean endOfDay) {
        try {
            if (dateStr.length() == 10) {
                LocalDate ld = LocalDate.parse(dateStr);
                return endOfDay ? ld.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().minusMillis(1)
                                : ld.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            }
            return Instant.parse(dateStr);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
