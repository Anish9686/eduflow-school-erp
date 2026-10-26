package com.erp.portal.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationRbacIntegrationTest {
    private static final String PASSWORD = "S3cure-test-password!";
    private static final String ROOT = "/api/v1";
    private String encodedPassword;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeAll
    void encodeTestPassword() {
        encodedPassword = passwordEncoder.encode(PASSWORD);
    }

    @BeforeEach
    void seedSecurityFixtures() {
        jdbc.update("DELETE FROM audit_logs");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM permissions");
        jdbc.update("DELETE FROM roles");
        jdbc.update("INSERT INTO roles(code,name) VALUES ('SUPER_ADMIN','Super Admin'),('TEACHER','Teacher'),('STUDENT','Student')");
        jdbc.update("INSERT INTO permissions(code,module) VALUES " +
                "('RBAC:USER_ROLE_UPDATE','RBAC'),('RBAC:ROLE_PERMISSION_UPDATE','RBAC')," +
                "('RBAC:ROLE_READ','RBAC'),('USER:CREATE','USER'),('USER:IMPORT','USER')," +
                "('STUDENT:READ','STUDENT'),('STUDENT:CREATE','STUDENT'),('ATTENDANCE:READ','ATTENDANCE')," +
                "('ADMISSION:READ','ADMISSION'),('ADMISSION:CREATE','ADMISSION'),('ADMISSION:UPDATE','ADMISSION')");
        jdbc.update("INSERT INTO users(username,email,password_hash,full_name,status) VALUES " +
                "('root-admin','root-admin@example.invalid',?,'Root Admin','ACTIVE')," +
                "('teacher-one','teacher-one@example.invalid',?,'Teacher One','ACTIVE')," +
                "('student-one','student-one@example.invalid',?,'Student One','ACTIVE')," +
                "('disabled-one','disabled-one@example.invalid',?,'Disabled One','DISABLED')",
                encodedPassword, encodedPassword, encodedPassword, encodedPassword);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u CROSS JOIN roles r " +
                "WHERE u.username='root-admin' AND r.code='SUPER_ADMIN'");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT u.id,r.id FROM users u CROSS JOIN roles r " +
                "WHERE u.username='teacher-one' AND r.code='TEACHER'");
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT r.id,p.id " +
                "FROM roles r CROSS JOIN permissions p WHERE r.code='SUPER_ADMIN'");
    }

    @Test
    void validLoginReturnsOpaqueBearerAndNeverReturnsPasswordHash(CapturedOutput output) throws Exception {
        var result = mockMvc.perform(post(ROOT + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("root-admin", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = body.path("accessToken").asText();
        String storedHash = jdbc.queryForObject("SELECT token_hash FROM auth_sessions", String.class);
        String storedPasswordHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE username='root-admin'", String.class);
        assertThat(storedPasswordHash).startsWith("$2a$").isNotEqualTo(PASSWORD);
        assertThat(storedHash).isNotEqualTo(token).hasSize(64);
        assertThat(body.toString()).doesNotContain(storedPasswordHash, PASSWORD);
        assertThat(output.getOut() + output.getErr()).doesNotContain(PASSWORD, token, storedPasswordHash);
    }

    @Test
    void studentPermissionsAloneDoNotGrantAdmissionWorkflowAccess() throws Exception {
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT r.id,p.id FROM roles r JOIN permissions p ON p.code='STUDENT:CREATE' WHERE r.code='TEACHER'");
        String token = login("teacher-one");

        mockMvc.perform(get(ROOT + "/admissions").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void currentUserResolvesExactDatabaseRoleAndPermissionAssignmentsOnEachRequest() throws Exception {
        String token = login("teacher-one");
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) " +
                "SELECT r.id,p.id FROM roles r JOIN permissions p ON p.code IN ('STUDENT:READ','ATTENDANCE:READ') " +
                "WHERE r.code='TEACHER'");

        mockMvc.perform(get(ROOT + "/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("TEACHER"))
                .andExpect(jsonPath("$.permissions[?(@ == 'STUDENT:READ')]").exists())
                .andExpect(jsonPath("$.permissions[?(@ == 'ATTENDANCE:READ')]").exists())
                .andExpect(jsonPath("$.permissions[?(@ == 'ROLE_TEACHER')]").doesNotExist());

        jdbc.update("DELETE FROM role_permissions WHERE role_id=(SELECT id FROM roles WHERE code='TEACHER') " +
                "AND permission_id=(SELECT id FROM permissions WHERE code='ATTENDANCE:READ')");
        mockMvc.perform(get(ROOT + "/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[?(@ == 'ATTENDANCE:READ')]").doesNotExist());
    }

    @Test
    void invalidCredentialsReturnGeneric401() throws Exception {
        mockMvc.perform(post(ROOT + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("root-admin", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
        mockMvc.perform(post(ROOT + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("no-such-user", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void missingAuthenticationReturns401() throws Exception {
        mockMvc.perform(get(ROOT + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mockMvc.perform(get(ROOT + "/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void invalidAndExpiredAuthenticationReturn401() throws Exception {
        mockMvc.perform(get(ROOT + "/me").header("Authorization", "Bearer " + "x".repeat(43)))
                .andExpect(status().isUnauthorized());

        String token = login("root-admin");
        jdbc.update("UPDATE auth_sessions SET expires_at = ? WHERE token_hash = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), OpaqueToken.digest(token));
        mockMvc.perform(get(ROOT + "/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logoutRevokesToken() throws Exception {
        String token = login("root-admin");
        mockMvc.perform(post(ROOT + "/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ROOT + "/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='AUTH_LOGOUT'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void authorizationAllowsGrantedRoleChangeAndAuditsIt() throws Exception {
        String token = login("root-admin");
        long teacherId = userId("teacher-one");
        mockMvc.perform(put(ROOT + "/admin/security/users/" + teacherId + "/roles")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"STUDENT\"]}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id=r.id " +
                "WHERE ur.user_id=?", String.class, teacherId)).isEqualTo("STUDENT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs " +
                "WHERE action='RBAC_USER_ROLES_CHANGED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void unauthorizedAuthenticatedUserGets403() throws Exception {
        String token = login("teacher-one");
        mockMvc.perform(put(ROOT + "/admin/security/users/" + userId("student-one") + "/roles")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"STUDENT\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void createUserCannotAssignPrivilegedRoleWithoutRoleAssignmentPermission() throws Exception {
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) " +
                "SELECT r.id,p.id FROM roles r CROSS JOIN permissions p " +
                "WHERE r.code='TEACHER' AND p.code='USER:CREATE'");
        String token = login("teacher-one");

        mockMvc.perform(post(ROOT + "/admin/users")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"created-admin\",\"email\":\"created-admin@example.invalid\"," +
                                "\"password\":\"S3cure-test-password!\",\"fullName\":\"Created Admin\"," +
                                "\"roleCodes\":[\"SUPER_ADMIN\"]}"))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username='created-admin'", Integer.class))
                .isZero();
    }

    @Test
    void userImportCannotAssignRolesWithoutRoleAssignmentPermission() throws Exception {
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) " +
                "SELECT r.id,p.id FROM roles r CROSS JOIN permissions p " +
                "WHERE r.code='TEACHER' AND p.code='USER:IMPORT'");
        String token = login("teacher-one");

        mockMvc.perform(post(ROOT + "/admin/import/users")
                        .header("Authorization", bearer(token)).contentType("text/csv")
                        .content("username,email,password,full_name,roles\n" +
                                "imported-admin,imported-admin@example.invalid,S3cure-test-password!," +
                                "Imported Admin,SUPER_ADMIN\n"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(0))
                .andExpect(jsonPath("$.failureCount").value(1));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username='imported-admin'", Integer.class))
                .isZero();
    }

    @Test
    void permissionChangesAreAuditedAndTakeEffectOnExistingSession() throws Exception {
        String adminToken = login("root-admin");
        String teacherToken = login("teacher-one");
        mockMvc.perform(put(ROOT + "/admin/security/roles/TEACHER/permissions")
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"RBAC:ROLE_PERMISSION_UPDATE\",\"STUDENT:READ\"]}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ROOT + "/me").header("Authorization", bearer(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.permissions[?(@ == 'STUDENT:READ')]").exists());
        mockMvc.perform(put(ROOT + "/admin/security/roles/STUDENT/permissions")
                        .header("Authorization", bearer(teacherToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"STUDENT:READ\"]}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs " +
                "WHERE action='RBAC_ROLE_PERMISSIONS_CHANGED'", Integer.class)).isEqualTo(2);
    }

    @Test
    void disabledAccountCannotLogin() throws Exception {
        mockMvc.perform(post(ROOT + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("disabled-one", PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void meIsScopedToAuthenticatedUserAndDoesNotExposeSecrets() throws Exception {
        String token = login("teacher-one");
        mockMvc.perform(get(ROOT + "/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId("teacher-one")))
                .andExpect(jsonPath("$.username").value("teacher-one"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.sessionHash").doesNotExist());
    }

    @Test
    void repeatedFailedLoginsLockAccount() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(ROOT + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("root-admin", "wrong-password")))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE username='root-admin'", String.class))
                .isEqualTo("LOCKED");
    }

    @Test
    void userCanChangePasswordAndLoginWithNewPassword() throws Exception {
        String token = login("student-one");

        mockMvc.perform(post(ROOT + "/auth/change-password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"N3w-secure-pw-2026\"}"))
                .andExpect(status().isNoContent());

        // Old password no longer works
        mockMvc.perform(post(ROOT + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("student-one", PASSWORD)))
                .andExpect(status().isUnauthorized());

        // New password works
        mockMvc.perform(post(ROOT + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("student-one", "N3w-secure-pw-2026")))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordFailsWithWrongCurrentPassword() throws Exception {
        String token = login("teacher-one");

        mockMvc.perform(post(ROOT + "/auth/change-password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongPassword123\",\"newPassword\":\"N3w-secure-pw-2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    void changePasswordFailsWhenNewPasswordSameAsCurrent() throws Exception {
        String token = login("teacher-one");

        mockMvc.perform(post(ROOT + "/auth/change-password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password cannot be the same as the current password"));
    }

    @Test
    void securityHeadersAreEmittedOnResponses() throws Exception {
        mockMvc.perform(get(ROOT + "/info"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'; object-src 'none';"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
    }

    @Test
    void corsHeadersAreEmittedForAllowedOrigin() throws Exception {
        mockMvc.perform(options(ROOT + "/info")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    private String login(String username) throws Exception {
        var result = mockMvc.perform(post(ROOT + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(username, PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username);
    }

    private String loginBody(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(new Credentials(username, password));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Credentials(String username, String password) {
    }
}
