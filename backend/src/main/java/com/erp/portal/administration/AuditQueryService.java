package com.erp.portal.administration;

import com.erp.portal.administration.AuditQueryDtos.AuditLogEntry;
import com.erp.portal.administration.AuditQueryDtos.AuditLogPageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AuditQueryService {
    private final JdbcTemplate jdbc;

    public AuditQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public AuditLogPageResponse queryLogs(
            String actor,
            String entityType,
            String action,
            String dateFrom,
            String dateTo,
            int page,
            int size) {

        int safePage = Math.max(0, page);
        int safeSize = (size <= 0 || size > 100) ? 20 : size;
        int offset = safePage * safeSize;

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
            Instant fromInstant = parseDateToInstant(dateFrom.trim(), false);
            if (fromInstant != null) {
                whereSql.append(" AND created_at >= ? ");
                params.add(Timestamp.from(fromInstant));
            }
        }

        if (dateTo != null && !dateTo.isBlank()) {
            Instant toInstant = parseDateToInstant(dateTo.trim(), true);
            if (toInstant != null) {
                whereSql.append(" AND created_at <= ? ");
                params.add(Timestamp.from(toInstant));
            }
        }

        String countSql = "SELECT COUNT(*) FROM audit_logs " + whereSql;
        Long total = jdbc.queryForObject(countSql, Long.class, params.toArray());
        long totalElements = total == null ? 0 : total;

        String selectSql = "SELECT id, actor_user_id, actor_username, action, entity_type, entity_id, " +
                "old_value, new_value, ip_address, trace_id, created_at FROM audit_logs " +
                whereSql + " ORDER BY id DESC LIMIT ? OFFSET ?";

        List<Object> selectParams = new ArrayList<>(params);
        selectParams.add(safeSize);
        selectParams.add(offset);

        List<AuditLogEntry> logs = jdbc.query(selectSql, (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("created_at");
            Long actorId = rs.getObject("actor_user_id") == null ? null : rs.getLong("actor_user_id");
            return new AuditLogEntry(
                    rs.getLong("id"),
                    actorId,
                    rs.getString("actor_username"),
                    rs.getString("action"),
                    rs.getString("entity_type"),
                    rs.getString("entity_id"),
                    rs.getString("old_value"),
                    rs.getString("new_value"),
                    rs.getString("ip_address"),
                    rs.getString("trace_id"),
                    ts == null ? Instant.now() : ts.toInstant()
            );
        }, selectParams.toArray());

        int totalPages = (int) Math.ceil((double) totalElements / safeSize);
        return new AuditLogPageResponse(logs, safePage, safeSize, totalElements, totalPages);
    }

    private Instant parseDateToInstant(String dateStr, boolean endOfDay) {
        try {
            if (dateStr.length() == 10) { // e.g. YYYY-MM-DD
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
