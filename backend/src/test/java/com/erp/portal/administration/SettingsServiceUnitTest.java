package com.erp.portal.administration;

import com.erp.portal.administration.SettingsDtos.SettingDto;
import com.erp.portal.administration.SettingsDtos.UpdateSettingRequest;
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
class SettingsServiceUnitTest {

    @Mock JdbcTemplate jdbc;
    @Mock AuditService auditService;
    @Mock HttpServletRequest httpRequest;

    private SettingsService service;

    @BeforeEach
    void setUp() {
        service = new SettingsService(jdbc, auditService);
    }

    @Test
    void getSettingThrows404WhenNotFound() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of(), "hash");
        when(jdbc.query(contains("FROM system_settings WHERE setting_key = ?"), any(RowMapper.class), eq("missing.key")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.getSetting("missing.key", actor))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 404);
    }

    @Test
    void getSecuritySettingRequiresSuperAdmin() {
        SecurityPrincipal teacher = new SecurityPrincipal(2L, "teacher", "Teacher", List.of("TEACHER"), Set.of(), "hash");
        SettingDto secSetting = new SettingDto("security.jwt_secret", "secret", "SECURITY", "JWT secret", 1L, Instant.now(), 1);

        when(jdbc.query(contains("FROM system_settings WHERE setting_key = ?"), any(RowMapper.class), eq("security.jwt_secret")))
                .thenReturn(List.of(secSetting));

        assertThatThrownBy(() -> service.getSetting("security.jwt_secret", teacher))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 403)
                .hasMessageContaining("Security settings require super-administrator access");
    }

    @Test
    void accountantAccessingNonGeneralSettingThrowsForbidden() {
        SecurityPrincipal accountant = new SecurityPrincipal(3L, "accountant", "Accountant", List.of("ACCOUNTANT"), Set.of(), "hash");
        SettingDto instSetting = new SettingDto("institution.name", "School", "INSTITUTION", "Name", 1L, Instant.now(), 1);

        when(jdbc.query(contains("FROM system_settings WHERE setting_key = ?"), any(RowMapper.class), eq("institution.name")))
                .thenReturn(List.of(instSetting));

        assertThatThrownBy(() -> service.getSetting("institution.name", accountant))
                .isInstanceOf(ResponseStatusException.class)
                .matches(e -> ((ResponseStatusException) e).getStatusCode().value() == 403)
                .hasMessageContaining("outside the limited accountant settings scope");
    }

    @Test
    void updateSettingIncrementsVersionInsertsHistoryAndAudits() {
        SecurityPrincipal superAdmin = new SecurityPrincipal(1L, "super", "Super", List.of("SUPER_ADMIN"), Set.of(), "hash");
        SettingDto current = new SettingDto("app.name", "Old School", "GENERAL", "App Name", 1L, Instant.now(), 3);
        SettingDto updated = new SettingDto("app.name", "New School", "GENERAL", "App Name", 1L, Instant.now(), 4);

        when(jdbc.query(contains("FROM system_settings WHERE setting_key = ?"), any(RowMapper.class), eq("app.name")))
                .thenReturn(List.of(current))
                .thenReturn(List.of(updated));

        UpdateSettingRequest req = new UpdateSettingRequest("New School");
        SettingDto result = service.updateSetting("app.name", req, superAdmin, httpRequest);

        assertThat(result.value()).isEqualTo("New School");
        assertThat(result.version()).isEqualTo(4);

        verify(jdbc).update(contains("UPDATE system_settings SET setting_value = ?"), eq("New School"), eq(1L), eq(4), eq("app.name"));
        verify(jdbc).update(contains("INSERT INTO system_settings_history"), eq("app.name"), eq("Old School"), eq("New School"), eq(1L), eq(4));
        verify(auditService).record(eq("SETTING_UPDATED"), eq("SETTING"), eq("app.name"), eq(superAdmin), eq("super"), anyMap(), anyMap(), eq(httpRequest));
    }
}
