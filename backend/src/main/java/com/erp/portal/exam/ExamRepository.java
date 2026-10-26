package com.erp.portal.exam;

import com.erp.portal.exam.ExamDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Repository
public class ExamRepository {
    private final JdbcTemplate jdbc;

    public ExamRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // =========================================================================
    // EXAMINATIONS
    // =========================================================================

    public List<ExamSummaryResponse> listExams(Long sessionId, Long classId, String examType, String status, String search) {
        StringBuilder sql = new StringBuilder(
                "SELECT e.id, e.name, e.exam_type, e.session_id, s.code AS session_code, " +
                "e.class_id, c.name AS class_name, e.section_id, sec.name AS section_name, " +
                "e.start_date, e.end_date, e.status, e.grading_scheme_id, e.published_at, e.published_by, e.created_at " +
                "FROM examinations e " +
                "JOIN academic_sessions s ON s.id = e.session_id " +
                "JOIN classes c ON c.id = e.class_id " +
                "LEFT JOIN sections sec ON sec.id = e.section_id " +
                "WHERE 1=1 ");
        List<Object> args = new ArrayList<>();

        if (sessionId != null) { sql.append("AND e.session_id = ? "); args.add(sessionId); }
        if (classId != null) { sql.append("AND e.class_id = ? "); args.add(classId); }
        if (examType != null && !examType.isBlank()) { sql.append("AND e.exam_type = ? "); args.add(examType.trim()); }
        if (status != null && !status.isBlank()) { sql.append("AND e.status = ? "); args.add(status.trim()); }
        if (search != null && !search.isBlank()) {
            sql.append("AND (LOWER(e.name) LIKE ? OR LOWER(c.name) LIKE ?) ");
            String term = "%" + search.trim().toLowerCase() + "%";
            args.add(term); args.add(term);
        }

        sql.append("ORDER BY e.start_date DESC, e.id DESC");
        return jdbc.query(sql.toString(), (rs, i) -> new ExamSummaryResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("exam_type"),
                rs.getLong("session_id"),
                rs.getString("session_code"),
                rs.getLong("class_id"),
                rs.getString("class_name"),
                nullableLong(rs, "section_id"),
                rs.getString("section_name"),
                date(rs, "start_date"),
                date(rs, "end_date"),
                rs.getString("status"),
                nullableLong(rs, "grading_scheme_id"),
                dateTime(rs, "published_at"),
                nullableLong(rs, "published_by"),
                dateTime(rs, "created_at")
        ), args.toArray());
    }

    public Optional<ExamSummaryResponse> findExamSummary(long id) {
        String sql = "SELECT e.id, e.name, e.exam_type, e.session_id, s.code AS session_code, " +
                "e.class_id, c.name AS class_name, e.section_id, sec.name AS section_name, " +
                "e.start_date, e.end_date, e.status, e.grading_scheme_id, e.published_at, e.published_by, e.created_at " +
                "FROM examinations e " +
                "JOIN academic_sessions s ON s.id = e.session_id " +
                "JOIN classes c ON c.id = e.class_id " +
                "LEFT JOIN sections sec ON sec.id = e.section_id " +
                "WHERE e.id = ?";
        List<ExamSummaryResponse> results = jdbc.query(sql, (rs, i) -> new ExamSummaryResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("exam_type"),
                rs.getLong("session_id"),
                rs.getString("session_code"),
                rs.getLong("class_id"),
                rs.getString("class_name"),
                nullableLong(rs, "section_id"),
                rs.getString("section_name"),
                date(rs, "start_date"),
                date(rs, "end_date"),
                rs.getString("status"),
                nullableLong(rs, "grading_scheme_id"),
                dateTime(rs, "published_at"),
                nullableLong(rs, "published_by"),
                dateTime(rs, "created_at")
        ), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<ExamDetailResponse> findExamDetail(long id) {
        String sql = "SELECT e.id, e.name, e.exam_type, e.session_id, s.code AS session_code, " +
                "e.class_id, c.name AS class_name, e.section_id, sec.name AS section_name, " +
                "e.start_date, e.end_date, e.status, e.grading_scheme_id, gs.name AS grading_scheme_name, " +
                "e.instructions, e.published_at, uPub.full_name AS published_by_name, " +
                "uCre.full_name AS created_by_name, e.created_at " +
                "FROM examinations e " +
                "JOIN academic_sessions s ON s.id = e.session_id " +
                "JOIN classes c ON c.id = e.class_id " +
                "LEFT JOIN sections sec ON sec.id = e.section_id " +
                "LEFT JOIN grading_schemes gs ON gs.id = e.grading_scheme_id " +
                "LEFT JOIN users uPub ON uPub.id = e.published_by " +
                "JOIN users uCre ON uCre.id = e.created_by " +
                "WHERE e.id = ?";
        List<ExamDetailResponse> results = jdbc.query(sql, (rs, i) -> new ExamDetailResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("exam_type"),
                rs.getLong("session_id"),
                rs.getString("session_code"),
                rs.getLong("class_id"),
                rs.getString("class_name"),
                nullableLong(rs, "section_id"),
                rs.getString("section_name"),
                date(rs, "start_date"),
                date(rs, "end_date"),
                rs.getString("status"),
                nullableLong(rs, "grading_scheme_id"),
                rs.getString("grading_scheme_name"),
                rs.getString("instructions"),
                dateTime(rs, "published_at"),
                rs.getString("published_by_name"),
                rs.getString("created_by_name"),
                dateTime(rs, "created_at"),
                listSchedules(id),
                listSubmissions(id, null, null)
        ), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public long createExam(CreateExamRequest req, long createdBy) {
        String sql = "INSERT INTO examinations (name, exam_type, session_id, class_id, section_id, start_date, end_date, status, grading_scheme_id, instructions, created_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 'PLANNED', ?, ?, ?)";
        return insert(sql, req.name().trim(), req.examType().trim().toUpperCase(), req.sessionId(), req.classId(),
                req.sectionId(), req.startDate(), req.endDate(), req.gradingSchemeId() != null ? req.gradingSchemeId() : 1L,
                req.instructions(), createdBy);
    }

    public int updateExam(long id, UpdateExamRequest req) {
        String sql = "UPDATE examinations SET name = ?, exam_type = ?, session_id = ?, class_id = ?, section_id = ?, " +
                "start_date = ?, end_date = ?, status = ?, grading_scheme_id = ?, instructions = ? WHERE id = ?";
        return jdbc.update(sql, req.name().trim(), req.examType().trim().toUpperCase(), req.sessionId(), req.classId(),
                req.sectionId(), req.startDate(), req.endDate(), req.status().trim().toUpperCase(),
                req.gradingSchemeId() != null ? req.gradingSchemeId() : 1L, req.instructions(), id);
    }

    public int deleteExam(long id) {
        return jdbc.update("DELETE FROM examinations WHERE id = ?", id);
    }

    public boolean examHasDependentMarksOrResults(long examId) {
        Integer marksCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_exam_marks WHERE exam_id = ?", Integer.class, examId);
        Integer resultsCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_exam_results WHERE exam_id = ?", Integer.class, examId);
        return (marksCount != null && marksCount > 0) || (resultsCount != null && resultsCount > 0);
    }

    // =========================================================================
    // SCHEDULES
    // =========================================================================

    public List<ScheduleResponse> listSchedules(long examId) {
        String sql = "SELECT es.id, es.exam_id, es.class_id, c.name AS class_name, " +
                "es.section_id, sec.name AS section_name, es.subject_id, sub.code AS subject_code, " +
                "sub.name AS subject_name, es.exam_date, es.start_time, es.end_time, es.max_marks, es.pass_marks, es.room_no " +
                "FROM exam_schedules es " +
                "JOIN classes c ON c.id = es.class_id " +
                "JOIN sections sec ON sec.id = es.section_id " +
                "JOIN subjects sub ON sub.id = es.subject_id " +
                "WHERE es.exam_id = ? " +
                "ORDER BY es.exam_date ASC, es.start_time ASC, c.order_seq, sec.name, sub.name";
        return jdbc.query(sql, (rs, i) -> new ScheduleResponse(
                rs.getLong("id"),
                rs.getLong("exam_id"),
                rs.getLong("class_id"),
                rs.getString("class_name"),
                rs.getLong("section_id"),
                rs.getString("section_name"),
                rs.getLong("subject_id"),
                rs.getString("subject_code"),
                rs.getString("subject_name"),
                date(rs, "exam_date"),
                time(rs, "start_time"),
                time(rs, "end_time"),
                rs.getBigDecimal("max_marks"),
                rs.getBigDecimal("pass_marks"),
                rs.getString("room_no")
        ), examId);
    }

    public Optional<ScheduleResponse> findSchedule(long scheduleId) {
        String sql = "SELECT es.id, es.exam_id, es.class_id, c.name AS class_name, " +
                "es.section_id, sec.name AS section_name, es.subject_id, sub.code AS subject_code, " +
                "sub.name AS subject_name, es.exam_date, es.start_time, es.end_time, es.max_marks, es.pass_marks, es.room_no " +
                "FROM exam_schedules es " +
                "JOIN classes c ON c.id = es.class_id " +
                "JOIN sections sec ON sec.id = es.section_id " +
                "JOIN subjects sub ON sub.id = es.subject_id " +
                "WHERE es.id = ?";
        List<ScheduleResponse> res = jdbc.query(sql, (rs, i) -> new ScheduleResponse(
                rs.getLong("id"),
                rs.getLong("exam_id"),
                rs.getLong("class_id"),
                rs.getString("class_name"),
                rs.getLong("section_id"),
                rs.getString("section_name"),
                rs.getLong("subject_id"),
                rs.getString("subject_code"),
                rs.getString("subject_name"),
                date(rs, "exam_date"),
                time(rs, "start_time"),
                time(rs, "end_time"),
                rs.getBigDecimal("max_marks"),
                rs.getBigDecimal("pass_marks"),
                rs.getString("room_no")
        ), scheduleId);
        return res.isEmpty() ? Optional.empty() : Optional.of(res.getFirst());
    }

    public Optional<ScheduleResponse> findScheduleForSubject(long examId, long sectionId, long subjectId) {
        String sql = "SELECT es.id, es.exam_id, es.class_id, c.name AS class_name, " +
                "es.section_id, sec.name AS section_name, es.subject_id, sub.code AS subject_code, " +
                "sub.name AS subject_name, es.exam_date, es.start_time, es.end_time, es.max_marks, es.pass_marks, es.room_no " +
                "FROM exam_schedules es " +
                "JOIN classes c ON c.id = es.class_id " +
                "JOIN sections sec ON sec.id = es.section_id " +
                "JOIN subjects sub ON sub.id = es.subject_id " +
                "WHERE es.exam_id = ? AND es.section_id = ? AND es.subject_id = ?";
        List<ScheduleResponse> res = jdbc.query(sql, (rs, i) -> new ScheduleResponse(
                rs.getLong("id"),
                rs.getLong("exam_id"),
                rs.getLong("class_id"),
                rs.getString("class_name"),
                rs.getLong("section_id"),
                rs.getString("section_name"),
                rs.getLong("subject_id"),
                rs.getString("subject_code"),
                rs.getString("subject_name"),
                date(rs, "exam_date"),
                time(rs, "start_time"),
                time(rs, "end_time"),
                rs.getBigDecimal("max_marks"),
                rs.getBigDecimal("pass_marks"),
                rs.getString("room_no")
        ), examId, sectionId, subjectId);
        return res.isEmpty() ? Optional.empty() : Optional.of(res.getFirst());
    }

    public long createSchedule(long examId, CreateScheduleRequest req) {
        String sql = "INSERT INTO exam_schedules (exam_id, class_id, section_id, subject_id, exam_date, start_time, end_time, max_marks, pass_marks, room_no) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return insert(sql, examId, req.classId(), req.sectionId(), req.subjectId(), req.examDate(), req.startTime(), req.endTime(), req.maxMarks(), req.passMarks(), req.roomNo());
    }

    public int updateSchedule(long scheduleId, UpdateScheduleRequest req) {
        String sql = "UPDATE exam_schedules SET exam_date = ?, start_time = ?, end_time = ?, max_marks = ?, pass_marks = ?, room_no = ? WHERE id = ?";
        return jdbc.update(sql, req.examDate(), req.startTime(), req.endTime(), req.maxMarks(), req.passMarks(), req.roomNo(), scheduleId);
    }

    public int deleteSchedule(long scheduleId) {
        return jdbc.update("DELETE FROM exam_schedules WHERE id = ?", scheduleId);
    }

    public boolean hasScheduleConflict(long examId, long sectionId, LocalDate date, LocalTime startTime, LocalTime endTime, Long excludeScheduleId) {
        String sql = "SELECT COUNT(*) FROM exam_schedules WHERE exam_id = ? AND section_id = ? AND exam_date = ? " +
                "AND start_time < ? AND end_time > ? AND (? IS NULL OR id <> ?)";
        Integer count = jdbc.queryForObject(sql, Integer.class, examId, sectionId, date, endTime, startTime, excludeScheduleId, excludeScheduleId);
        return count != null && count > 0;
    }

    // =========================================================================
    // TEACHER SCOPE & STUDENT ENROLLMENT
    // =========================================================================

    public Long findStaffIdForUser(long userId) {
        List<Long> ids = jdbc.query("SELECT id FROM staff WHERE user_id = ? AND status = 'ACTIVE'", (rs, i) -> rs.getLong(1), userId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public boolean isTeacherAssignedToSectionSubject(long staffId, long sessionId, long sectionId, long subjectId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM teacher_assignments WHERE staff_id = ? AND session_id = ? AND section_id = ? AND subject_id = ? AND status = 'ACTIVE'",
                Integer.class, staffId, sessionId, sectionId, subjectId);
        return count != null && count > 0;
    }

    public List<Map<String, Object>> findAssignedTeachingScopes(long staffId, long sessionId) {
        String sql = "SELECT ta.section_id, sec.name AS section_name, sec.class_id, c.name AS class_name, ta.subject_id, s.name AS subject_name " +
                "FROM teacher_assignments ta " +
                "JOIN sections sec ON sec.id = ta.section_id " +
                "JOIN classes c ON c.id = sec.class_id " +
                "JOIN subjects s ON s.id = ta.subject_id " +
                "WHERE ta.staff_id = ? AND ta.session_id = ? AND ta.status = 'ACTIVE'";
        return jdbc.queryForList(sql, staffId, sessionId);
    }

    public record EnrolledStudent(long studentId, String admissionNo, String rollNo, String studentName) {}

    public List<EnrolledStudent> findEligibleStudentsForSection(long sessionId, long sectionId) {
        String sql = "SELECT se.student_id, s.admission_no, se.roll_no, " +
                "CONCAT(s.first_name, ' ', COALESCE(s.last_name, '')) AS student_name " +
                "FROM student_enrollments se " +
                "JOIN students s ON s.id = se.student_id " +
                "WHERE se.session_id = ? AND se.section_id = ? AND s.status = 'ACTIVE' " +
                "ORDER BY se.roll_no ASC, s.first_name ASC, s.last_name ASC";
        return jdbc.query(sql, (rs, i) -> new EnrolledStudent(
                rs.getLong("student_id"),
                rs.getString("admission_no"),
                rs.getString("roll_no"),
                rs.getString("student_name").trim()
        ), sessionId, sectionId);
    }

    // =========================================================================
    // MARKS ENTRY & SUBMISSIONS
    // =========================================================================

    public List<MarkRowResponse> findExistingMarks(long examId, long scheduleId) {
        String sql = "SELECT sem.id, sem.student_id, s.admission_no, se.roll_no, " +
                "CONCAT(s.first_name, ' ', COALESCE(s.last_name, '')) AS student_name, " +
                "sem.marks_obtained, sem.max_marks, sem.pass_marks, sem.percentage, sem.grade, sem.status, sem.teacher_remarks " +
                "FROM student_exam_marks sem " +
                "JOIN students s ON s.id = sem.student_id " +
                "JOIN exam_schedules es ON es.id = sem.schedule_id " +
                "LEFT JOIN student_enrollments se ON se.student_id = s.id AND se.section_id = es.section_id " +
                "WHERE sem.exam_id = ? AND sem.schedule_id = ? " +
                "ORDER BY se.roll_no ASC, s.first_name ASC, s.last_name ASC";
        return jdbc.query(sql, (rs, i) -> new MarkRowResponse(
                rs.getLong("id"),
                rs.getLong("student_id"),
                rs.getString("admission_no"),
                rs.getString("roll_no"),
                rs.getString("student_name").trim(),
                rs.getBigDecimal("marks_obtained"),
                rs.getBigDecimal("max_marks"),
                rs.getBigDecimal("pass_marks"),
                rs.getBigDecimal("percentage"),
                rs.getString("grade"),
                rs.getString("status"),
                rs.getString("teacher_remarks")
        ), examId, scheduleId);
    }

    public void upsertStudentMark(long examId, long scheduleId, long studentId, long subjectId,
                                  BigDecimal marksObtained, BigDecimal maxMarks, BigDecimal passMarks,
                                  BigDecimal percentage, String grade, String status, String remarks, long userId) {
        String sql = "INSERT INTO student_exam_marks (exam_id, schedule_id, student_id, subject_id, marks_obtained, max_marks, pass_marks, percentage, grade, status, teacher_remarks, created_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "schedule_id = VALUES(schedule_id), " +
                "marks_obtained = VALUES(marks_obtained), " +
                "max_marks = VALUES(max_marks), " +
                "pass_marks = VALUES(pass_marks), " +
                "percentage = VALUES(percentage), " +
                "grade = VALUES(grade), " +
                "status = VALUES(status), " +
                "teacher_remarks = VALUES(teacher_remarks), " +
                "updated_at = CURRENT_TIMESTAMP(6)";
        jdbc.update(sql, examId, scheduleId, studentId, subjectId, marksObtained, maxMarks, passMarks, percentage, grade, status, remarks, userId);
    }

    public Optional<SubmissionSummaryResponse> findSubmission(long examId, long sectionId, long subjectId) {
        String sql = "SELECT ems.id, ems.exam_id, e.name AS exam_name, ems.section_id, sec.name AS section_name, " +
                "sec.class_id, c.name AS class_name, ems.subject_id, sub.name AS subject_name, " +
                "ems.teacher_id, CONCAT(st.first_name, ' ', COALESCE(st.last_name, '')) AS teacher_name, " +
                "ems.status, " +
                "(SELECT COUNT(*) FROM student_exam_marks m WHERE m.exam_id = ems.exam_id AND m.subject_id = ems.subject_id " +
                "   AND m.schedule_id IN (SELECT es.id FROM exam_schedules es WHERE es.exam_id = ems.exam_id AND es.section_id = ems.section_id)) AS student_count, " +
                "ems.submitted_at, ems.reviewed_at, uRev.full_name AS reviewed_by_name, ems.review_comments " +
                "FROM exam_marks_submissions ems " +
                "JOIN examinations e ON e.id = ems.exam_id " +
                "JOIN sections sec ON sec.id = ems.section_id " +
                "JOIN classes c ON c.id = sec.class_id " +
                "JOIN subjects sub ON sub.id = ems.subject_id " +
                "JOIN staff st ON st.id = ems.teacher_id " +
                "LEFT JOIN users uRev ON uRev.id = ems.reviewed_by " +
                "WHERE ems.exam_id = ? AND ems.section_id = ? AND ems.subject_id = ?";
        List<SubmissionSummaryResponse> res = jdbc.query(sql, (rs, i) -> new SubmissionSummaryResponse(
                rs.getLong("id"),
                rs.getLong("exam_id"),
                rs.getString("exam_name"),
                rs.getLong("section_id"),
                rs.getString("section_name"),
                rs.getLong("class_id"),
                rs.getString("class_name"),
                rs.getLong("subject_id"),
                rs.getString("subject_name"),
                rs.getLong("teacher_id"),
                rs.getString("teacher_name").trim(),
                rs.getString("status"),
                rs.getInt("student_count"),
                dateTime(rs, "submitted_at"),
                dateTime(rs, "reviewed_at"),
                rs.getString("reviewed_by_name"),
                rs.getString("review_comments")
        ), examId, sectionId, subjectId);
        return res.isEmpty() ? Optional.empty() : Optional.of(res.getFirst());
    }

    public Optional<SubmissionSummaryResponse> findSubmissionById(long submissionId) {
        String sql = "SELECT ems.id, ems.exam_id, e.name AS exam_name, ems.section_id, sec.name AS section_name, " +
                "sec.class_id, c.name AS class_name, ems.subject_id, sub.name AS subject_name, " +
                "ems.teacher_id, CONCAT(st.first_name, ' ', COALESCE(st.last_name, '')) AS teacher_name, " +
                "ems.status, " +
                "(SELECT COUNT(*) FROM student_exam_marks m WHERE m.exam_id = ems.exam_id AND m.subject_id = ems.subject_id " +
                "   AND m.schedule_id IN (SELECT es.id FROM exam_schedules es WHERE es.exam_id = ems.exam_id AND es.section_id = ems.section_id)) AS student_count, " +
                "ems.submitted_at, ems.reviewed_at, uRev.full_name AS reviewed_by_name, ems.review_comments " +
                "FROM exam_marks_submissions ems " +
                "JOIN examinations e ON e.id = ems.exam_id " +
                "JOIN sections sec ON sec.id = ems.section_id " +
                "JOIN classes c ON c.id = sec.class_id " +
                "JOIN subjects sub ON sub.id = ems.subject_id " +
                "JOIN staff st ON st.id = ems.teacher_id " +
                "LEFT JOIN users uRev ON uRev.id = ems.reviewed_by " +
                "WHERE ems.id = ?";
        List<SubmissionSummaryResponse> res = jdbc.query(sql, (rs, i) -> new SubmissionSummaryResponse(
                rs.getLong("id"),
                rs.getLong("exam_id"),
                rs.getString("exam_name"),
                rs.getLong("section_id"),
                rs.getString("section_name"),
                rs.getLong("class_id"),
                rs.getString("class_name"),
                rs.getLong("subject_id"),
                rs.getString("subject_name"),
                rs.getLong("teacher_id"),
                rs.getString("teacher_name").trim(),
                rs.getString("status"),
                rs.getInt("student_count"),
                dateTime(rs, "submitted_at"),
                dateTime(rs, "reviewed_at"),
                rs.getString("reviewed_by_name"),
                rs.getString("review_comments")
        ), submissionId);
        return res.isEmpty() ? Optional.empty() : Optional.of(res.getFirst());
    }

    public void upsertSubmissionState(long examId, long sectionId, long subjectId, long teacherId, String status, Long submittedBy) {
        String sql = "INSERT INTO exam_marks_submissions (exam_id, section_id, subject_id, teacher_id, status, submitted_at, submitted_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "status = VALUES(status), " +
                "submitted_at = VALUES(submitted_at), " +
                "submitted_by = VALUES(submitted_by), " +
                "updated_at = CURRENT_TIMESTAMP(6)";
        LocalDateTime now = "SUBMITTED".equals(status) ? LocalDateTime.now() : null;
        jdbc.update(sql, examId, sectionId, subjectId, teacherId, status, now, submittedBy);
    }

    public int updateSubmissionReview(long submissionId, String status, long reviewedBy, String comments) {
        String sql = "UPDATE exam_marks_submissions SET status = ?, reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP(6), review_comments = ? WHERE id = ?";
        return jdbc.update(sql, status, reviewedBy, comments, submissionId);
    }

    public List<SubmissionSummaryResponse> listSubmissions(Long examId, String status, Long teacherStaffId) {
        StringBuilder sql = new StringBuilder(
                "SELECT ems.id, ems.exam_id, e.name AS exam_name, ems.section_id, sec.name AS section_name, " +
                "sec.class_id, c.name AS class_name, ems.subject_id, sub.name AS subject_name, " +
                "ems.teacher_id, CONCAT(st.first_name, ' ', COALESCE(st.last_name, '')) AS teacher_name, " +
                "ems.status, " +
                "(SELECT COUNT(*) FROM student_exam_marks m WHERE m.exam_id = ems.exam_id AND m.subject_id = ems.subject_id " +
                "   AND m.schedule_id IN (SELECT es.id FROM exam_schedules es WHERE es.exam_id = ems.exam_id AND es.section_id = ems.section_id)) AS student_count, " +
                "ems.submitted_at, ems.reviewed_at, uRev.full_name AS reviewed_by_name, ems.review_comments " +
                "FROM exam_marks_submissions ems " +
                "JOIN examinations e ON e.id = ems.exam_id " +
                "JOIN sections sec ON sec.id = ems.section_id " +
                "JOIN classes c ON c.id = sec.class_id " +
                "JOIN subjects sub ON sub.id = ems.subject_id " +
                "JOIN staff st ON st.id = ems.teacher_id " +
                "LEFT JOIN users uRev ON uRev.id = ems.reviewed_by " +
                "WHERE 1=1 ");
        List<Object> args = new ArrayList<>();
        if (examId != null) { sql.append("AND ems.exam_id = ? "); args.add(examId); }
        if (status != null && !status.isBlank()) { sql.append("AND ems.status = ? "); args.add(status.trim().toUpperCase()); }
        if (teacherStaffId != null) { sql.append("AND ems.teacher_id = ? "); args.add(teacherStaffId); }

        sql.append("ORDER BY ems.submitted_at DESC, ems.id DESC");
        return jdbc.query(sql.toString(), (rs, i) -> new SubmissionSummaryResponse(
                rs.getLong("id"),
                rs.getLong("exam_id"),
                rs.getString("exam_name"),
                rs.getLong("section_id"),
                rs.getString("section_name"),
                rs.getLong("class_id"),
                rs.getString("class_name"),
                rs.getLong("subject_id"),
                rs.getString("subject_name"),
                rs.getLong("teacher_id"),
                rs.getString("teacher_name").trim(),
                rs.getString("status"),
                rs.getInt("student_count"),
                dateTime(rs, "submitted_at"),
                dateTime(rs, "reviewed_at"),
                rs.getString("reviewed_by_name"),
                rs.getString("review_comments")
        ), args.toArray());
    }

    // =========================================================================
    // GRADING SCHEME & RULES
    // =========================================================================

    public List<GradingSchemeResponse> listGradingSchemes() {
        String sql = "SELECT id, name, description, is_default FROM grading_schemes ORDER BY is_default DESC, name ASC";
        return jdbc.query(sql, (rs, i) -> new GradingSchemeResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBoolean("is_default"),
                findGradingRules(rs.getLong("id"))
        ));
    }

    public List<GradingScaleRuleDto> findGradingRules(long schemeId) {
        String sql = "SELECT id, grade, min_percentage, max_percentage, grade_point, is_pass, remarks, order_seq " +
                "FROM grading_scale_rules WHERE scheme_id = ? ORDER BY order_seq ASC";
        return jdbc.query(sql, (rs, i) -> new GradingScaleRuleDto(
                rs.getLong("id"),
                rs.getString("grade"),
                rs.getBigDecimal("min_percentage"),
                rs.getBigDecimal("max_percentage"),
                rs.getBigDecimal("grade_point"),
                rs.getBoolean("is_pass"),
                rs.getString("remarks"),
                rs.getInt("order_seq")
        ), schemeId);
    }

    public long createGradingScheme(CreateGradingSchemeRequest req) {
        long id = insert("INSERT INTO grading_schemes (name, description, is_default) VALUES (?, ?, 0)",
                req.name().trim(), req.description());
        for (int i = 0; i < req.rules().size(); i++) {
            GradingScaleRuleDto r = req.rules().get(i);
            jdbc.update("INSERT INTO grading_scale_rules (scheme_id, grade, min_percentage, max_percentage, grade_point, is_pass, remarks, order_seq) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", id, r.grade().trim().toUpperCase(), r.minPercentage(), r.maxPercentage(),
                    r.gradePoint(), r.isPass(), r.remarks(), r.orderSeq() > 0 ? r.orderSeq() : i + 1);
        }
        return id;
    }

    // =========================================================================
    // RESULTS CALCULATION & PUBLICATION
    // =========================================================================

    public void upsertOverallResult(long examId, long studentId, BigDecimal totalMax, BigDecimal totalObtained,
                                    BigDecimal percentage, String overallGrade, BigDecimal gpa,
                                    String resultStatus, Integer rankSec, Integer rankClass, boolean isPublished) {
        String sql = "INSERT INTO student_exam_results (exam_id, student_id, total_max_marks, total_marks_obtained, " +
                "percentage, overall_grade, gpa, result_status, rank_in_section, rank_in_class, is_published, published_at, calculated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6)) " +
                "ON DUPLICATE KEY UPDATE " +
                "total_max_marks = VALUES(total_max_marks), " +
                "total_marks_obtained = VALUES(total_marks_obtained), " +
                "percentage = VALUES(percentage), " +
                "overall_grade = VALUES(overall_grade), " +
                "gpa = VALUES(gpa), " +
                "result_status = VALUES(result_status), " +
                "rank_in_section = VALUES(rank_in_section), " +
                "rank_in_class = VALUES(rank_in_class), " +
                "is_published = VALUES(is_published), " +
                "published_at = VALUES(published_at), " +
                "calculated_at = CURRENT_TIMESTAMP(6), " +
                "updated_at = CURRENT_TIMESTAMP(6)";
        Timestamp pubTime = isPublished ? Timestamp.valueOf(LocalDateTime.now()) : null;
        jdbc.update(sql, examId, studentId, totalMax, totalObtained, percentage, overallGrade, gpa, resultStatus, rankSec, rankClass, isPublished, pubTime);
    }

    public int publishExamResults(long examId, long publishedBy) {
        jdbc.update("UPDATE student_exam_results SET is_published = 1, published_at = CURRENT_TIMESTAMP(6) WHERE exam_id = ?", examId);
        return jdbc.update("UPDATE examinations SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP(6), published_by = ? WHERE id = ?", publishedBy, examId);
    }

    public int unpublishExamResults(long examId) {
        jdbc.update("UPDATE student_exam_results SET is_published = 0, published_at = NULL WHERE exam_id = ?", examId);
        return jdbc.update("UPDATE examinations SET status = 'COMPLETED', published_at = NULL, published_by = NULL WHERE id = ?", examId);
    }

    public List<StudentResultResponse> findCalculatedResults(long examId, Long sectionId, Long studentId, Boolean onlyPublished) {
        StringBuilder sql = new StringBuilder(
                "SELECT ser.id, ser.exam_id, e.name AS exam_name, e.exam_type, s.code AS session_code, " +
                "ser.student_id, CONCAT(st.first_name, ' ', COALESCE(st.last_name, '')) AS student_name, " +
                "st.admission_no, se.roll_no, c.name AS class_name, sec.name AS section_name, " +
                "ser.total_max_marks, ser.total_marks_obtained, ser.percentage, ser.overall_grade, ser.gpa, " +
                "ser.result_status, ser.rank_in_section, ser.rank_in_class, ser.is_published, ser.published_at " +
                "FROM student_exam_results ser " +
                "JOIN examinations e ON e.id = ser.exam_id " +
                "JOIN academic_sessions s ON s.id = e.session_id " +
                "JOIN students st ON st.id = ser.student_id " +
                "LEFT JOIN student_enrollments se ON se.student_id = st.id AND se.session_id = e.session_id " +
                "LEFT JOIN classes c ON c.id = se.class_id " +
                "LEFT JOIN sections sec ON sec.id = se.section_id " +
                "WHERE ser.exam_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(examId);

        if (sectionId != null) { sql.append("AND se.section_id = ? "); args.add(sectionId); }
        if (studentId != null) { sql.append("AND ser.student_id = ? "); args.add(studentId); }
        if (Boolean.TRUE.equals(onlyPublished)) { sql.append("AND ser.is_published = 1 "); }

        sql.append("ORDER BY ser.percentage DESC, ser.total_marks_obtained DESC, st.first_name");
        return jdbc.query(sql.toString(), (rs, i) -> new StudentResultResponse(
                rs.getLong("exam_id"),
                rs.getString("exam_name"),
                rs.getString("exam_type"),
                rs.getString("session_code"),
                rs.getLong("student_id"),
                rs.getString("student_name").trim(),
                rs.getString("admission_no"),
                rs.getString("roll_no"),
                rs.getString("class_name"),
                rs.getString("section_name"),
                rs.getBigDecimal("total_max_marks"),
                rs.getBigDecimal("total_marks_obtained"),
                rs.getBigDecimal("percentage"),
                rs.getString("overall_grade"),
                rs.getBigDecimal("gpa"),
                rs.getString("result_status"),
                nullableInt(rs, "rank_in_section"),
                nullableInt(rs, "rank_in_class"),
                rs.getBoolean("is_published"),
                dateTime(rs, "published_at"),
                findSubjectResultsForStudent(rs.getLong("exam_id"), rs.getLong("student_id"))
        ), args.toArray());
    }

    public List<StudentSubjectResultDto> findSubjectResultsForStudent(long examId, long studentId) {
        String sql = "SELECT sem.subject_id, sub.code AS subject_code, sub.name AS subject_name, " +
                "sem.marks_obtained, sem.max_marks, sem.pass_marks, sem.percentage, sem.grade, sem.status, " +
                "(CASE WHEN sem.status = 'PRESENT' AND sem.marks_obtained >= sem.pass_marks THEN 1 ELSE 0 END) AS is_pass, " +
                "sem.teacher_remarks " +
                "FROM student_exam_marks sem " +
                "JOIN subjects sub ON sub.id = sem.subject_id " +
                "WHERE sem.exam_id = ? AND sem.student_id = ? " +
                "ORDER BY sub.name ASC";
        return jdbc.query(sql, (rs, i) -> new StudentSubjectResultDto(
                rs.getLong("subject_id"),
                rs.getString("subject_code"),
                rs.getString("subject_name"),
                rs.getBigDecimal("marks_obtained"),
                rs.getBigDecimal("max_marks"),
                rs.getBigDecimal("pass_marks"),
                rs.getBigDecimal("percentage"),
                rs.getString("grade"),
                rs.getString("status"),
                rs.getBoolean("is_pass"),
                rs.getString("teacher_remarks")
        ), examId, studentId);
    }

    // =========================================================================
    // CORRECTIONS & AUDIT
    // =========================================================================

    public long recordCorrection(long examId, long studentId, long subjectId,
                                 BigDecimal previousMarks, BigDecimal newMarks,
                                 String previousStatus, String newStatus,
                                 String reason, long correctedBy) {
        String sql = "INSERT INTO exam_result_corrections (exam_id, student_id, subject_id, previous_marks, new_marks, previous_status, new_status, reason, corrected_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return insert(sql, examId, studentId, subjectId, previousMarks, newMarks, previousStatus, newStatus, reason.trim(), correctedBy);
    }

    public List<CorrectionResponse> listCorrections(long examId) {
        String sql = "SELECT erc.id, erc.exam_id, erc.student_id, " +
                "CONCAT(st.first_name, ' ', COALESCE(st.last_name, '')) AS student_name, " +
                "erc.subject_id, sub.name AS subject_name, erc.previous_marks, erc.new_marks, " +
                "erc.previous_status, erc.new_status, erc.reason, u.full_name AS corrected_by_name, erc.corrected_at " +
                "FROM exam_result_corrections erc " +
                "JOIN students st ON st.id = erc.student_id " +
                "JOIN subjects sub ON sub.id = erc.subject_id " +
                "JOIN users u ON u.id = erc.corrected_by " +
                "WHERE erc.exam_id = ? " +
                "ORDER BY erc.corrected_at DESC";
        return jdbc.query(sql, (rs, i) -> new CorrectionResponse(
                rs.getLong("id"),
                rs.getLong("exam_id"),
                rs.getLong("student_id"),
                rs.getString("student_name").trim(),
                rs.getLong("subject_id"),
                rs.getString("subject_name"),
                rs.getBigDecimal("previous_marks"),
                rs.getBigDecimal("new_marks"),
                rs.getString("previous_status"),
                rs.getString("new_status"),
                rs.getString("reason"),
                rs.getString("corrected_by_name"),
                dateTime(rs, "corrected_at")
        ), examId);
    }

    // =========================================================================
    // PARENT / STUDENT SCOPING HELPERS
    // =========================================================================

    public Long findStudentIdForUser(long userId) {
        List<Long> ids = jdbc.query("SELECT id FROM students WHERE user_id = ?", (rs, i) -> rs.getLong(1), userId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public List<Long> findLinkedStudentIdsForGuardian(long userId) {
        String sql = "SELECT sg.student_id FROM student_guardians sg JOIN guardians g ON g.id = sg.guardian_id WHERE g.user_id = ?";
        return jdbc.query(sql, (rs, i) -> rs.getLong(1), userId);
    }

    // =========================================================================
    // ANALYTICS & DASHBOARD
    // =========================================================================

    public ExamAnalyticsSummary getExamAnalytics(long examId) {
        String examName = jdbc.queryForObject("SELECT name FROM examinations WHERE id = ?", String.class, examId);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM student_exam_results WHERE exam_id = ?", Integer.class, examId);
        Integer appeared = jdbc.queryForObject("SELECT COUNT(*) FROM student_exam_results WHERE exam_id = ? AND total_marks_obtained > 0", Integer.class, examId);
        Integer passed = jdbc.queryForObject("SELECT COUNT(*) FROM student_exam_results WHERE exam_id = ? AND result_status = 'PASS'", Integer.class, examId);
        Integer failed = jdbc.queryForObject("SELECT COUNT(*) FROM student_exam_results WHERE exam_id = ? AND result_status = 'FAIL'", Integer.class, examId);

        BigDecimal passPct = (appeared != null && appeared > 0 && passed != null)
                ? BigDecimal.valueOf(passed * 100.0 / appeared).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> stats = jdbc.queryForMap(
                "SELECT COALESCE(AVG(percentage), 0) AS avg_pct, COALESCE(MAX(percentage), 0) AS max_pct, COALESCE(MIN(percentage), 0) AS min_pct " +
                "FROM student_exam_results WHERE exam_id = ?", examId);

        // Grade distribution
        String gdSql = "SELECT overall_grade AS grade, COUNT(*) AS count, " +
                "ROUND(COUNT(*) * 100.0 / (SELECT GREATEST(COUNT(*), 1) FROM student_exam_results WHERE exam_id = ?), 2) AS pct " +
                "FROM student_exam_results WHERE exam_id = ? AND overall_grade IS NOT NULL " +
                "GROUP BY overall_grade ORDER BY count DESC";
        List<GradeDistributionDto> gradeDist = jdbc.query(gdSql, (rs, i) -> new GradeDistributionDto(
                rs.getString("grade"),
                rs.getInt("count"),
                rs.getBigDecimal("pct")
        ), examId, examId);

        // Subject performance
        String spSql = "SELECT sem.subject_id, s.name AS subject_name, " +
                "ROUND(AVG(sem.marks_obtained), 2) AS avg_marks, " +
                "ROUND(MAX(sem.marks_obtained), 2) AS max_marks, " +
                "ROUND(MIN(sem.marks_obtained), 2) AS min_marks, " +
                "ROUND(SUM(CASE WHEN sem.marks_obtained >= sem.pass_marks THEN 1 ELSE 0 END) * 100.0 / GREATEST(COUNT(*), 1), 2) AS pass_pct " +
                "FROM student_exam_marks sem " +
                "JOIN subjects s ON s.id = sem.subject_id " +
                "WHERE sem.exam_id = ? AND sem.status = 'PRESENT' " +
                "GROUP BY sem.subject_id, s.name ORDER BY s.name";
        List<SubjectPerformanceDto> subPerf = jdbc.query(spSql, (rs, i) -> new SubjectPerformanceDto(
                rs.getLong("subject_id"),
                rs.getString("subject_name"),
                rs.getBigDecimal("avg_marks"),
                rs.getBigDecimal("max_marks"),
                rs.getBigDecimal("min_marks"),
                rs.getBigDecimal("pass_pct")
        ), examId);

        // Attention students
        String attSql = "SELECT ser.student_id, CONCAT(st.first_name, ' ', COALESCE(st.last_name, '')) AS student_name, " +
                "st.admission_no, ser.percentage, ser.result_status, " +
                "(SELECT COUNT(*) FROM student_exam_marks m WHERE m.exam_id = ser.exam_id AND m.student_id = ser.student_id AND (m.status = 'ABSENT' OR m.marks_obtained < m.pass_marks)) AS failed_count " +
                "FROM student_exam_results ser " +
                "JOIN students st ON st.id = ser.student_id " +
                "WHERE ser.exam_id = ? AND (ser.result_status = 'FAIL' OR ser.percentage < 40) " +
                "ORDER BY ser.percentage ASC LIMIT 20";
        List<AttentionStudentDto> attentionList = jdbc.query(attSql, (rs, i) -> new AttentionStudentDto(
                rs.getLong("student_id"),
                rs.getString("student_name").trim(),
                rs.getString("admission_no"),
                rs.getBigDecimal("percentage"),
                rs.getString("result_status"),
                rs.getInt("failed_count")
        ), examId);

        return new ExamAnalyticsSummary(
                examId,
                examName != null ? examName : "Examination",
                total != null ? total : 0,
                appeared != null ? appeared : 0,
                passed != null ? passed : 0,
                failed != null ? failed : 0,
                passPct,
                BigDecimal.valueOf(((Number) stats.get("avg_pct")).doubleValue()).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(((Number) stats.get("max_pct")).doubleValue()).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(((Number) stats.get("min_pct")).doubleValue()).setScale(2, RoundingMode.HALF_UP),
                gradeDist,
                subPerf,
                attentionList
        );
    }

    public ExamDashboardOverview getDashboardOverview(Long teacherStaffId) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM examinations", Integer.class);
        Integer upcoming = jdbc.queryForObject("SELECT COUNT(*) FROM examinations WHERE start_date > CURRENT_DATE()", Integer.class);
        Integer ongoing = jdbc.queryForObject("SELECT COUNT(*) FROM examinations WHERE CURRENT_DATE() BETWEEN start_date AND end_date", Integer.class);
        Integer published = jdbc.queryForObject("SELECT COUNT(*) FROM examinations WHERE status = 'PUBLISHED'", Integer.class);

        Integer pendingSubmissions;
        Integer awaitingReview;

        if (teacherStaffId != null) {
            pendingSubmissions = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM exam_schedules es " +
                    "JOIN teacher_assignments ta ON ta.section_id = es.section_id AND ta.subject_id = es.subject_id " +
                    "WHERE ta.staff_id = ? AND ta.status = 'ACTIVE' AND NOT EXISTS (" +
                    "   SELECT 1 FROM exam_marks_submissions ems WHERE ems.exam_id = es.exam_id AND ems.section_id = es.section_id AND ems.subject_id = es.subject_id AND ems.status IN ('SUBMITTED', 'APPROVED')" +
                    ")", Integer.class, teacherStaffId);
            awaitingReview = 0;
        } else {
            pendingSubmissions = jdbc.queryForObject("SELECT COUNT(*) FROM exam_marks_submissions WHERE status = 'DRAFT'", Integer.class);
            awaitingReview = jdbc.queryForObject("SELECT COUNT(*) FROM exam_marks_submissions WHERE status = 'SUBMITTED'", Integer.class);
        }

        List<ExamSummaryResponse> recent = listExams(null, null, null, null, null);
        if (recent.size() > 5) recent = recent.subList(0, 5);

        List<SubmissionSummaryResponse> actionRequired = listSubmissions(null, teacherStaffId != null ? "REJECTED" : "SUBMITTED", teacherStaffId);
        if (actionRequired.size() > 5) actionRequired = actionRequired.subList(0, 5);

        return new ExamDashboardOverview(
                total != null ? total : 0,
                upcoming != null ? upcoming : 0,
                ongoing != null ? ongoing : 0,
                pendingSubmissions != null ? pendingSubmissions : 0,
                awaitingReview != null ? awaitingReview : 0,
                published != null ? published : 0,
                recent,
                actionRequired
        );
    }

    public List<Long> findDistinctStudentIdsWithMarks(long examId) {
        return jdbc.query("SELECT DISTINCT student_id FROM student_exam_marks WHERE exam_id = ? ORDER BY student_id",
                (rs, i) -> rs.getLong(1), examId);
    }

    public Long getStudentSectionId(long sessionId, long studentId) {
        List<Long> secIds = jdbc.query("SELECT section_id FROM student_enrollments WHERE session_id = ? AND student_id = ? LIMIT 1",
                (rs, i) -> rs.getLong(1), sessionId, studentId);
        return secIds.isEmpty() ? null : secIds.getFirst();
    }

    public Optional<MarkRowResponse> findStudentMark(long examId, long studentId, long subjectId) {
        String sql = "SELECT sem.id, sem.student_id, st.admission_no, se.roll_no, " +
                "CONCAT(st.first_name, ' ', COALESCE(st.last_name, '')) AS student_name, " +
                "sem.marks_obtained, sem.max_marks, sem.pass_marks, sem.percentage, sem.grade, sem.status, sem.teacher_remarks " +
                "FROM student_exam_marks sem " +
                "JOIN students st ON st.id = sem.student_id " +
                "JOIN examinations e ON e.id = sem.exam_id " +
                "LEFT JOIN student_enrollments se ON se.student_id = st.id AND se.session_id = e.session_id " +
                "WHERE sem.exam_id = ? AND sem.student_id = ? AND sem.subject_id = ?";
        List<MarkRowResponse> res = jdbc.query(sql, (rs, i) -> new MarkRowResponse(
                rs.getLong("id"),
                rs.getLong("student_id"),
                rs.getString("admission_no"),
                rs.getString("roll_no"),
                rs.getString("student_name").trim(),
                rs.getBigDecimal("marks_obtained"),
                rs.getBigDecimal("max_marks"),
                rs.getBigDecimal("pass_marks"),
                rs.getBigDecimal("percentage"),
                rs.getString("grade"),
                rs.getString("status"),
                rs.getString("teacher_remarks")
        ), examId, studentId, subjectId);
        return res.isEmpty() ? Optional.empty() : Optional.of(res.getFirst());
    }

    public int countSchedules(long examId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM exam_schedules WHERE exam_id = ?", Integer.class, examId);
        return c == null ? 0 : c;
    }

    public int countApprovedSubmissions(long examId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM exam_marks_submissions WHERE exam_id = ? AND status = 'APPROVED'", Integer.class, examId);
        return c == null ? 0 : c;
    }

    public int countPendingSubmissions(long examId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM exam_marks_submissions WHERE exam_id = ? AND status IN ('DRAFT', 'SUBMITTED', 'REJECTED')", Integer.class, examId);
        return c == null ? 0 : c;
    }

    public boolean isTeacherAssignedToExam(long staffId, long examId) {
        String sql = "SELECT COUNT(*) FROM examinations e " +
                "JOIN teacher_assignments ta ON ta.session_id = e.session_id AND (e.section_id IS NULL OR ta.section_id = e.section_id) " +
                "JOIN sections sec ON sec.id = ta.section_id AND sec.class_id = e.class_id " +
                "WHERE e.id = ? AND ta.staff_id = ? AND ta.status = 'ACTIVE'";
        Integer c = jdbc.queryForObject(sql, Integer.class, examId, staffId);
        return c != null && c > 0;
    }

    public Map<String, String> getInstitutionDetails() {
        Map<String, String> details = new HashMap<>();
        details.put("name", "EduFlow Academy");
        details.put("address", "123 Education Blvd, Knowledge City");
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT setting_key, setting_value FROM system_settings WHERE setting_key IN ('institution.name', 'institution.address')");
            for (Map<String, Object> r : rows) {
                String k = (String) r.get("setting_key");
                String v = (String) r.get("setting_value");
                if ("institution.name".equals(k) && v != null && !v.isBlank()) details.put("name", v);
                if ("institution.address".equals(k) && v != null && !v.isBlank()) details.put("address", v);
            }
        } catch (Exception ignored) {}
        return details;
    }

    public BigDecimal getStudentAttendancePercentage(long sessionId, long studentId) {
        try {
            String sql = "SELECT COUNT(CASE WHEN status IN ('PRESENT','LATE','HALF_DAY') THEN 1 END) * 100.0 / NULLIF(COUNT(*), 0) " +
                    "FROM attendance WHERE session_id = ? AND student_id = ?";
            BigDecimal pct = jdbc.queryForObject(sql, BigDecimal.class, sessionId, studentId);
            return pct != null ? pct.setScale(1, RoundingMode.HALF_UP) : BigDecimal.valueOf(100.0);
        } catch (Exception e) {
            return BigDecimal.valueOf(100.0);
        }
    }

    // =========================================================================
    // UTILITY METHODS
    // =========================================================================

    private long insert(String sql, Object... params) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps;
        }, kh);
        if (kh.getKeys() != null) {
            Object idVal = kh.getKeys().get("ID");
            if (idVal == null) idVal = kh.getKeys().get("id");
            if (idVal instanceof Number n) return n.longValue();
        }
        if (kh.getKeyList() != null && !kh.getKeyList().isEmpty()) {
            var firstMap = kh.getKeyList().getFirst();
            Object idVal = firstMap.get("ID");
            if (idVal == null) idVal = firstMap.get("id");
            if (idVal instanceof Number n) return n.longValue();
        }
        Number key = kh.getKey();
        if (key != null) return key.longValue();
        throw new IllegalStateException("Generated key was null");
    }

    private static LocalDate date(ResultSet r, String col) throws SQLException {
        java.sql.Date d = r.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    private static LocalDateTime dateTime(ResultSet r, String col) throws SQLException {
        Timestamp t = r.getTimestamp(col);
        return t == null ? null : t.toLocalDateTime();
    }

    private static LocalTime time(ResultSet r, String col) throws SQLException {
        Time t = r.getTime(col);
        return t == null ? null : t.toLocalTime();
    }

    private static Long nullableLong(ResultSet r, String col) throws SQLException {
        long v = r.getLong(col);
        return r.wasNull() ? null : v;
    }

    private static Integer nullableInt(ResultSet r, String col) throws SQLException {
        int v = r.getInt(col);
        return r.wasNull() ? null : v;
    }
}
