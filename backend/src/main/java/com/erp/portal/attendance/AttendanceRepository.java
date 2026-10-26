package com.erp.portal.attendance;

import com.erp.portal.attendance.AttendanceDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class AttendanceRepository {
    private final JdbcTemplate jdbc;
    public AttendanceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean exists(String table, long id) {
        if (!List.of("students", "staff", "classes", "sections", "academic_sessions", "attendance", "staff_attendance").contains(table))
            throw new IllegalArgumentException("Unsupported table");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, id);
        return count != null && count > 0;
    }

    public boolean activeStudent(long id) {
        return count("SELECT COUNT(*) FROM students WHERE id = ? AND status = 'ACTIVE'", id) > 0;
    }

    public boolean activeStaff(long id) {
        return count("SELECT COUNT(*) FROM staff WHERE id = ? AND status = 'ACTIVE'", id) > 0;
    }

    public Long staffForUser(long userId) {
        return jdbc.query("SELECT id FROM staff WHERE user_id = ? AND status = 'ACTIVE' LIMIT 1",
                r -> r.next() ? r.getLong(1) : null, userId);
    }

    public boolean validClassSection(long classId, long sectionId) {
        return count("SELECT COUNT(*) FROM sections WHERE id = ? AND class_id = ?", sectionId, classId) > 0;
    }

    public boolean enrolled(long sessionId, long studentId, long classId, long sectionId) {
        return count("SELECT COUNT(*) FROM student_enrollments WHERE session_id = ? AND student_id = ? AND class_id = ? AND section_id = ?",
                sessionId, studentId, classId, sectionId) > 0;
    }

    public boolean enrollmentExists(long sessionId, long studentId) {
        return count("SELECT COUNT(*) FROM student_enrollments WHERE session_id = ? AND student_id = ?", sessionId, studentId) > 0;
    }

    public int createEnrollment(EnrollmentRequest x) {
        return jdbc.update("INSERT INTO student_enrollments(student_id,session_id,class_id,section_id,roll_no) VALUES(?,?,?,?,?)",
                x.studentId(), x.sessionId(), x.classId(), x.sectionId(), blankToNull(x.rollNo()));
    }

    public List<EligibleStudent> eligibleStudents(long sessionId, String search) {
        String term = search == null ? null : "%" + search.trim() + "%";
        return jdbc.query("SELECT s.id,s.admission_no,CONCAT_WS(' ',s.first_name,s.last_name) name FROM students s WHERE s.status='ACTIVE' AND (? IS NULL OR s.admission_no LIKE ? OR s.first_name LIKE ? OR s.last_name LIKE ?) AND NOT EXISTS(SELECT 1 FROM student_enrollments e WHERE e.student_id=s.id AND e.session_id=?) ORDER BY s.first_name,s.last_name,s.admission_no LIMIT 100",
                (r, n) -> new EligibleStudent(r.getLong("id"), r.getString("admission_no"), r.getString("name")), term, term, term, term, sessionId);
    }

    public List<Enrollment> enrollments(long sessionId, Long classId, Long sectionId) {
        StringBuilder sql = new StringBuilder("SELECT e.id,e.session_id,e.student_id,s.admission_no,CONCAT_WS(' ',s.first_name,s.last_name) student_name,e.class_id,c.name class_name,e.section_id,sec.name section_name,e.roll_no,e.enrolled_at FROM student_enrollments e JOIN students s ON s.id=e.student_id JOIN classes c ON c.id=e.class_id JOIN sections sec ON sec.id=e.section_id WHERE e.session_id=?");
        List<Object> args = new ArrayList<>();
        args.add(sessionId);
        if (classId != null) { sql.append(" AND e.class_id=?"); args.add(classId); }
        if (sectionId != null) { sql.append(" AND e.section_id=?"); args.add(sectionId); }
        sql.append(" ORDER BY c.order_seq,sec.name,s.last_name,s.first_name");
        return jdbc.query(sql.toString(),
                (r, n) -> new Enrollment(r.getLong("id"), r.getLong("session_id"), r.getLong("student_id"), r.getString("admission_no"), r.getString("student_name"), r.getLong("class_id"), r.getString("class_name"), r.getLong("section_id"), r.getString("section_name"), r.getString("roll_no"), dateTime(r, "enrolled_at")), args.toArray());
    }

    public boolean sessionContainsDate(long sessionId, LocalDate date) {
        return count("SELECT COUNT(*) FROM academic_sessions WHERE id=? AND start_date<=? AND end_date>=?", sessionId, date, date) > 0;
    }

    public List<Lookup> sessionOptions() {
        return jdbc.query("SELECT id,name FROM academic_sessions ORDER BY start_date DESC,id DESC",
                (r, n) -> new Lookup(r.getLong(1), r.getString(2)));
    }

    public List<Lookup> classOptions() {
        return jdbc.query("SELECT id,name FROM classes ORDER BY order_seq,name",
                (r, n) -> new Lookup(r.getLong(1), r.getString(2)));
    }

    public List<SectionLookup> sectionOptions(Long classId) {
        StringBuilder sql = new StringBuilder("SELECT sec.id,sec.class_id,CONCAT(c.name,' · ',sec.name) name FROM sections sec JOIN classes c ON c.id=sec.class_id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (classId != null) { sql.append(" AND sec.class_id=?"); args.add(classId); }
        sql.append(" ORDER BY c.order_seq,sec.name");
        return jdbc.query(sql.toString(),
                (r, n) -> new SectionLookup(r.getLong(1), r.getLong(2), r.getString(3)), args.toArray());
    }

    public List<Lookup> staffOptions() {
        return jdbc.query("SELECT id,CONCAT_WS(' ',first_name,last_name) name FROM staff WHERE status='ACTIVE' ORDER BY first_name,last_name",
                (r, n) -> new Lookup(r.getLong(1), r.getString(2)));
    }

    public record Lookup(long id, String name) { }
    public record SectionLookup(long id, long classId, String name) { }

    public boolean studentLocked(long session, LocalDate date) {
        return count("SELECT COUNT(*) FROM attendance_date_locks WHERE lock_scope='STUDENT' AND session_id=? AND attendance_date=?", session, date) > 0;
    }

    public boolean staffLocked(LocalDate date) {
        return count("SELECT COUNT(*) FROM attendance_date_locks WHERE lock_scope='STAFF' AND attendance_date=?", date) > 0;
    }

    public List<DateLock> locks(LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("SELECT l.id,l.lock_scope,l.session_id,l.attendance_date,l.reason,l.locked_by,COALESCE(u.full_name,u.username) locked_by_name,l.locked_at FROM attendance_date_locks l JOIN users u ON u.id=l.locked_by WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (from != null) { sql.append(" AND l.attendance_date>=?"); args.add(from); }
        if (to != null) { sql.append(" AND l.attendance_date<=?"); args.add(to); }
        sql.append(" ORDER BY l.attendance_date DESC,l.id DESC");
        return jdbc.query(sql.toString(),
                (r, n) -> new DateLock(r.getLong("id"), r.getString("lock_scope"), nullableLong(r, "session_id"), date(r, "attendance_date"), r.getString("reason"), r.getLong("locked_by"), r.getString("locked_by_name"), dateTime(r, "locked_at")), args.toArray());
    }

    public long createLock(DateLockRequest x, long actor) {
        String key = "STUDENT".equals(x.scope()) ? "STUDENT:" + x.sessionId() + ":" + x.attendanceDate() : "STAFF:" + x.attendanceDate();
        return insert("INSERT INTO attendance_date_locks(lock_scope,session_id,attendance_date,reason,locked_by,lock_key) VALUES(?,?,?,?,?,?)",
                x.scope(), "STUDENT".equals(x.scope()) ? x.sessionId() : null, x.attendanceDate(), x.reason().trim(), actor, key);
    }

    public int removeLock(long id) {
        return jdbc.update("DELETE FROM attendance_date_locks WHERE id=?", id);
    }

    public Optional<DateLock> lock(long id) {
        return locks(null, null).stream().filter(x -> x.id() == id).findFirst();
    }

    public List<RosterStudent> roster(long session, long section, LocalDate date) {
        return jdbc.query("SELECT s.id student_id,s.admission_no,CONCAT_WS(' ',s.first_name,s.last_name) student_name,a.status,a.remarks,a.id attendance_id,a.is_corrected FROM student_enrollments e JOIN students s ON s.id=e.student_id LEFT JOIN attendance a ON a.student_id=e.student_id AND a.session_id=e.session_id AND a.attendance_date=? WHERE e.session_id=? AND e.section_id=? AND s.status='ACTIVE' ORDER BY e.roll_no,s.last_name,s.first_name,s.admission_no",
                (r, n) -> new RosterStudent(r.getLong("student_id"), r.getString("admission_no"), r.getString("student_name"), r.getString("status"), r.getString("remarks"), nullableLong(r, "attendance_id"), r.getBoolean("is_corrected")), date, session, section);
    }

    public Optional<StudentAttendance> studentAttendance(long id) {
        return jdbc.query(studentSelect() + " WHERE a.id=?", (r, n) -> studentAttendanceRow(r), id).stream().findFirst();
    }

    public StudentMarkResult markStudent(long session, long classId, long section, LocalDate date, long student, String status, String remarks, long actor, Long markerStaff, String key) {
        Optional<StudentAttendance> byKey = studentByKey(key);
        if (byKey.isPresent()) {
            StudentAttendance old = byKey.get();
            if (old.studentId() != student || old.sessionId() != session || old.sectionId() != section || !old.attendanceDate().equals(date) || !old.status().equals(status) || !Objects.equals(old.remarks(), blankToNull(remarks)))
                throw AttendanceException.conflict("Idempotency-Key was already used for a different attendance value");
            return new StudentMarkResult(old, false);
        }
        Optional<StudentAttendance> natural = studentByNatural(session, student, date);
        if (natural.isPresent()) {
            StudentAttendance old = natural.get();
            if (old.sectionId() != section || old.classId() != classId || !old.status().equals(status) || !Objects.equals(old.remarks(), blankToNull(remarks)))
                throw AttendanceException.conflict("Attendance already exists for this student and date; use the correction workflow");
            return new StudentMarkResult(old, false);
        }
        jdbc.update("INSERT INTO attendance(student_id,session_id,class_id,section_id,attendance_date,status,marked_by,marked_by_user_id,remarks,idempotency_key) VALUES(?,?,?,?,?,?,?,?,?,?)",
                student, session, classId, section, date, status, markerStaff, actor, blankToNull(remarks), key);
        return new StudentMarkResult(studentByNatural(session, student, date).orElseThrow(), true);
    }

    public record StudentMarkResult(StudentAttendance record, boolean created) { }

    private Optional<StudentAttendance> studentByKey(String key) {
        return attendanceByKey(key).stream().findFirst();
    }

    private Optional<StudentAttendance> studentByNatural(long session, long student, LocalDate date) {
        return jdbc.query(studentSelect() + " WHERE a.session_id=? AND a.student_id=? AND a.attendance_date=?", (r, n) -> studentAttendanceRow(r), session, student, date).stream().findFirst();
    }

    public List<StudentAttendance> studentRecords(LocalDate from, LocalDate to, Long session, Long classId, Long sectionId, Long student, LocalDate exactDate, String status,
                                                   Long studentUser, Long guardianUser, Long teacherStaff) {
        StringBuilder sql = new StringBuilder(studentSelect()).append(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (from != null) { sql.append(" AND a.attendance_date>=?"); args.add(from); }
        if (to != null) { sql.append(" AND a.attendance_date<=?"); args.add(to); }
        if (exactDate != null) { sql.append(" AND a.attendance_date=?"); args.add(exactDate); }
        if (session != null) { sql.append(" AND a.session_id=?"); args.add(session); }
        if (classId != null) { sql.append(" AND a.class_id=?"); args.add(classId); }
        if (sectionId != null) { sql.append(" AND a.section_id=?"); args.add(sectionId); }
        if (student != null) { sql.append(" AND a.student_id=?"); args.add(student); }
        if (status != null && !status.isBlank()) { sql.append(" AND a.status=?"); args.add(status.trim()); }
        if (studentUser != null) { sql.append(" AND s.user_id=?"); args.add(studentUser); }
        if (guardianUser != null) { sql.append(" AND EXISTS (SELECT 1 FROM student_guardians sg JOIN guardians g ON g.id=sg.guardian_id WHERE sg.student_id=s.id AND g.user_id=?)"); args.add(guardianUser); }
        if (teacherStaff != null) { sql.append(" AND EXISTS (SELECT 1 FROM teacher_assignments ta WHERE ta.session_id=a.session_id AND ta.section_id=a.section_id AND ta.staff_id=? AND ta.status='ACTIVE')"); args.add(teacherStaff); }
        sql.append(" ORDER BY a.attendance_date DESC,a.id DESC LIMIT 2000");
        return jdbc.query(sql.toString(), (r, n) -> studentAttendanceRow(r), args.toArray());
    }

    public List<StudentAttendance> attendanceByKey(String key) {
        return jdbc.query(studentSelect() + " WHERE a.idempotency_key=?", (r, n) -> studentAttendanceRow(r), key);
    }

    private String studentSelect() {
        return "SELECT a.id,a.student_id,s.admission_no,CONCAT_WS(' ',s.first_name,s.last_name) student_name,a.session_id,a.class_id,c.name class_name,a.section_id,sec.name section_name,a.attendance_date,a.status,a.marked_by,a.marked_by_user_id,COALESCE(mu.full_name,mu.username,CONCAT_WS(' ',st.first_name,st.last_name),'Staff') marker_name,COALESCE(a.marked_by_user_id,mu.id,st.user_id,0) marker_user,a.marked_at,a.remarks,a.is_corrected FROM attendance a JOIN students s ON s.id=a.student_id JOIN classes c ON c.id=a.class_id JOIN sections sec ON sec.id=a.section_id LEFT JOIN staff st ON st.id=a.marked_by LEFT JOIN users mu ON mu.id=a.marked_by_user_id";
    }

    private StudentAttendance studentAttendanceRow(java.sql.ResultSet r) throws java.sql.SQLException {
        return new StudentAttendance(
                r.getLong("id"),
                r.getLong("student_id"),
                r.getString("admission_no"),
                r.getString("student_name"),
                r.getLong("session_id"),
                r.getLong("class_id"),
                r.getString("class_name"),
                r.getLong("section_id"),
                r.getString("section_name"),
                date(r, "attendance_date"),
                r.getString("status"),
                r.getLong("marker_user"),
                r.getString("marker_name"),
                dateTime(r, "marked_at"),
                r.getString("remarks"),
                r.getBoolean("is_corrected")
        );
    }

    public String studentStatus(long id) {
        return jdbc.query("SELECT status FROM attendance WHERE id=?", r -> r.next() ? r.getString(1) : null, id);
    }

    public int correctStudent(long id, String status, long actor) {
        return jdbc.update("UPDATE attendance SET status=?,is_corrected=1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?", status, id);
    }

    public void addStudentCorrection(long id, String old, String next, String reason, long actor) {
        jdbc.update("INSERT INTO attendance_corrections(attendance_id,old_status,new_status,reason,corrected_by) VALUES(?,?,?,?,?)",
                id, old, next, reason.trim(), actor);
    }

    public List<AttendanceCorrection> studentCorrectionHistory(long id) {
        return jdbc.query("SELECT c.id,c.attendance_id,c.old_status,c.new_status,c.reason,c.corrected_by,COALESCE(u.full_name,u.username) actor_name,c.corrected_at FROM attendance_corrections c JOIN users u ON u.id=c.corrected_by WHERE c.attendance_id=? ORDER BY c.corrected_at DESC,c.id DESC",
                (r, n) -> new AttendanceCorrection(r.getLong("id"), r.getLong("attendance_id"), r.getString("old_status"), r.getString("new_status"), r.getString("reason"), r.getLong("corrected_by"), r.getString("actor_name"), dateTime(r, "corrected_at")), id);
    }

    public StaffMarkResult markStaff(LocalDate date, long staff, String status, String remarks, long actor, String key) {
        Optional<StaffAttendance> byKey = staffByKey(key);
        if (byKey.isPresent()) {
            StaffAttendance old = byKey.get();
            if (old.staffId() != staff || !old.attendanceDate().equals(date) || !old.status().equals(status) || !Objects.equals(old.remarks(), blankToNull(remarks)))
                throw AttendanceException.conflict("Idempotency-Key was already used for a different attendance value");
            return new StaffMarkResult(old, false);
        }
        Optional<StaffAttendance> natural = staffByNatural(staff, date);
        if (natural.isPresent()) {
            StaffAttendance old = natural.get();
            if (!old.status().equals(status) || !Objects.equals(old.remarks(), blankToNull(remarks)))
                throw AttendanceException.conflict("Staff attendance already exists for this date; use the correction workflow");
            return new StaffMarkResult(old, false);
        }
        jdbc.update("INSERT INTO staff_attendance(staff_id,attendance_date,status,marked_by,remarks,idempotency_key) VALUES(?,?,?,?,?,?)",
                staff, date, status, actor, blankToNull(remarks), key);
        return new StaffMarkResult(staffByNatural(staff, date).orElseThrow(), true);
    }

    public record StaffMarkResult(StaffAttendance record, boolean created) { }

    public List<StaffAttendance> staffRecords(LocalDate from, LocalDate to, Long staffId, Long userScope, Long markerScope, String status) {
        StringBuilder sql = new StringBuilder(staffSelect()).append(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (from != null) { sql.append(" AND a.attendance_date>=?"); args.add(from); }
        if (to != null) { sql.append(" AND a.attendance_date<=?"); args.add(to); }
        if (staffId != null) { sql.append(" AND a.staff_id=?"); args.add(staffId); }
        if (status != null && !status.isBlank()) { sql.append(" AND a.status=?"); args.add(status.trim()); }
        if (userScope != null) { sql.append(" AND st.user_id=?"); args.add(userScope); }
        if (markerScope != null) { sql.append(" AND a.marked_by=?"); args.add(markerScope); }
        sql.append(" ORDER BY a.attendance_date DESC,a.id DESC LIMIT 2000");
        return jdbc.query(sql.toString(), (r, n) -> staffRow(r), args.toArray());
    }

    public Optional<StaffAttendance> staffAttendance(long id) {
        return jdbc.query(staffSelect() + " WHERE a.id=?", (r, n) -> staffRow(r), id).stream().findFirst();
    }

    private Optional<StaffAttendance> staffByNatural(long staff, LocalDate date) {
        return jdbc.query(staffSelect() + " WHERE a.staff_id=? AND a.attendance_date=?", (r, n) -> staffRow(r), staff, date).stream().findFirst();
    }

    private Optional<StaffAttendance> staffByKey(String key) {
        return jdbc.query(staffSelect() + " WHERE a.idempotency_key=?", (r, n) -> staffRow(r), key).stream().findFirst();
    }

    private String staffSelect() {
        return "SELECT a.id,a.staff_id,st.employee_no,CONCAT_WS(' ',st.first_name,st.last_name) staff_name,a.attendance_date,a.status,a.marked_by,COALESCE(u.full_name,u.username,'Admin') marker_name,a.created_at marked_at,a.remarks,EXISTS(SELECT 1 FROM staff_attendance_corrections c WHERE c.staff_attendance_id=a.id) corrected FROM staff_attendance a JOIN staff st ON st.id=a.staff_id LEFT JOIN users u ON u.id=a.marked_by";
    }

    private StaffAttendance staffRow(java.sql.ResultSet r) throws java.sql.SQLException {
        return new StaffAttendance(
                r.getLong("id"),
                r.getLong("staff_id"),
                r.getString("employee_no"),
                r.getString("staff_name"),
                date(r, "attendance_date"),
                r.getString("status"),
                nullableLong(r, "marked_by"),
                r.getString("marker_name"),
                dateTime(r, "marked_at"),
                r.getString("remarks"),
                r.getBoolean("corrected")
        );
    }

    public String staffStatus(long id) {
        return jdbc.query("SELECT status FROM staff_attendance WHERE id=?", r -> r.next() ? r.getString(1) : null, id);
    }

    public int correctStaff(long id, String status) {
        return jdbc.update("UPDATE staff_attendance SET status=? WHERE id=?", status, id);
    }

    public void addStaffCorrection(long id, String old, String next, String reason, long actor) {
        jdbc.update("INSERT INTO staff_attendance_corrections(staff_attendance_id,old_status,new_status,reason,corrected_by) VALUES(?,?,?,?,?)",
                id, old, next, reason.trim(), actor);
    }

    public List<StaffAttendanceCorrection> staffCorrectionHistory(long id) {
        return jdbc.query("SELECT c.id,c.staff_attendance_id,c.old_status,c.new_status,c.reason,c.corrected_by,COALESCE(u.full_name,u.username) actor_name,c.corrected_at FROM staff_attendance_corrections c JOIN users u ON u.id=c.corrected_by WHERE c.staff_attendance_id=? ORDER BY c.corrected_at DESC,c.id DESC",
                (r, n) -> new StaffAttendanceCorrection(r.getLong("id"), r.getLong("staff_attendance_id"), r.getString("old_status"), r.getString("new_status"), r.getString("reason"), r.getLong("corrected_by"), r.getString("actor_name"), dateTime(r, "corrected_at")), id);
    }

    private long insert(String sql, Object... values) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> {
            PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) ps.setObject(i + 1, values[i]);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    private int count(String sql, Object... args) {
        Integer count = jdbc.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static LocalDate date(java.sql.ResultSet r, String col) throws java.sql.SQLException {
        java.sql.Date d = r.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    private static LocalDateTime dateTime(java.sql.ResultSet r, String col) throws java.sql.SQLException {
        Timestamp t = r.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }

    private static Long nullableLong(java.sql.ResultSet r, String col) throws java.sql.SQLException {
        long x = r.getLong(col);
        return r.wasNull() ? null : x;
    }
}
