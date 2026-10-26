package com.erp.portal.administration;

import com.erp.portal.administration.ImportExportDtos.ImportResult;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportExportServiceUnitTest {

    @Mock JdbcTemplate jdbc;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuditService auditService;
    @Mock UserManagementService userService;
    @Mock PlatformTransactionManager transactionManager;
    @Mock HttpServletRequest httpRequest;

    private ImportExportService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ImportExportService(jdbc, passwordEncoder, auditService, userService, transactionManager);
    }

    @Test
    void importUsersEmptyFileReturnsFailure() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of("USER:IMPORT"), "hash");
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);

        ImportResult result = service.importUsers(in, actor, httpRequest);

        assertThat(result.totalRows()).isEqualTo(0);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().getFirst().error()).contains("File is empty");
    }

    @Test
    void importUsersRejectsShortUsernameInvalidEmailAndShortPassword() {
        SecurityPrincipal actor = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"), Set.of("USER:IMPORT"), "hash");
        String csv = """
                username,email,password,fullname
                ab,valid@example.com,Password123!,Short Username
                validuser,notanemail,Password123!,Bad Email
                validuser2,valid2@example.com,12345,Short Password
                """;
        ByteArrayInputStream in = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8));

        ImportResult result = service.importUsers(in, actor, httpRequest);

        assertThat(result.totalRows()).isEqualTo(3);
        assertThat(result.failureCount()).isEqualTo(3);
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.errors()).hasSize(3);
        assertThat(result.errors().get(0).error()).contains("Username must be between 3 and 50 characters");
        assertThat(result.errors().get(1).error()).contains("Valid email address is required");
        assertThat(result.errors().get(2).error()).contains("Password must be at least 8 characters");
    }

    @Test
    void exportUsersNeutralizesFormulaInjection() {
        Map<String, Object> maliciousUser = Map.of(
                "id", 1L,
                "username", "=cmd|' /C calc'!A0",
                "email", "+user@example.com",
                "full_name", "-Full Name",
                "phone", "@123456789",
                "status", "ACTIVE",
                "created_at", "2026-10-01"
        );
        when(jdbc.queryForList(anyString())).thenReturn(List.of(maliciousUser));
        when(userService.findUserRoles(1L)).thenReturn(List.of("TEACHER"));

        String csv = service.exportUsersCsv();

        assertThat(csv).contains("'=cmd|' /C calc'!A0");
        assertThat(csv).contains("'+user@example.com");
        assertThat(csv).contains("'-Full Name");
        assertThat(csv).contains("'@123456789");
    }
}
