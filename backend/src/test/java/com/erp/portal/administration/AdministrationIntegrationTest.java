package com.erp.portal.administration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdministrationIntegrationTest {
    private static final String ROOT = "/api/v1";
    private static final String TEST_PASSWORD = "Password123!";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;

    private String encodedPassword;

    @BeforeAll
    void setupGlobal() {
        encodedPassword = passwordEncoder.encode(TEST_PASSWORD);
    }

    @BeforeEach
    void seedFixtures() {
        jdbc.update("DELETE FROM system_settings_history");
        jdbc.update("DELETE FROM system_settings");
        jdbc.update("DELETE FROM audit_logs");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM permissions");
        jdbc.update("DELETE FROM roles");

        // Seed roles
        jdbc.update("INSERT INTO roles (code, name, is_system) VALUES ('SUPER_ADMIN', 'Super Admin', true)");
        jdbc.update("INSERT INTO roles (code, name, is_system) VALUES ('TEACHER', 'Teacher', true)");
        jdbc.update("INSERT INTO roles (code, name, is_system) VALUES ('STUDENT', 'Student', true)");
        jdbc.update("INSERT INTO roles (code, name, is_system) VALUES ('ADMIN', 'Admin', true)");

        // Seed permissions
        List<String> perms = List.of(
                "USER:CREATE", "USER:READ", "USER:UPDATE", "USER:MANAGE", "USER:IMPORT", "USER:EXPORT",
                "RBAC:ROLE_CREATE", "RBAC:ROLE_READ", "RBAC:ROLE_UPDATE", "RBAC:USER_ROLE_UPDATE", "RBAC:ROLE_PERMISSION_UPDATE",
                "AUDIT:READ", "AUDIT:EXPORT",
                "SETTINGS:READ", "SETTINGS:UPDATE"
        );
        for (String p : perms) {
            String mod = p.split(":")[0];
            jdbc.update("INSERT INTO permissions (code, description, module) VALUES (?, ?, ?)", p, "Desc " + p, mod);
        }

        // Grant all permissions to SUPER_ADMIN
        jdbc.update("INSERT INTO role_permissions (role_id, permission_id) " +
                "SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN'");

        // Seed super admin user
        jdbc.update("INSERT INTO users (username, email, password_hash, full_name, status) VALUES " +
                "('super-admin', 'superadmin@example.com', ?, 'Super Administrator', 'ACTIVE')", encodedPassword);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u CROSS JOIN roles r " +
                "WHERE u.username = 'super-admin' AND r.code = 'SUPER_ADMIN'");

        // Seed unprivileged user (Teacher has no admin permissions)
        jdbc.update("INSERT INTO users (username, email, password_hash, full_name, status) VALUES " +
                "('plain-user', 'plainuser@example.com', ?, 'Plain Teacher', 'ACTIVE')", encodedPassword);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u CROSS JOIN roles r " +
                "WHERE u.username = 'plain-user' AND r.code = 'TEACHER'");

        jdbc.update("INSERT INTO users (username, email, password_hash, full_name, status) VALUES " +
                "('school-admin', 'schooladmin@example.com', ?, 'School Admin', 'ACTIVE')", encodedPassword);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u CROSS JOIN roles r " +
                "WHERE u.username = 'school-admin' AND r.code = 'ADMIN'");
        jdbc.update("INSERT INTO role_permissions (role_id, permission_id) " +
                "SELECT r.id, p.id FROM roles r CROSS JOIN permissions p " +
                "WHERE r.code='ADMIN' AND p.code IN ('USER:READ','RBAC:ROLE_READ','RBAC:USER_ROLE_UPDATE')");

        // Seed default system setting
        jdbc.update("INSERT INTO system_settings (setting_key, setting_value, category, description, version) VALUES " +
                "('institution.name', 'Acme Test School', 'INSTITUTION', 'Test Name', 1)");
    }

    private String login(String username) throws Exception {
        String json = "{\"username\":\"" + username + "\",\"password\":\"" + TEST_PASSWORD + "\"}";
        MvcResult res = mockMvc.perform(post(ROOT + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void adminCanAssignOperationalRolesButCannotGrantOrRevokeSuperAdmin() throws Exception {
        String token = login("school-admin");
        long plainUserId = jdbc.queryForObject("SELECT id FROM users WHERE username='plain-user'", Long.class);
        long superAdminId = jdbc.queryForObject("SELECT id FROM users WHERE username='super-admin'", Long.class);

        mockMvc.perform(put(ROOT + "/admin/users/" + plainUserId + "/roles")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"STUDENT\"]}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(put(ROOT + "/admin/users/" + plainUserId + "/roles")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"SUPER_ADMIN\"]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(ROOT + "/admin/users/" + superAdminId + "/roles")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"STUDENT\"]}"))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 1. USER MANAGEMENT TESTS (REQ-USER-001)
    // ==========================================

    @Test
    void userCrudLifecycleWithAuditAndSessionControl() throws Exception {
        String adminToken = login("super-admin");

        // 1. Create User
        String createPayload = """
                {
                    "username": "john.doe",
                    "email": "john.doe@example.com",
                    "password": "Password123!",
                    "fullName": "John Doe",
                    "phone": "+1234567890",
                    "roleCodes": ["TEACHER"]
                }
                """;
        MvcResult createRes = mockMvc.perform(post(ROOT + "/admin/users")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("john.doe"))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("TEACHER"))
                .andReturn();

        long createdUserId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        // Verify password is NOT exposed in response
        assertThat(createRes.getResponse().getContentAsString()).doesNotContain("Password123!");
        assertThat(createRes.getResponse().getContentAsString()).doesNotContain("passwordHash");

        // Verify audit log for creation
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'USER_CREATED' AND entity_id = ?",
                Integer.class, String.valueOf(createdUserId));
        assertThat(count).isEqualTo(1);

        // 2. List Users (Paginated & Filterable)
        mockMvc.perform(get(ROOT + "/admin/users?search=john&role=TEACHER")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].username").value("john.doe"));

        // 3. Get User By ID
        mockMvc.perform(get(ROOT + "/admin/users/" + createdUserId)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("John Doe"));

        mockMvc.perform(put(ROOT + "/admin/users/" + createdUserId + "/roles")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"STUDENT\"]}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForList("SELECT r.code FROM roles r JOIN user_roles ur ON ur.role_id = r.id WHERE ur.user_id = ?", String.class, createdUserId))
                .containsExactly("STUDENT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'RBAC_USER_ROLES_CHANGED' AND entity_id = ?", Integer.class,
                String.valueOf(createdUserId))).isEqualTo(1);

        // 4. Update User
        String updatePayload = """
                {
                    "email": "john.newemail@example.com",
                    "fullName": "John Jonathan Doe",
                    "phone": "+9876543210"
                }
                """;
        mockMvc.perform(put(ROOT + "/admin/users/" + createdUserId)
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john.newemail@example.com"))
                .andExpect(jsonPath("$.fullName").value("John Jonathan Doe"));

        // Verify audit log for update
        Integer updateAudit = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'USER_UPDATED' AND entity_id = ?",
                Integer.class, String.valueOf(createdUserId));
        assertThat(updateAudit).isEqualTo(1);

        // 5. Change Status to LOCKED
        mockMvc.perform(put(ROOT + "/admin/users/" + createdUserId + "/status")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"));

        // Verify locked account cannot login
        mockMvc.perform(post(ROOT + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"john.doe\",\"password\":\"Password123!\"}"))
                .andExpect(status().isUnauthorized());

        // 6. Unlock account back to ACTIVE
        mockMvc.perform(put(ROOT + "/admin/users/" + createdUserId + "/status")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.failedAttempts").value(0));

        // Now user can log in
        mockMvc.perform(post(ROOT + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"john.doe\",\"password\":\"Password123!\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void userCreationRejectsDuplicateUsernameOrEmail() throws Exception {
        String token = login("super-admin");
        String payload = """
                {
                    "username": "super-admin",
                    "email": "unique123@example.com",
                    "password": "Password123!"
                }
                """;
        mockMvc.perform(post(ROOT + "/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict());

        String emailPayload = """
                {
                    "username": "brand-new-user",
                    "email": "superadmin@example.com",
                    "password": "Password123!"
                }
                """;
        mockMvc.perform(post(ROOT + "/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailPayload))
                .andExpect(status().isConflict());
    }

    @Test
    void userCreationRejectsUnknownRoleWithoutPersistingUser() throws Exception {
        String token = login("super-admin");
        mockMvc.perform(post(ROOT + "/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"unknown.role\",\"email\":\"unknown.role@example.com\",\"password\":\"Password123!\",\"roleCodes\":[\"MISSING_ROLE\"]}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = 'unknown.role'", Integer.class)).isZero();
    }

    // ==========================================
    // 2. ROLE & PERMISSION TESTS (REQ-ADM-001)
    // ==========================================

    @Test
    void roleLifecycleAndPermissionAssignments() throws Exception {
        String token = login("super-admin");

        // 1. Create Role
        String createRoleJson = """
                {
                    "code": "EXAM_OFFICER",
                    "name": "Examination Officer",
                    "description": "Manages exams and grade submissions",
                    "permissionCodes": ["USER:READ", "AUDIT:READ"]
                }
                """;
        mockMvc.perform(post(ROOT + "/admin/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRoleJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("EXAM_OFFICER"))
                .andExpect(jsonPath("$.isSystem").value(false))
                .andExpect(jsonPath("$.permissions").isArray());

        // 2. List Roles
        mockMvc.perform(get(ROOT + "/admin/roles")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'EXAM_OFFICER')]").exists());

        // 3. Update Role description
        mockMvc.perform(put(ROOT + "/admin/roles/EXAM_OFFICER")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Chief Exam Officer\",\"description\":\"Lead exam administrator\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Chief Exam Officer"));

        // 4. Update Role Permissions
        mockMvc.perform(put(ROOT + "/admin/roles/EXAM_OFFICER/permissions")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionCodes\":[\"SETTINGS:READ\"]}"))
                .andExpect(status().isNoContent());

        // 5. List Permissions
        mockMvc.perform(get(ROOT + "/admin/permissions")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("AUDIT:EXPORT"));
    }

    @Test
    void roleCreationRejectsUnknownPermissionWithoutPersistingRole() throws Exception {
        String token = login("super-admin");
        mockMvc.perform(post(ROOT + "/admin/roles")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BAD_ROLE\",\"name\":\"Bad Role\",\"permissionCodes\":[\"MISSING:ACTION\"]}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE code = 'BAD_ROLE'", Integer.class)).isZero();
    }

    // ==========================================
    // 3. AUDIT LOG QUERY TESTS (REQ-ADM-002, 008)
    // ==========================================

    @Test
    void auditQueryFiltersAndIncludesTraceId() throws Exception {
        String token = login("super-admin");

        // Generate an audit event by updating a setting
        mockMvc.perform(put(ROOT + "/admin/settings/institution.name")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"New Academy\"}"))
                .andExpect(status().isOk());

        // Query audit logs
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        mockMvc.perform(get(ROOT + "/admin/audit-logs")
                        .param("actor", "super-admin")
                        .param("entityType", "SETTING")
                        .param("action", "SETTING_UPDATED")
                        .param("dateFrom", today)
                        .param("dateTo", today)
                        .param("page", "0")
                        .param("size", "10")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("SETTING_UPDATED"))
                .andExpect(jsonPath("$.content[0].entityType").value("SETTING"))
                .andExpect(jsonPath("$.content[0].traceId").isString());
    }

    // ==========================================
    // 4. SETTINGS TESTS (REQ-ADM-007)
    // ==========================================

    @Test
    void settingsListAndUpdateWithVersioningAndHistory() throws Exception {
        String token = login("super-admin");

        // List settings
        mockMvc.perform(get(ROOT + "/admin/settings")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("institution.name"))
                .andExpect(jsonPath("$[0].version").value(1));

        // Update setting
        mockMvc.perform(put(ROOT + "/admin/settings/institution.name")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"Global Excellence Academy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("Global Excellence Academy"))
                .andExpect(jsonPath("$.version").value(2));

        // Check history table has 1 record
        Integer historyCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM system_settings_history WHERE setting_key = 'institution.name' AND version = 2",
                Integer.class
        );
        assertThat(historyCount).isEqualTo(1);
    }

    @Test
    void settingsAccessIsReadOnlyOrCategoryScopedByEffectiveRole() throws Exception {
        jdbc.update("INSERT INTO system_settings (setting_key, setting_value, category, description, version) VALUES ('security.session_timeout_minutes','60','SECURITY','Session timeout',1),('system.notification_banner','Hello','GENERAL','Banner',1)");
        addSettingsUser("manager-settings", "MANAGEMENT", List.of("SETTINGS:READ"));
        addSettingsUser("accountant-settings", "ACCOUNTANT", List.of("SETTINGS:READ", "SETTINGS:UPDATE"));
        addSettingsUser("admin-settings", "ADMIN", List.of("SETTINGS:READ", "SETTINGS:UPDATE"));

        String manager = login("manager-settings");
        mockMvc.perform(get(ROOT + "/admin/settings").header("Authorization", bearer(manager)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.category == 'SECURITY')]").doesNotExist());
        mockMvc.perform(put(ROOT + "/admin/settings/institution.name").header("Authorization", bearer(manager))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"Denied\"}"))
                .andExpect(status().isForbidden());

        String accountant = login("accountant-settings");
        mockMvc.perform(get(ROOT + "/admin/settings").header("Authorization", bearer(accountant)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.category == 'INSTITUTION')]").doesNotExist())
                .andExpect(jsonPath("$[?(@.category == 'SECURITY')]").doesNotExist());
        mockMvc.perform(put(ROOT + "/admin/settings/security.session_timeout_minutes").header("Authorization", bearer(accountant))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"30\"}"))
                .andExpect(status().isForbidden());

        String admin = login("admin-settings");
        mockMvc.perform(get(ROOT + "/admin/settings").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.category == 'INSTITUTION')]").exists())
                .andExpect(jsonPath("$[?(@.category == 'SECURITY')]").doesNotExist());
        mockMvc.perform(put(ROOT + "/admin/settings/security.session_timeout_minutes").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"30\"}"))
                .andExpect(status().isForbidden());
    }

    private void addSettingsUser(String username, String roleCode, List<String> permissions) {
        Integer roleExists = jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE code=?", Integer.class, roleCode);
        if (roleExists == null || roleExists == 0)
            jdbc.update("INSERT INTO roles (code, name, is_system) VALUES (?, ?, true)", roleCode, roleCode);
        jdbc.update("INSERT INTO users (username, email, password_hash, full_name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                username, username + "@example.com", encodedPassword, username);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u CROSS JOIN roles r WHERE u.username=? AND r.code=?", username, roleCode);
        for (String permission : permissions)
            jdbc.update("INSERT INTO role_permissions (role_id, permission_id) SELECT r.id,p.id FROM roles r JOIN permissions p ON p.code=? WHERE r.code=?", permission, roleCode);
    }

    // ==========================================
    // 5. IMPORT TESTS (REQ-ADM-006)
    // ==========================================

    @Test
    void importUsersValidatesRowByRowAndIsolatesFailures() throws Exception {
        String token = login("super-admin");

        String csvContent = """
                username,email,password,full_name,phone,roles
                valid.user1,valid1@example.com,Password123!,Valid User One,+111,TEACHER
                bad_email,invalid-email-format,Password123!,Bad Email,+222,TEACHER
                valid.user2,valid2@example.com,Password123!,Valid User Two,+333,STUDENT
                short,short@example.com,123,Short Pwd,+444,STUDENT
                """;

        MvcResult result = mockMvc.perform(post(ROOT + "/admin/import/users")
                        .header("Authorization", bearer(token))
                        .contentType("text/csv")
                        .content(csvContent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(4))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(2))
                .andExpect(jsonPath("$.errors[0].rowNumber").value(3))
                .andExpect(jsonPath("$.errors[1].rowNumber").value(5))
                .andReturn();

        // Valid users must exist in database
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = 'valid.user1'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = 'valid.user2'", Integer.class)).isEqualTo(1);
        // Invalid users must NOT exist
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = 'bad_email'", Integer.class)).isEqualTo(0);
    }

    @Test
    void importRowsWithDuplicateIdentifiersDoNotCreatePartialOrDuplicateUsers() throws Exception {
        String token = login("super-admin");
        String csv = "username,email,password,full_name,roles\n" +
                "same.user,same@example.com,Password123!,First User,TEACHER\n" +
                "same.user,other@example.com,Password123!,Duplicate Username,STUDENT\n" +
                "other.user,same@example.com,Password123!,Duplicate Email,STUDENT\n";
        mockMvc.perform(post(ROOT + "/admin/import/users")
                        .header("Authorization", bearer(token))
                        .contentType("text/csv")
                        .content(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failureCount").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username IN ('same.user','other.user')", Integer.class)).isEqualTo(1);
    }

    // ==========================================
    // 6. EXPORT TESTS (REQ-ADM-005)
    // ==========================================

    @Test
    void exportUsersAndAuditLogsProducesCsvWithScopeAndHeaders() throws Exception {
        String token = login("super-admin");

        // Export users
        MvcResult usersCsvRes = mockMvc.perform(get(ROOT + "/admin/export/users")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"users_export.csv\""))
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andReturn();

        String usersCsv = usersCsvRes.getResponse().getContentAsString();
        assertThat(usersCsv).startsWith("id,username,email,fullName,phone,status,roles,createdAt");
        assertThat(usersCsv).contains("super-admin");

        // Export audit logs
        MvcResult auditCsvRes = mockMvc.perform(get(ROOT + "/admin/export/audit-logs")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"audit_logs_export.csv\""))
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andReturn();

        String auditCsv = auditCsvRes.getResponse().getContentAsString();
        assertThat(auditCsv).startsWith("id,timestamp,actorUsername,action,entityType,entityId,traceId,ipAddress,oldValue,newValue");
    }

    @Test
    void userCsvExportNeutralizesSpreadsheetFormulaCells() throws Exception {
        jdbc.update("INSERT INTO users (username, email, password_hash, full_name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                "=1+1", "formula@example.com", encodedPassword, "+SUM(A1:A2)");
        String token = login("super-admin");

        String csv = mockMvc.perform(get(ROOT + "/admin/export/users").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(csv).contains("'=1+1").contains("'+SUM(A1:A2)");
    }

    // ==========================================
    // 7. SECURITY & AUTHORIZATION TESTS
    // ==========================================

    @Test
    void unauthenticatedAndUnauthorizedRequestsAreDenied() throws Exception {
        // Unauthenticated request to /admin/users returns 401
        mockMvc.perform(get(ROOT + "/admin/users"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(ROOT + "/admin/export/users"))
                .andExpect(status().isUnauthorized());

        // Authenticated plain user without USER:READ returns 403
        String plainToken = login("plain-user");
        mockMvc.perform(get(ROOT + "/admin/users")
                        .header("Authorization", bearer(plainToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ROOT + "/admin/export/users")
                        .header("Authorization", bearer(plainToken)))
                .andExpect(status().isForbidden());

        // Plain user without SETTINGS:UPDATE returns 403
        mockMvc.perform(put(ROOT + "/admin/settings/institution.name")
                        .header("Authorization", bearer(plainToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"Hacked\"}"))
                .andExpect(status().isForbidden());
    }
}
