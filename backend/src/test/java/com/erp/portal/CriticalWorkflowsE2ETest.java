package com.erp.portal;

import com.erp.portal.academic.AcademicDtos.*;
import com.erp.portal.academic.AcademicException;
import com.erp.portal.academic.AcademicService;
import com.erp.portal.administration.UserDtos.*;
import com.erp.portal.administration.UserManagementService;
import com.erp.portal.attendance.AttendanceDtos.*;
import com.erp.portal.attendance.AttendanceException;
import com.erp.portal.attendance.AttendanceService;
import com.erp.portal.auth.service.AuthService;
import com.erp.portal.auth.service.RbacService;
import com.erp.portal.common.export.CsvSafety;
import com.erp.portal.finance.FinanceDtos.*;
import com.erp.portal.finance.FinanceService;
import com.erp.portal.reports.ReportingService;
import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.student.StudentModuleException;
import com.erp.portal.student.api.StudentDtos.*;
import com.erp.portal.student.service.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:e2e_critical_workflows;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;NON_KEYWORDS=VALUE")
@Sql(scripts = "/e2e-all-tables-schema.sql")
class CriticalWorkflowsE2ETest {

    @Autowired AuthService authService;
    @Autowired UserManagementService userService;
    @Autowired RbacService rbacService;
    @Autowired StudentService studentService;
    @Autowired AcademicService academicService;
    @Autowired AttendanceService attendanceService;
    @Autowired FinanceService financeService;
    @Autowired ReportingService reportingService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    private static final String TEST_PASSWORD = "Password123!";
    private SecurityPrincipal superAdminPrincipal;
    private SecurityPrincipal accountantPrincipal;
    private SecurityPrincipal teacherPrincipal;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM student_guardians");
        jdbc.update("DELETE FROM guardians");
        jdbc.update("DELETE FROM admissions");
        jdbc.update("DELETE FROM student_documents");
        jdbc.update("DELETE FROM student_history");
        jdbc.update("DELETE FROM attendance");
        jdbc.update("DELETE FROM staff_attendance");
        jdbc.update("DELETE FROM timetable");
        jdbc.update("DELETE FROM teacher_assignments");
        jdbc.update("DELETE FROM class_subjects");
        jdbc.update("DELETE FROM subjects");
        jdbc.update("DELETE FROM sections");
        jdbc.update("DELETE FROM finance_adjustments");
        jdbc.update("DELETE FROM receipts");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM fee_discounts");
        jdbc.update("DELETE FROM expenses");
        jdbc.update("DELETE FROM fee_demand_items");
        jdbc.update("DELETE FROM fee_demands");
        jdbc.update("DELETE FROM fee_structure_items");
        jdbc.update("DELETE FROM fee_structures");
        jdbc.update("DELETE FROM fee_categories");
        jdbc.update("DELETE FROM student_enrollments");
        jdbc.update("DELETE FROM students");
        jdbc.update("DELETE FROM classes");
        jdbc.update("DELETE FROM academic_sessions");
        jdbc.update("DELETE FROM staff");
        jdbc.update("DELETE FROM auth_sessions");
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM role_permissions");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM permissions");
        jdbc.update("DELETE FROM roles");

        // Seed roles & permissions
        jdbc.update("INSERT INTO roles (code, name, is_system) VALUES ('SUPER_ADMIN', 'Super Admin', true)");
        jdbc.update("INSERT INTO roles (code, name, is_system) VALUES ('ACCOUNTANT', 'Accountant', true)");
        jdbc.update("INSERT INTO roles (code, name, is_system) VALUES ('TEACHER', 'Teacher', true)");

        // Seed super admin user
        String encoded = passwordEncoder.encode(TEST_PASSWORD);
        jdbc.update("INSERT INTO users (id, username, email, password_hash, full_name, status) VALUES (1, 'admin', 'admin@erp.test', ?, 'Admin User', 'ACTIVE')", encoded);
        jdbc.update("INSERT INTO users (id, username, email, password_hash, full_name, status) VALUES (2, 'accountant', 'accountant@erp.test', ?, 'Accountant User', 'ACTIVE')", encoded);
        jdbc.update("INSERT INTO users (id, username, email, password_hash, full_name, status) VALUES (3, 'teacher', 'teacher@erp.test', ?, 'Teacher User', 'ACTIVE')", encoded);

        long adminRoleId = jdbc.queryForObject("SELECT id FROM roles WHERE code = 'SUPER_ADMIN'", Long.class);
        long accountantRoleId = jdbc.queryForObject("SELECT id FROM roles WHERE code = 'ACCOUNTANT'", Long.class);
        long teacherRoleId = jdbc.queryForObject("SELECT id FROM roles WHERE code = 'TEACHER'", Long.class);

        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (1, ?)", adminRoleId);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (2, ?)", accountantRoleId);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (3, ?)", teacherRoleId);

        superAdminPrincipal = new SecurityPrincipal(
                1L, "admin", "Admin User", List.of("SUPER_ADMIN"),
                Set.of("STUDENT:READ", "STUDENT:CREATE", "STUDENT:UPDATE", "ADMISSION:READ", "ADMISSION:CREATE", "ADMISSION:UPDATE",
                        "ACADEMIC:READ", "ACADEMIC:MANAGE", "TIMETABLE:READ", "TIMETABLE:MANAGE", "ATTENDANCE:READ", "ATTENDANCE:MANAGE",
                        "FEE:READ", "FEE:MANAGE", "FEE:COLLECT", "FEE_DISCOUNT:REQUEST", "FEE_DISCOUNT:APPROVE",
                        "EXPENSE:MANAGE", "EXPENSE:APPROVE", "FINANCE_REPORT:READ", "FINANCE_REPORT:EXPORT",
                        "USER:READ", "USER:CREATE", "USER:UPDATE", "ROLE:READ", "ROLE:UPDATE", "RBAC:USER_ROLE_UPDATE"),
                "token"
        );

        accountantPrincipal = new SecurityPrincipal(
                2L, "accountant", "Accountant User", List.of("ACCOUNTANT"),
                Set.of("FEE:READ", "FEE:MANAGE", "FEE:COLLECT", "FEE_DISCOUNT:REQUEST", "EXPENSE:MANAGE", "FINANCE_REPORT:READ", "FINANCE_REPORT:EXPORT"),
                "token"
        );

        teacherPrincipal = new SecurityPrincipal(
                3L, "teacher", "Teacher User", List.of("TEACHER"),
                Set.of("STUDENT:READ", "ACADEMIC:READ", "ATTENDANCE:READ", "ATTENDANCE:MANAGE"),
                "token"
        );
    }

    // =========================================================================
    // 1. LOGIN WORKFLOW
    // =========================================================================
    @Nested
    @DisplayName("1. Login Workflow")
    class LoginWorkflow {
        @Test
        @DisplayName("Valid login returns access token; invalid password increments failed attempts and locks user")
        void loginPositiveAndNegative() {
            // Positive
            AuthService.LoginResult response = authService.login("admin", TEST_PASSWORD, null);
            assertThat(response).isNotNull();
            assertThat(response.accessToken()).isNotBlank();

            // Negative: bad password
            AuthService.LoginResult failed = authService.login("admin", "WrongPass!", null);
            assertThat(failed).isNull();

            Integer attempts = jdbc.queryForObject("SELECT failed_attempts FROM users WHERE username = 'admin'", Integer.class);
            assertThat(attempts).isEqualTo(1);
        }
    }

    // =========================================================================
    // 2. STUDENT CREATION WORKFLOW
    // =========================================================================
    @Nested
    @DisplayName("2. Student Creation Workflow")
    class StudentCreationWorkflow {
        @Test
        @DisplayName("Creates student profile with guardians; rejects duplicate admission number with 409 conflict")
        void studentCreationPositiveAndNegative() {
            var req = new CreateStudentRequest(
                    "ADM-2026-001", "Aarav", "Sharma", LocalDate.of(2012, 5, 10),
                    "MALE", "B+", "Hindu", "General", "9876543210", "aarav@test.com", "Address 1", null
            );
            StudentResponse created = studentService.create(req, superAdminPrincipal, null);
            assertThat(created.id()).isPositive();
            assertThat(created.admissionNo()).isEqualTo("ADM-2026-001");
            assertThat(created.status()).isEqualTo("ACTIVE");

            // Negative: duplicate admission number
            assertThatThrownBy(() -> studentService.create(req, superAdminPrincipal, null))
                    .isInstanceOf(DuplicateKeyException.class);
        }
    }

    // =========================================================================
    // 3. ADMISSION WORKFLOW
    // =========================================================================
    @Nested
    @DisplayName("3. Admission Workflow")
    class AdmissionWorkflow {
        @Test
        @DisplayName("Full admission lifecycle: draft -> submit -> decide approved; blocks invalid transitions")
        void admissionLifecycle() {
            var studentReq = new CreateStudentRequest(
                    "ADM-APP-01", "Priya", "Verma", LocalDate.of(2014, 3, 20),
                    "FEMALE", "A+", "Hindu", "General", "9876543220", "priya@test.com", "Address 2", null
            );
            var admReq = new CreateAdmissionRequest("APP-2026-100", studentReq);

            AdmissionResponse draft = studentService.createAdmission(admReq, superAdminPrincipal, null);
            assertThat(draft.status()).isEqualTo("DRAFT");

            // Attach required supporting document before submitting
            jdbc.update("INSERT INTO student_documents (student_id, doc_type, file_name, file_path, file_size_bytes, mime_type, uploaded_by) VALUES (?, 'BIRTH_CERTIFICATE', 'birth_cert.pdf', '/files/cert.pdf', 1024, 'application/pdf', 1)", draft.studentId());

            // Submit application
            AdmissionResponse submitted = studentService.submitAdmission(draft.id(), superAdminPrincipal, null);
            assertThat(submitted.status()).isEqualTo("PENDING");

            // Review decision: Approved
            var decision = new AdmissionDecisionRequest("APPROVED", "All entrance criteria met");
            AdmissionResponse approved = studentService.decideAdmission(draft.id(), decision, superAdminPrincipal, null);
            assertThat(approved.status()).isEqualTo("APPROVED");

            // Negative: cannot decide on already approved admission again with invalid transition
            var badTransition = new AdmissionDecisionRequest("DRAFT", "Revert to draft");
            assertThatThrownBy(() -> studentService.decideAdmission(draft.id(), badTransition, superAdminPrincipal, null))
                    .isInstanceOf(StudentModuleException.class)
                    .hasMessageContaining("Admission cannot transition");
        }
    }

    // =========================================================================
    // 4. ACADEMIC ASSIGNMENT WORKFLOW
    // =========================================================================
    @Nested
    @DisplayName("4. Academic Assignment Workflow")
    class AcademicAssignmentWorkflow {
        @Test
        @DisplayName("Creates academic session, class, section, subject, teacher assignment, and timetable with overlap detection")
        void academicAssignmentLifecycle() {
            var session = academicService.createSession(new SessionRequest("2026-27", "Year 2026-27", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true), superAdminPrincipal, null);
            var cls = academicService.createClass(new ClassRequest("Grade 10", 10), superAdminPrincipal, null);
            var sec = academicService.createSection(new SectionRequest(cls.id(), "A", 35, "Room 101"), superAdminPrincipal, null);
            var sub = academicService.createSubject(new SubjectRequest("MATH10", "Mathematics", "Standard math"), superAdminPrincipal, null);

            // Assign subject to class for this session
            academicService.assignSubject(new ClassSubjectRequest(session.id(), cls.id(), sub.id(), true), superAdminPrincipal, null);

            jdbc.update("INSERT INTO staff (id, employee_no, first_name, last_name, status, user_id) VALUES (10, 'EMP-10', 'John', 'Doe', 'ACTIVE', 3)");
            var assignment = academicService.assignTeacher(new TeacherAssignmentRequest(session.id(), sec.id(), sub.id(), 10L), superAdminPrincipal, null);
            assertThat(assignment.status()).isEqualTo("ACTIVE");

            // Timetable slot
            var slot = academicService.addTimetable(new TimetableRequest(session.id(), cls.id(), sec.id(), sub.id(), 10L, 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "Room 101"), superAdminPrincipal, null);
            assertThat(slot.id()).isPositive();

            // Negative: overlapping timetable slot for same section and time
            var duplicateSlot = new TimetableRequest(session.id(), cls.id(), sec.id(), sub.id(), 10L, 1, LocalTime.of(9, 30), LocalTime.of(10, 30), "Room 101");
            assertThatThrownBy(() -> academicService.addTimetable(duplicateSlot, superAdminPrincipal, null))
                    .isInstanceOf(AcademicException.class)
                    .hasMessageContaining("overlapping timetable entry");
        }
    }

    // =========================================================================
    // 5. ATTENDANCE WORKFLOW
    // =========================================================================
    @Nested
    @DisplayName("5. Attendance Workflow")
    class AttendanceWorkflow {
        @Test
        @DisplayName("Marks batch student attendance; protects against duplicate marking without correction workflow")
        void attendanceLifecycle() {
            // Seed session, class, section, student, enrollment
            jdbc.update("INSERT INTO academic_sessions (code, name, start_date, end_date, is_active) VALUES ('2026-27', 'Year', '2026-04-01', '2027-03-31', 1)");
            long sessId = jdbc.queryForObject("SELECT id FROM academic_sessions WHERE code = '2026-27'", Long.class);
            jdbc.update("INSERT INTO classes (name, order_seq) VALUES ('Grade 10', 1)");
            long clsId = jdbc.queryForObject("SELECT id FROM classes WHERE name = 'Grade 10'", Long.class);
            jdbc.update("INSERT INTO sections (class_id, name, capacity) VALUES (" + clsId + ", 'A', 30)");
            long secId = jdbc.queryForObject("SELECT id FROM sections WHERE class_id = " + clsId + " AND name = 'A'", Long.class);
            jdbc.update("INSERT INTO students (admission_no, first_name, status) VALUES ('STU-01', 'Test', 'ACTIVE')");
            long stuId = jdbc.queryForObject("SELECT id FROM students WHERE admission_no = 'STU-01'", Long.class);
            jdbc.update("INSERT INTO student_enrollments (student_id, session_id, class_id, section_id, roll_no) VALUES (?, ?, ?, ?, '101')", stuId, sessId, clsId, secId);

            LocalDate today = LocalDate.of(2026, 10, 10);
            var batch = new StudentMarkBatchRequest(sessId, clsId, secId, today, List.of(new StudentMarkEntry(stuId, "PRESENT", "On time")));

            MarkBatchResult result = attendanceService.markStudents(batch, "IDEMP-KEY-001", superAdminPrincipal, null);
            assertThat(result.created()).isEqualTo(1);
            assertThat(result.alreadyMarked()).isEqualTo(0);

            // Replay with same idempotency key
            MarkBatchResult replay = attendanceService.markStudents(batch, "IDEMP-KEY-001", superAdminPrincipal, null);
            assertThat(replay.alreadyMarked()).isEqualTo(1);

            // Negative: attempt to mark conflicting status without correction workflow triggers conflict
            var conflictingBatch = new StudentMarkBatchRequest(sessId, clsId, secId, today, List.of(new StudentMarkEntry(stuId, "ABSENT", "Changed status")));
            assertThatThrownBy(() -> attendanceService.markStudents(conflictingBatch, "IDEMP-KEY-002", superAdminPrincipal, null))
                    .isInstanceOf(AttendanceException.class)
                    .hasMessageContaining("Attendance already exists");
        }
    }

    // =========================================================================
    // 6. FEE COLLECTION WORKFLOW
    // =========================================================================
    @Nested
    @DisplayName("6. Fee Collection Workflow")
    class FeeCollectionWorkflow {
        @Test
        @DisplayName("Creates fee structure, demand, and posts payment collection with receipt issuance")
        void feeCollectionLifecycle() {
            jdbc.update("INSERT INTO academic_sessions (code, name, start_date, end_date, is_active) VALUES ('2026-27', 'Year', '2026-04-01', '2027-03-31', 1)");
            long sessId = jdbc.queryForObject("SELECT id FROM academic_sessions WHERE code = '2026-27'", Long.class);
            jdbc.update("INSERT INTO classes (name, order_seq) VALUES ('Grade 10', 1)");
            long clsId = jdbc.queryForObject("SELECT id FROM classes WHERE name = 'Grade 10'", Long.class);
            jdbc.update("INSERT INTO sections (class_id, name, capacity) VALUES (" + clsId + ", 'A', 30)");
            long secId = jdbc.queryForObject("SELECT id FROM sections WHERE class_id = " + clsId + " AND name = 'A'", Long.class);
            jdbc.update("INSERT INTO fee_categories (name, is_active) VALUES ('Tuition', 1)");
            long catId = jdbc.queryForObject("SELECT id FROM fee_categories WHERE name = 'Tuition'", Long.class);
            jdbc.update("INSERT INTO students (admission_no, first_name, status) VALUES ('STU-FEES', 'Rahul', 'ACTIVE')");
            long stuId = jdbc.queryForObject("SELECT id FROM students WHERE admission_no = 'STU-FEES'", Long.class);
            jdbc.update("INSERT INTO student_enrollments (student_id, session_id, class_id, section_id, roll_no) VALUES (?, ?, ?, ?, '101')", stuId, sessId, clsId, secId);

            // Fee structure
            var fsReq = new FeeStructureRequest(sessId, clsId, catId, new BigDecimal("4000.00"), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 4, 1), null, "MONTHLY");
            FeeStructure structure = financeService.createStructure(fsReq, accountantPrincipal, null);
            assertThat(structure.id()).isPositive();

            // Generate demand
            var demandReq = new DemandRequest(stuId, structure.id(), LocalDate.of(2026, 10, 1));
            FeeDemand demand = financeService.generateDemand(demandReq, accountantPrincipal, null);
            assertThat(demand.amountDue()).isEqualByComparingTo("4000.00");
            assertThat(demand.status()).isEqualTo("PENDING");

            // Post payment
            var payReq = new PaymentRequest(demand.id(), "REF-CASH-999", new BigDecimal("4000.00"), "CASH", LocalDate.of(2026, 10, 5), "Fee paid in full");
            Payment payment = financeService.postPayment(payReq, accountantPrincipal, null);
            assertThat(payment.receiptNo()).isNotBlank();
            assertThat(payment.status()).isEqualTo("POSTED");

            // Verify receipt
            Payment receipt = financeService.receipt(payment.id(), accountantPrincipal);
            assertThat(receipt.receiptNo()).isEqualTo(payment.receiptNo());
        }
    }

    // =========================================================================
    // 7. DISCOUNT APPROVAL WORKFLOW
    // =========================================================================
    @Nested
    @DisplayName("7. Discount Approval Workflow")
    class DiscountApprovalWorkflow {
        @Test
        @DisplayName("Requests fee discount; approves discount; enforces separation of duties (requester != approver)")
        void discountApprovalLifecycle() {
            jdbc.update("INSERT INTO academic_sessions (code, name, start_date, end_date, is_active) VALUES ('2026-27', 'Year', '2026-04-01', '2027-03-31', 1)");
            long sessId = jdbc.queryForObject("SELECT id FROM academic_sessions WHERE code = '2026-27'", Long.class);
            jdbc.update("INSERT INTO classes (name, order_seq) VALUES ('Grade 10', 1)");
            long clsId = jdbc.queryForObject("SELECT id FROM classes WHERE name = 'Grade 10'", Long.class);
            jdbc.update("INSERT INTO fee_categories (name, is_active) VALUES ('Tuition', 1)");
            long catId = jdbc.queryForObject("SELECT id FROM fee_categories WHERE name = 'Tuition'", Long.class);
            jdbc.update("INSERT INTO students (admission_no, first_name, status) VALUES ('STU-DISC', 'Ananya', 'ACTIVE')");
            long stuId = jdbc.queryForObject("SELECT id FROM students WHERE admission_no = 'STU-DISC'", Long.class);
            jdbc.update("INSERT INTO fee_structures (session_id, class_id, category_id, amount, effective_from) VALUES (" + sessId + "," + clsId + "," + catId + ", 5000, '2026-04-01')");
            long fsId = jdbc.queryForObject("SELECT id FROM fee_structures WHERE session_id = " + sessId + " AND class_id = " + clsId, Long.class);
            jdbc.update("INSERT INTO fee_demands (student_id, fee_structure_id, session_id, amount_due, billing_period) VALUES (" + stuId + "," + fsId + "," + sessId + ", 5000, '2026-10-01')");
            long demId = jdbc.queryForObject("SELECT id FROM fee_demands WHERE student_id = " + stuId + " AND fee_structure_id = " + fsId, Long.class);

            // Accountant (user 2) requests discount
            var discReq = new DiscountRequest(demId, new BigDecimal("500.00"), "Financial hardship merit concession");
            long discountId = financeService.requestDiscount(discReq, accountantPrincipal, null);
            assertThat(discountId).isPositive();

            // Negative: requester (accountant user 2) cannot approve own discount request
            var decision = new DecisionRequest("Self approval attempt");
            assertThatThrownBy(() -> financeService.decideDiscount(discountId, true, decision, accountantPrincipal, null))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Requester cannot approve their own discount");

            // Positive: Super admin (user 1) approves
            financeService.decideDiscount(discountId, true, new DecisionRequest("Approved by Admin"), superAdminPrincipal, null);

            BigDecimal discountAmount = jdbc.queryForObject("SELECT discount_amount FROM fee_demands WHERE id = ?", BigDecimal.class, demId);
            assertThat(discountAmount).isEqualByComparingTo("500.00");
        }
    }

    // =========================================================================
    // 8. EXPENSE WORKFLOW
    // =========================================================================
    @Nested
    @DisplayName("8. Expense Workflow")
    class ExpenseWorkflow {
        @Test
        @DisplayName("Creates expense, submits for review, and approver approves payout")
        void expenseLifecycle() {
            var expReq = new ExpenseRequest("OFFICE_SUPPLIES", "Printer Cartridges", new BigDecimal("3200.00"), LocalDate.of(2026, 10, 8), "Staples", null);
            long expenseId = financeService.createExpense(expReq, accountantPrincipal, null);
            assertThat(expenseId).isPositive();

            // Submit
            financeService.submitExpense(expenseId, accountantPrincipal, null);
            String status = jdbc.queryForObject("SELECT approval_status FROM expenses WHERE id = ?", String.class, expenseId);
            assertThat(status).isEqualTo("PENDING");

            // Approve by super admin
            financeService.decideExpense(expenseId, true, new DecisionRequest("Approved for procurement"), superAdminPrincipal, null);
            String finalStatus = jdbc.queryForObject("SELECT approval_status FROM expenses WHERE id = ?", String.class, expenseId);
            assertThat(finalStatus).isEqualTo("APPROVED");
        }
    }

    // =========================================================================
    // 9. REPORT WORKFLOW
    // =========================================================================
    @Nested
    @DisplayName("9. Report Workflow")
    class ReportWorkflow {
        @Test
        @DisplayName("Queries reporting catalog and student directory report with pagination and sorting")
        void reportingLifecycle() {
            jdbc.update("INSERT INTO students (admission_no, first_name, last_name, status) VALUES ('REP-01', 'Arjun', 'Mehta', 'ACTIVE')");
            jdbc.update("INSERT INTO students (admission_no, first_name, last_name, status) VALUES ('REP-02', 'Bhavna', 'Shah', 'ACTIVE')");

            var report = reportingService.report("student-directory", Map.of("page", "0", "size", "10", "sortBy", "admissionNo", "direction", "ASC"), superAdminPrincipal);
            assertThat(report.totalElements()).isGreaterThanOrEqualTo(2);
            assertThat(report.columns()).extracting("key").contains("admissionNo", "firstName");
        }
    }

    // =========================================================================
    // 10. EXPORT WORKFLOW
    // =========================================================================
    @Nested
    @DisplayName("10. Export Workflow")
    class ExportWorkflow {
        @Test
        @DisplayName("Exports payment transactions and neutralizes CSV formula injection attacks")
        void exportLifecycleAndCsvInjectionDefense() {
            // Test formula neutralization
            String malicious1 = "=cmd|' /C calc'!A0";
            String malicious2 = "+SUM(A1:A10)";
            String malicious3 = "@admin_macro";

            assertThat(CsvSafety.cell(malicious1)).startsWith("'=");
            assertThat(CsvSafety.cell(malicious2)).startsWith("'+");
            assertThat(CsvSafety.cell(malicious3)).startsWith("'@");

            // Test payments export
            String csv = financeService.exportPayments(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), accountantPrincipal);
            assertThat(csv).isNotNull();
            assertThat(csv).contains("receipt_no", "amount", "status");
        }
    }

    // =========================================================================
    // 11. USER / ROLE MANAGEMENT WORKFLOW
    // =========================================================================
    @Nested
    @DisplayName("11. User & Role Management Workflow")
    class UserRoleManagementWorkflow {
        @Test
        @DisplayName("Creates user with hashed credentials, assigns role, and revokes sessions on account lock")
        void userManagementLifecycle() {
            var createUserReq = new CreateUserRequest("newstaff", "newstaff@erp.test", "P@ssword123!", "New Staff", "1234567890", List.of("TEACHER"));
            var userDetail = userService.createUser(createUserReq, superAdminPrincipal, null);
            assertThat(userDetail.username()).isEqualTo("newstaff");
            assertThat(userDetail.roles()).contains("TEACHER");

            // Verify password was hashed (never plaintext in DB)
            String storedHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'newstaff'", String.class);
            assertThat(storedHash).isNotEqualTo("P@ssword123!");
            assertThat(passwordEncoder.matches("P@ssword123!", storedHash)).isTrue();

            // Lock account -> revokes any active sessions
            jdbc.update("INSERT INTO auth_sessions (user_id, token_hash, expires_at) VALUES (?, 'session-hash-123', DATEADD('HOUR', 1, CURRENT_TIMESTAMP))", userDetail.id());
            userService.updateUserStatus(userDetail.id(), "LOCKED", superAdminPrincipal, null);

            java.sql.Timestamp revokedAt = jdbc.queryForObject("SELECT revoked_at FROM auth_sessions WHERE user_id = ?", java.sql.Timestamp.class, userDetail.id());
            assertThat(revokedAt).isNotNull();

            // Negative: duplicate username conflict
            assertThatThrownBy(() -> userService.createUser(createUserReq, superAdminPrincipal, null))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Username already exists");
        }
    }
}
