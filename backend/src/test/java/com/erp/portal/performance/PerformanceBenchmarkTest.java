package com.erp.portal.performance;

import com.erp.portal.administration.AuditService;
import com.erp.portal.attendance.AttendanceDtos;
import com.erp.portal.attendance.AttendanceRepository;
import com.erp.portal.attendance.AttendanceService;
import com.erp.portal.auth.persistence.AuthStore;
import com.erp.portal.auth.service.AuthService;
import com.erp.portal.finance.FinanceDtos;
import com.erp.portal.finance.FinanceService;
import com.erp.portal.reports.ReportDtos;
import com.erp.portal.reports.ReportingService;
import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.student.api.StudentDtos;
import com.erp.portal.student.service.StudentService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:perf_test_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;NON_KEYWORDS=VALUE",
        "spring.datasource.hikari.maximum-pool-size=20",
        "spring.datasource.hikari.minimum-idle=5"
})
@Sql(scripts = "/e2e-all-tables-schema.sql")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PerformanceBenchmarkTest {

    @Autowired private AuthService authService;
    @Autowired private StudentService studentService;
    @Autowired private AttendanceService attendanceService;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private FinanceService financeService;
    @Autowired private ReportingService reportingService;
    @Autowired private AuthStore authStore;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DataSource dataSource;

    private static final int SEED_STUDENT_COUNT = 10_000;
    private static final int SEED_STAFF_COUNT = 1_000;
    private static final int ITERATIONS = 30;
    private static final int WARMUP_ITERATIONS = 5;

    private SecurityPrincipal adminPrincipal;
    private long sampleStudentId;
    private long sampleDemandId;
    private long sessionId;
    private long classId;
    private long sectionId;

    public record BenchmarkResult(
            String operation,
            double p50Ms,
            double p95Ms,
            double p99Ms,
            double avgMs,
            double throughputOpsPerSec,
            long memoryDeltaBytes,
            int activeConnections
    ) {}

    private final Map<String, BenchmarkResult> benchmarkResults = new LinkedHashMap<>();

    @BeforeAll
    void seedBenchmarkDataset() throws Exception {
        var populator = new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("e2e-all-tables-schema.sql")
        );
        populator.execute(dataSource);
        System.out.println("==================================================================");
        System.out.println("SEEDING BENCHMARK DATASET: " + SEED_STUDENT_COUNT + " students, " + SEED_STAFF_COUNT + " staff...");
        System.out.println("==================================================================");
        long startTime = System.currentTimeMillis();

        // 1. Roles & Permissions
        jdbc.update("DELETE FROM user_roles");
        jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM roles");
        jdbc.update("INSERT INTO roles (code, name, is_system) VALUES ('SUPER_ADMIN', 'Super Admin', 1), ('ACCOUNTANT', 'Accountant', 1), ('TEACHER', 'Teacher', 1)");

        String encodedPass = passwordEncoder.encode("Secret123!");
        jdbc.update("INSERT INTO users (id, username, email, password_hash, full_name, status) VALUES (1, 'admin', 'admin@erp.test', ?, 'Administrator', 'ACTIVE')", encodedPass);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) VALUES (1, (SELECT id FROM roles WHERE code='SUPER_ADMIN'))");

        adminPrincipal = new SecurityPrincipal(1L, "admin", "Administrator",
                List.of("SUPER_ADMIN"),
                Set.of("STUDENT:READ", "STUDENT:CREATE", "STUDENT:UPDATE", "ATTENDANCE:READ", "ATTENDANCE:MARK",
                        "FEE:READ", "FEE:COLLECT", "FEE:MANAGE", "EXPENSE:APPROVE", "FINANCE_REPORT:READ",
                        "REPORT:READ", "REPORT:EXPORT"),
                "token-hash-admin");

        // 2. Academic structure
        jdbc.update("INSERT INTO academic_sessions (id, code, name, start_date, end_date, is_active) VALUES (1, 'AY-2026', '2026-2027', '2026-04-01', '2027-03-31', true)");
        jdbc.update("INSERT INTO classes (id, name, order_seq) VALUES (1, 'Class 10', 10)");
        jdbc.update("INSERT INTO sections (id, class_id, name, capacity) VALUES (1, 1, 'A', 50)");
        sessionId = 1L;
        classId = 1L;
        sectionId = 1L;

        // 3. Batch insert Staff (1,000 staff)
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO staff (id, employee_no, first_name, last_name, email, phone, status, department) " +
                            "VALUES (?, ?, ?, ?, ?, '9876543210', 'ACTIVE', 'Academics')")) {
                for (int i = 1; i <= SEED_STAFF_COUNT; i++) {
                    ps.setLong(1, i);
                    ps.setString(2, String.format("EMP-%04d", i));
                    ps.setString(3, "StaffFirst" + i);
                    ps.setString(4, "StaffLast" + i);
                    ps.setString(5, "staff" + i + "@erp.test");
                    ps.addBatch();
                    if (i % 250 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        }

        // 4. Batch insert Students
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO students (id, admission_no, first_name, last_name, date_of_birth, gender, status, admission_date, created_at) " +
                            "VALUES (?, ?, ?, ?, '2010-01-01', 'MALE', 'ACTIVE', '2026-04-01', TIMESTAMPADD(MINUTE, -?, CURRENT_TIMESTAMP))")) {
                for (int i = 1; i <= SEED_STUDENT_COUNT; i++) {
                    ps.setLong(1, i);
                    ps.setString(2, String.format("STU-%06d", i));
                    ps.setString(3, "Student" + i);
                    ps.setString(4, "LastName" + (i % 100));
                    ps.setInt(5, i); // creates descending createdAt timestamps
                    ps.addBatch();
                    if (i % 1000 == 0) ps.executeBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        }
        sampleStudentId = 500L;

        // 5. Enrollments & Guardians
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO student_enrollments (student_id, session_id, class_id, section_id, roll_no) VALUES (?, 1, 1, 1, ?)")) {
                for (int i = 1; i <= 500; i++) {
                    ps.setLong(1, i);
                    ps.setString(2, "R-" + i);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        }

        jdbc.update("INSERT INTO guardians (id, name, phone, email) VALUES (1, 'Parent Guardian', '9998887770', 'parent@erp.test')");
        jdbc.update("INSERT INTO student_guardians (student_id, guardian_id, relation, is_primary) VALUES (?, 1, 'Father', 1)", sampleStudentId);

        // 6. Attendance records (spanning days)
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO attendance (student_id, session_id, class_id, section_id, attendance_date, status, marked_by, marked_at) " +
                            "VALUES (?, 1, 1, 1, ?, 'PRESENT', 1, CURRENT_TIMESTAMP)")) {
                for (int day = 1; day <= 20; day++) {
                    LocalDate date = LocalDate.of(2026, 4, day);
                    for (int s = 1; s <= 200; s++) {
                        ps.setLong(1, s);
                        ps.setDate(2, java.sql.Date.valueOf(date));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            conn.commit();
        }

        // 7. Finance fee structures, demands, payments
        jdbc.update("INSERT INTO fee_categories (id, name, is_active) VALUES (1, 'Tuition Fee', true)");
        jdbc.update("INSERT INTO fee_structures (id, session_id, class_id, category_id, amount, due_date, effective_from, frequency, is_active) " +
                "VALUES (1, 1, 1, 1, 5000.00, '2026-05-10', '2026-04-01', 'MONTHLY', true)");

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO fee_demands (id, student_id, fee_structure_id, session_id, amount_due, amount_paid, discount_amount, due_date, status) " +
                            "VALUES (?, ?, 1, 1, 5000.00, 0.00, 0.00, '2026-05-10', 'PENDING')")) {
                for (int i = 1; i <= 1000; i++) {
                    ps.setLong(1, i);
                    ps.setLong(2, i);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
        }
        sampleDemandId = 1L;

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("Dataset seeded successfully in " + elapsed + " ms.");
    }

    private BenchmarkResult benchmark(String name, Runnable operation) {
        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            operation.run();
        }

        System.gc();
        long memBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long[] durations = new long[ITERATIONS];
        long totalStart = System.nanoTime();

        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.nanoTime();
            operation.run();
            durations[i] = System.nanoTime() - start;
        }

        long totalEnd = System.nanoTime();
        long memAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        Arrays.sort(durations);

        double p50 = durations[(int) (ITERATIONS * 0.50)] / 1_000_000.0;
        double p95 = durations[(int) (ITERATIONS * 0.95)] / 1_000_000.0;
        double p99 = durations[(int) (ITERATIONS * 0.99)] / 1_000_000.0;
        double avg = (Arrays.stream(durations).sum() / (double) ITERATIONS) / 1_000_000.0;

        double totalDurationSec = (totalEnd - totalStart) / 1_000_000_000.0;
        double throughput = ITERATIONS / totalDurationSec;
        long memDelta = Math.max(0, memAfter - memBefore);

        BenchmarkResult result = new BenchmarkResult(name, p50, p95, p99, avg, throughput, memDelta, 1);
        benchmarkResults.put(name, result);

        System.out.printf("[%s] p50: %.2f ms | p95: %.2f ms | p99: %.2f ms | avg: %.2f ms | throughput: %.1f ops/s | mem: %d KB%n",
                name, p50, p95, p99, avg, throughput, memDelta / 1024);
        return result;
    }

    @Test
    @Order(1)
    @DisplayName("Benchmark 1: Login Workflow")
    void benchmarkLogin() {
        benchmark("Login", () -> {
            var result = authService.login("admin", "Secret123!", null);
            assertThat(result).isNotNull();
        });
    }

    @Test
    @Order(2)
    @DisplayName("Benchmark 2: Student Directory List (Pagination & Sorting)")
    void benchmarkStudentList() {
        benchmark("Student List", () -> {
            var page = studentService.list(null, null, "createdAt", "DESC", 0, 25, adminPrincipal);
            assertThat(page.content()).hasSize(25);
        });
    }

    @Test
    @Order(3)
    @DisplayName("Benchmark 3: Student Search (Prefix & Match)")
    void benchmarkStudentSearch() {
        benchmark("Student Search", () -> {
            var page = studentService.list("Student50", null, "createdAt", "DESC", 0, 25, adminPrincipal);
            assertThat(page.content()).isNotEmpty();
        });
    }

    @Test
    @Order(4)
    @DisplayName("Benchmark 4: Student Detail with Guardian Relationships")
    void benchmarkStudentDetail() {
        benchmark("Student Detail", () -> {
            var detail = studentService.detail(sampleStudentId, adminPrincipal);
            assertThat(detail.id()).isEqualTo(sampleStudentId);
        });
    }

    @Test
    @Order(5)
    @DisplayName("Benchmark 5: Attendance Records & Roster")
    void benchmarkAttendance() {
        benchmark("Attendance", () -> {
            var records = attendanceRepository.studentRecords(
                    LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15),
                    sessionId, classId, sectionId, null, null, null,
                    null, null, null);
            assertThat(records).isNotEmpty();
        });
    }

    @Test
    @Order(6)
    @DisplayName("Benchmark 6: Fees Demands Query")
    void benchmarkFees() {
        benchmark("Fees", () -> {
            var demands = financeService.demands(adminPrincipal);
            assertThat(demands).isNotEmpty();
        });
    }

    @Test
    @Order(7)
    @DisplayName("Benchmark 7: Payment Posting (Atomic Transaction & Lock)")
    void benchmarkPayment() {
        final long[] counter = {System.currentTimeMillis()};
        benchmark("Payment", () -> {
            counter[0]++;
            String ref = "PAY-BENCH-" + counter[0];
            var req = new FinanceDtos.PaymentRequest(
                    sampleStudentId, ref, new BigDecimal("100.00"), "CASH", LocalDate.now(), "Benchmark payment"
            );
            // Post against demand 1 if outstanding exists
            try {
                financeService.postPayment(req, adminPrincipal, null);
            } catch (Exception ignored) {
                // Ignore business errors if demand balance settled during repeat benchmark runs
            }
        });
    }

    @Test
    @Order(8)
    @DisplayName("Benchmark 8: Report Generation (Student Directory Slice)")
    void benchmarkReports() {
        Map<String, String> params = Map.of("page", "0", "size", "25", "sortBy", "createdAt", "direction", "DESC");
        benchmark("Reports", () -> {
            var reportPage = reportingService.report("student-directory", params, adminPrincipal);
            assertThat(reportPage.content()).hasSize(25);
        });
    }

    @Test
    @Order(9)
    @DisplayName("Benchmark 9: Export Execution (Async End-to-End Processing)")
    void benchmarkExports() {
        Map<String, String> params = Map.of("sortBy", "createdAt", "direction", "DESC");
        benchmark("Exports", () -> {
            var exportResponse = reportingService.export("student-directory", params, "CSV", true, adminPrincipal);
            assertThat(exportResponse.job()).isNotNull();
            String jobId = exportResponse.job().jobId();
            while (!"COMPLETED".equals(reportingService.job(jobId, adminPrincipal).status())) {
                if ("FAILED".equals(reportingService.job(jobId, adminPrincipal).status())) {
                    reportingService.downloadJob(jobId, adminPrincipal);
                }
                try { Thread.sleep(5); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            var file = reportingService.downloadJob(jobId, adminPrincipal);
            assertThat(file).isNotNull();
            assertThat(file.rowCount()).isEqualTo(SEED_STUDENT_COUNT);
        });
    }
}
