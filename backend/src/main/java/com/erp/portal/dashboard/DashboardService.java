package com.erp.portal.dashboard;

import com.erp.portal.security.SecurityPrincipal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {
    private final JdbcTemplate jdbc;

    public DashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public DashboardResponse load(SecurityPrincipal user) {
        var metrics = new ArrayList<DashboardResponse.Metric>();
        var roles = user.roles();
        boolean administrator = has(roles, "SUPER_ADMIN", "ADMIN");
        boolean management = has(roles, "MANAGEMENT");
        boolean accountant = has(roles, "ACCOUNTANT");
        boolean admission = has(roles, "RECEPTION_ADMISSION");
        boolean teacher = has(roles, "TEACHER");
        boolean student = has(roles, "STUDENT");
        boolean parent = has(roles, "PARENT_GUARDIAN");
        boolean staffAdmin = has(roles, "STAFF_ADMIN");
        boolean librarian = has(roles, "LIBRARIAN");
        boolean inventoryManager = has(roles, "INVENTORY_MANAGER");
        boolean supportAgent = has(roles, "SUPPORT_AGENT");

        if (permitted(user, "STUDENT:READ") && (administrator || management || accountant))
            metric(metrics, "students", "Students", "SELECT COUNT(*) FROM students WHERE status = 'ACTIVE'", "Active enrolments");
        if (permitted(user, "STAFF_ATTENDANCE:READ") && (administrator || management || staffAdmin))
            metric(metrics, "staff", "Staff", "SELECT COUNT(*) FROM staff WHERE status = 'ACTIVE'", "Active staff members");
        if (hasAny(user, "ATTENDANCE:READ", "ATTENDANCE:MARK", "ATTENDANCE:MANAGE") &&
                (administrator || management)) {
            metric(metrics, "attendance", "Attendance today",
                    "SELECT COUNT(*) FROM attendance WHERE attendance_date = ? AND status = 'PRESENT'",
                    "Present today", LocalDate.now());
        }
        boolean canViewFees = hasAny(user, "FEE:READ", "FEE:COLLECT", "FEE:MANAGE", "FINANCE_REPORT:READ");
        if ((administrator || management || accountant) && canViewFees) {
            BigDecimal outstanding = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(amount_due - amount_paid - discount_amount), 0) FROM fee_demands WHERE status IN ('PENDING','PARTIAL')",
                    BigDecimal.class);
            metrics.add(new DashboardResponse.Metric("outstandingFees", "Outstanding fees", money(outstanding), "Across pending demands"));
            if (hasAny(user, "FEE:READ", "FEE:COLLECT", "FINANCE_REPORT:READ")) {
                metric(metrics, "collections", "Collections this month",
                        "SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'POSTED' AND payment_date >= ?",
                        "Posted payments", LocalDate.now().withDayOfMonth(1));
                metric(metrics, "paymentsToday", "Payments today",
                        "SELECT COUNT(*) FROM payments WHERE status = 'POSTED' AND payment_date = ?",
                        "Posted transactions", LocalDate.now());
            }
            if (permitted(user, "FEE_DISCOUNT:APPROVE")) {
                metric(metrics, "discounts", "Discount requests",
                        "SELECT COUNT(*) FROM fee_discounts WHERE approval_status = 'PENDING'",
                        "Awaiting approval");
            }
        }
        if ((administrator || management || accountant) && hasAny(user, "EXPENSE:MANAGE", "EXPENSE:APPROVE")) {
            if (accountant || permitted(user, "EXPENSE:APPROVE")) {
                metric(metrics, "expenses", "Expenses this month",
                        "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE expense_date >= ? AND approval_status = 'APPROVED'",
                        "Approved expenses", LocalDate.now().withDayOfMonth(1));
            }
        }
        if (administrator || management) {
            var approvalParts = new ArrayList<String>();
            if (permitted(user, "ADMISSION:READ")) approvalParts.add("(SELECT COUNT(*) FROM admissions WHERE status = 'PENDING')");
            if (permitted(user, "FEE_DISCOUNT:APPROVE")) approvalParts.add("(SELECT COUNT(*) FROM fee_discounts WHERE approval_status = 'PENDING')");
            if (!approvalParts.isEmpty()) {
                metric(metrics, "pendingApprovals", "Pending approvals",
                        "SELECT " + String.join(" + ", approvalParts), "Application and discount queues");
            }
        }
        if (admission && permitted(user, "ADMISSION:READ")) {
            metric(metrics, "pendingApplicants", "Pending applicants", "SELECT COUNT(*) FROM admissions WHERE status = 'PENDING'", "Applications awaiting review");
            metric(metrics, "admissions", "Admissions", "SELECT COUNT(*) FROM admissions WHERE applied_on >= ?", "Applications this month", LocalDate.now().withDayOfMonth(1));
            metric(metrics, "documents", "Documents", "SELECT COUNT(*) FROM student_documents", "Uploaded applicant documents");
            metric(metrics, "applicants", "Applicants", "SELECT COUNT(*) FROM admissions", "All applications");
        }
        if (teacher && (permitted(user, "TIMETABLE:READ") || hasAny(user, "ATTENDANCE:READ", "ATTENDANCE:MARK"))) {
            Long staffId = jdbc.query("SELECT id FROM staff WHERE user_id = ?", rs -> rs.next() ? rs.getLong(1) : null, user.userId());
            if (staffId != null) {
                if (permitted(user, "TIMETABLE:READ"))
                    metric(metrics, "assignedClasses", "Assigned classes", "SELECT COUNT(DISTINCT section_id) FROM teacher_assignments WHERE staff_id = ? AND status = 'ACTIVE'", "Active teaching assignments", staffId);
                if (hasAny(user, "ATTENDANCE:READ", "ATTENDANCE:MARK")) metric(metrics, "attendance", "Attendance today",
                        "SELECT COUNT(*) FROM attendance a JOIN teacher_assignments ta ON ta.section_id = a.section_id " +
                                "AND ta.session_id = a.session_id WHERE ta.staff_id = ? AND ta.status = 'ACTIVE' " +
                                "AND a.attendance_date = ? AND a.status = 'PRESENT'",
                        "Present in your assigned classes", staffId, LocalDate.now());
                if (permitted(user, "HOMEWORK:READ") || permitted(user, "HOMEWORK:CREATE"))
                    metric(metrics, "homework", "Homework", "SELECT COUNT(*) FROM homework h JOIN staff s ON s.id = h.assigned_by WHERE s.user_id = ? AND h.due_date >= ?", "Upcoming assignments", user.userId(), LocalDate.now());
                if (hasAny(user, "SYLLABUS:READ", "SYLLABUS:UPDATE"))
                    metric(metrics, "syllabus", "Syllabus topics", "SELECT COUNT(*) FROM syllabus_progress sp JOIN staff s ON s.id = sp.recorded_by WHERE s.user_id = ? AND sp.status <> 'COMPLETED'", "Topics in progress", user.userId());
            }
        }
        if (student && permitted(user, "STUDENT:READ")) {
            Long studentId = jdbc.query("SELECT id FROM students WHERE user_id = ?", rs -> rs.next() ? rs.getLong(1) : null, user.userId());
            if (studentId != null) {
                if (hasAny(user, "ATTENDANCE:READ", "ATTENDANCE:MARK")) metric(metrics, "ownAttendance", "Attendance records", "SELECT COUNT(*) FROM attendance WHERE student_id = ? AND status = 'PRESENT'", "Present days", studentId);
                if (permitted(user, "FEE:READ")) metric(metrics, "ownFees", "Outstanding fees", "SELECT COALESCE(SUM(amount_due - amount_paid - discount_amount), 0) FROM fee_demands WHERE student_id = ? AND status IN ('PENDING','PARTIAL')", "Your pending demands", studentId);
                if (permitted(user, "TIMETABLE:READ")) metric(metrics, "ownTimetable", "Classes today",
                        "SELECT COUNT(*) FROM timetable_slots ts JOIN student_enrollments se ON se.section_id = ts.section_id " +
                                "JOIN academic_sessions ac ON ac.id = se.session_id AND ac.is_active = 1 " +
                                "WHERE se.student_id = ? AND ts.day_of_week = ?",
                        "From your active class timetable", studentId, LocalDate.now().getDayOfWeek().getValue());
                if (permitted(user, "HOMEWORK:READ")) metric(metrics, "ownHomework", "Upcoming homework",
                        "SELECT COUNT(*) FROM homework h JOIN student_enrollments se ON se.section_id = h.section_id " +
                                "JOIN academic_sessions ac ON ac.id = se.session_id AND ac.is_active = 1 " +
                                "WHERE se.student_id = ? AND h.due_date >= ?",
                        "Due today or later", studentId, LocalDate.now());
                if (hasAny(user, "SYLLABUS:READ", "ACADEMIC:READ")) metric(metrics, "ownAcademics", "Syllabus topics", 
                        "SELECT COUNT(*) FROM syllabus_progress sp JOIN student_enrollments se ON se.section_id = sp.section_id " +
                                "JOIN academic_sessions ac ON ac.id = se.session_id AND ac.is_active = 1 " +
                                "WHERE se.student_id = ? AND sp.status <> 'COMPLETED'",
                        "Topics in progress", studentId);
            }
        }
        if (parent && permitted(user, "STUDENT:READ")) {
            metric(metrics, "linkedChildren", "Linked children", "SELECT COUNT(DISTINCT sg.student_id) FROM student_guardians sg JOIN guardians g ON g.id=sg.guardian_id WHERE g.user_id=?", "Children linked to your account", user.userId());
            if (hasAny(user, "ATTENDANCE:READ", "ATTENDANCE:MARK"))
                metric(metrics, "linkedAttendance", "Attendance records", "SELECT COUNT(*) FROM attendance a JOIN student_guardians sg ON sg.student_id=a.student_id JOIN guardians g ON g.id=sg.guardian_id WHERE g.user_id=? AND a.status='PRESENT'", "Present days for linked children", user.userId());
            if (permitted(user, "FEE:READ"))
                metric(metrics, "linkedFees", "Outstanding fees", "SELECT COALESCE(SUM(fd.amount_due-fd.amount_paid-fd.discount_amount),0) FROM fee_demands fd JOIN student_guardians sg ON sg.student_id=fd.student_id JOIN guardians g ON g.id=sg.guardian_id WHERE g.user_id=? AND fd.status IN ('PENDING','PARTIAL')", "Pending demands for linked children", user.userId());
        }
        if (staffAdmin && permitted(user, "STAFF_ATTENDANCE:READ"))
            metric(metrics, "staffAttendance", "Staff attendance today", "SELECT COUNT(*) FROM staff_attendance WHERE attendance_date=? AND status='PRESENT'", "Present staff members", LocalDate.now());
        if (librarian && permitted(user, "LIBRARY:READ")) {
            metric(metrics, "libraryCatalog", "Library titles", "SELECT COUNT(*) FROM library_items", "Available catalog titles");
            metric(metrics, "libraryIssues", "Open issues", "SELECT COUNT(*) FROM library_issues WHERE status='ISSUED'", "Items currently issued");
        }
        if (inventoryManager && permitted(user, "INVENTORY:READ")) {
            metric(metrics, "inventoryItems", "Inventory items", "SELECT COUNT(*) FROM inventory_items", "Catalogued stock items");
            metric(metrics, "lowStock", "Low stock items", "SELECT COUNT(*) FROM inventory_items WHERE current_stock <= min_stock_level", "At or below minimum level");
        }
        if (supportAgent && permitted(user, "SUPPORT:READ"))
            metric(metrics, "assignedTickets", "Assigned tickets", "SELECT COUNT(*) FROM support_tickets WHERE assigned_to=? AND status NOT IN ('RESOLVED','CLOSED')", "Open tickets assigned to you", user.userId());
        var activity = (administrator || management) && permitted(user, "AUDIT:READ") ? jdbc.query(
                "SELECT action, entity_type, COALESCE(actor_username, 'System') actor, created_at " +
                        "FROM audit_logs ORDER BY created_at DESC LIMIT 6",
                (rs, row) -> new DashboardResponse.Activity(rs.getString("action"), rs.getString("entity_type"),
                        rs.getString("actor"), rs.getTimestamp("created_at").toInstant().toString())) : List.<DashboardResponse.Activity>of();
        return new DashboardResponse(List.copyOf(metrics), activity);
    }

    private static boolean has(List<String> roles, String... expected) {
        for (String role : expected) if (roles.contains(role)) return true;
        return false;
    }

    private static boolean permitted(SecurityPrincipal user, String permission) {
        return user.permissions().contains(permission);
    }

    private static boolean hasAny(SecurityPrincipal user, String... permissions) {
        for (String permission : permissions) if (permitted(user, permission)) return true;
        return false;
    }

    private void metric(List<DashboardResponse.Metric> metrics, String key, String label,
                        String sql, String detail, Object... args) {
        Object value = jdbc.queryForObject(sql, Object.class, args);
        String rendered = value instanceof BigDecimal decimal ? money(decimal) : String.valueOf(value);
        metrics.add(new DashboardResponse.Metric(key, label, rendered, detail));
    }

    private static String money(BigDecimal value) { return value == null ? "0.00" : value.toPlainString(); }
}
