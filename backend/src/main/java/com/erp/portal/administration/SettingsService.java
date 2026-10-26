package com.erp.portal.administration;

import com.erp.portal.administration.SettingsDtos.SettingDto;
import com.erp.portal.administration.SettingsDtos.UpdateSettingRequest;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class SettingsService {
    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public SettingsService(JdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public List<SettingDto> listSettings(SecurityPrincipal actor) {
        boolean securityAdmin = actor != null && actor.roles().contains("SUPER_ADMIN");
        boolean accountantOnly = actor != null && actor.roles().contains("ACCOUNTANT") &&
                !actor.roles().contains("ADMIN") && !actor.roles().contains("SUPER_ADMIN");
        return jdbc.query(
                "SELECT setting_key, setting_value, category, description, updated_by, updated_at, version " +
                        "FROM system_settings " + (securityAdmin ? "" : accountantOnly ? "WHERE category = 'GENERAL' " : "WHERE category <> 'SECURITY' ") +
                        "ORDER BY category ASC, setting_key ASC",
                (rs, rowNum) -> {
                    Timestamp ts = rs.getTimestamp("updated_at");
                    Long updatedBy = rs.getObject("updated_by") == null ? null : rs.getLong("updated_by");
                    return new SettingDto(
                            rs.getString("setting_key"),
                            rs.getString("setting_value"),
                            rs.getString("category"),
                            rs.getString("description"),
                            updatedBy,
                            ts == null ? Instant.now() : ts.toInstant(),
                            rs.getInt("version")
                    );
                }
        );
    }

    public SettingDto getSetting(String key, SecurityPrincipal actor) {
        List<SettingDto> list = jdbc.query(
                "SELECT setting_key, setting_value, category, description, updated_by, updated_at, version " +
                        "FROM system_settings WHERE setting_key = ?",
                (rs, rowNum) -> {
                    Timestamp ts = rs.getTimestamp("updated_at");
                    Long updatedBy = rs.getObject("updated_by") == null ? null : rs.getLong("updated_by");
                    return new SettingDto(
                            rs.getString("setting_key"),
                            rs.getString("setting_value"),
                            rs.getString("category"),
                            rs.getString("description"),
                            updatedBy,
                            ts == null ? Instant.now() : ts.toInstant(),
                            rs.getInt("version")
                    );
                }, key
        );

        if (list.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Setting not found: " + key);
        }
        SettingDto setting = list.getFirst();
        if ("SECURITY".equals(setting.category()) && (actor == null || !actor.roles().contains("SUPER_ADMIN")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Security settings require super-administrator access");
        if (actor != null && actor.roles().contains("ACCOUNTANT") && !actor.roles().contains("ADMIN") &&
                !actor.roles().contains("SUPER_ADMIN") && !"GENERAL".equals(setting.category()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This setting is outside the limited accountant settings scope");
        return setting;
    }

    @Transactional
    public SettingDto updateSetting(String key, UpdateSettingRequest request, SecurityPrincipal actor, HttpServletRequest httpRequest) {
        SettingDto existing = getSetting(key, actor);
        String newValue = request.value();
        int newVersion = existing.version() + 1;
        Long actorId = actor == null ? null : actor.userId();

        jdbc.update(
                "UPDATE system_settings SET setting_value = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP, version = ? WHERE setting_key = ?",
                newValue, actorId, newVersion, key
        );

        jdbc.update(
                "INSERT INTO system_settings_history (setting_key, old_value, new_value, changed_by, changed_at, version) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?)",
                key, existing.value(), newValue, actorId, newVersion
        );

        auditService.record("SETTING_UPDATED", "SETTING", key, actor,
                actor == null ? null : actor.username(),
                Map.of("value", existing.value(), "version", existing.version()),
                Map.of("value", newValue, "version", newVersion), httpRequest);

        return getSetting(key, actor);
    }
}
