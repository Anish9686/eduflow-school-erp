package com.erp.portal.attendance;

import com.erp.portal.academic.AcademicRepository;
import com.erp.portal.administration.AuditService;
import com.erp.portal.attendance.AttendanceDtos.*;
import com.erp.portal.attendance.AttendanceRepository.StaffMarkResult;
import com.erp.portal.attendance.AttendanceRepository.StudentMarkResult;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock AttendanceRepository repo;
    @Mock AcademicRepository academics;
    @Mock AuditService audit;
    @Mock HttpServletRequest request;

    @InjectMocks AttendanceService service;

    private static final LocalDate TEST_DATE = LocalDate.parse("2026-10-06");
    private static final String IDEMPOTENCY_KEY = "test-idemp-key-12345";

    // ==========================================
    // 1. NORMAL MARKING (REQ-ATT-001)
    // ==========================================
    @Test
    void normalMarkingSucceedsForAssignedTeacher() {
        when(repo.exists("academic_sessions", 1L)).thenReturn(true);
        when(repo.sessionContainsDate(1L, TEST_DATE)).thenReturn(true);
        when(repo.validClassSection(2L, 3L)).thenReturn(true);
        when(repo.staffForUser(10L)).thenReturn(100L);
        when(academics.isAssignedToSection(100L, 1L, 3L)).thenReturn(true);
        when(repo.studentLocked(1L, TEST_DATE)).thenReturn(false);
        when(repo.activeStudent(50L)).thenReturn(true);
        when(repo.enrolled(1L, 50L, 2L, 3L)).thenReturn(true);

        StudentAttendance record = sampleRecord(1L, 50L, "PRESENT", false);
        when(repo.markStudent(eq(1L), eq(2L), eq(3L), eq(TEST_DATE), eq(50L), eq("PRESENT"), isNull(), eq(10L), eq(100L), anyString()))
                .thenReturn(new StudentMarkResult(record, true));

        var batch = new StudentMarkBatchRequest(1L, 2L, 3L, TEST_DATE, List.of(new StudentMarkEntry(50L, "PRESENT", null)));
        MarkBatchResult result = service.markStudents(batch, IDEMPOTENCY_KEY, teacherPrincipal(), request);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.alreadyMarked()).isEqualTo(0);
        assertThat(result.records()).hasSize(1);
        assertThat(result.records().getFirst().status()).isEqualTo("PRESENT");

        verify(audit).record(eq("STUDENT_ATTENDANCE_MARKED"), eq("ATTENDANCE"), eq("1"), eq(teacherPrincipal()), isNull(), isNull(), anyMap(), eq(request));
    }

    // ==========================================
    // 2. DUPLICATE MARKING (REQ-ATT-002)
    // ==========================================
    @Test
    void duplicateMarkingWithIdenticalValuesIsHandledIdempotently() {
        when(repo.exists("academic_sessions", 1L)).thenReturn(true);
        when(repo.sessionContainsDate(1L, TEST_DATE)).thenReturn(true);
        when(repo.validClassSection(2L, 3L)).thenReturn(true);
        when(repo.staffForUser(10L)).thenReturn(100L);
        when(academics.isAssignedToSection(100L, 1L, 3L)).thenReturn(true);
        when(repo.studentLocked(1L, TEST_DATE)).thenReturn(false);
        when(repo.activeStudent(50L)).thenReturn(true);
        when(repo.enrolled(1L, 50L, 2L, 3L)).thenReturn(true);

        // Repo returns created = false for existing identical record
        StudentAttendance existing = sampleRecord(1L, 50L, "PRESENT", false);
        when(repo.markStudent(eq(1L), eq(2L), eq(3L), eq(TEST_DATE), eq(50L), eq("PRESENT"), isNull(), eq(10L), eq(100L), anyString()))
                .thenReturn(new StudentMarkResult(existing, false));

        var batch = new StudentMarkBatchRequest(1L, 2L, 3L, TEST_DATE, List.of(new StudentMarkEntry(50L, "PRESENT", null)));
        MarkBatchResult result = service.markStudents(batch, IDEMPOTENCY_KEY, teacherPrincipal(), request);

        assertThat(result.created()).isEqualTo(0);
        assertThat(result.alreadyMarked()).isEqualTo(1);
        assertThat(result.records()).hasSize(1);
        verify(audit, never()).record(eq("STUDENT_ATTENDANCE_MARKED"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void duplicateStudentInSameBatchIsRejected() {
        when(repo.exists("academic_sessions", 1L)).thenReturn(true);
        when(repo.sessionContainsDate(1L, TEST_DATE)).thenReturn(true);
        when(repo.validClassSection(2L, 3L)).thenReturn(true);
        when(repo.studentLocked(1L, TEST_DATE)).thenReturn(false);

        var batch = new StudentMarkBatchRequest(1L, 2L, 3L, TEST_DATE, List.of(
                new StudentMarkEntry(50L, "PRESENT", null),
                new StudentMarkEntry(50L, "ABSENT", null)
        ));

        assertThatThrownBy(() -> service.markStudents(batch, IDEMPOTENCY_KEY, adminPrincipal(), request))
                .isInstanceOf(AttendanceException.class)
                .hasMessageContaining("once in a marking batch");
    }

    // ==========================================
    // 3. IDEMPOTENCY KEY VALIDATION
    // ==========================================
    @Test
    void rejectsInvalidIdempotencyKey() {
        var batch = new StudentMarkBatchRequest(1L, 2L, 3L, TEST_DATE, List.of(new StudentMarkEntry(50L, "PRESENT", null)));

        assertThatThrownBy(() -> service.markStudents(batch, "short", adminPrincipal(), request))
                .isInstanceOf(AttendanceException.class)
                .hasMessageContaining("between 8 and 80 safe characters");

        assertThatThrownBy(() -> service.markStudents(batch, "invalid chars @!#$%^&*()", adminPrincipal(), request))
                .isInstanceOf(AttendanceException.class)
                .hasMessageContaining("between 8 and 80 safe characters");
    }

    // ==========================================
    // 4. WRONG TEACHER SCOPE (REQ-ATT-001)
    // ==========================================
    @Test
    void teacherCannotMarkSectionTheyAreNotAssignedTo() {
        when(repo.exists("academic_sessions", 1L)).thenReturn(true);
        when(repo.sessionContainsDate(1L, TEST_DATE)).thenReturn(true);
        when(repo.validClassSection(2L, 3L)).thenReturn(true);
        when(repo.staffForUser(10L)).thenReturn(100L);
        when(academics.isAssignedToSection(100L, 1L, 3L)).thenReturn(false); // Not assigned!

        var batch = new StudentMarkBatchRequest(1L, 2L, 3L, TEST_DATE, List.of(new StudentMarkEntry(50L, "PRESENT", null)));

        assertThatThrownBy(() -> service.markStudents(batch, IDEMPOTENCY_KEY, teacherPrincipal(), request))
                .isInstanceOf(AttendanceException.class)
                .matches(e -> ((AttendanceException) e).status().value() == 403);
    }

    @Test
    void teacherCannotViewRosterOutsideAssignedSection() {
        when(repo.exists("academic_sessions", 1L)).thenReturn(true);
        when(repo.validClassSection(2L, 3L)).thenReturn(true);
        when(repo.staffForUser(10L)).thenReturn(100L);
        when(academics.isAssignedToSection(100L, 1L, 3L)).thenReturn(false);

        assertThatThrownBy(() -> service.roster(1L, 2L, 3L, TEST_DATE, teacherPrincipal()))
                .isInstanceOf(AttendanceException.class)
                .matches(e -> ((AttendanceException) e).status().value() == 403);
    }

    @Test
    void unassignedStudentInMarkingBatchIsRejected() {
        when(repo.exists("academic_sessions", 1L)).thenReturn(true);
        when(repo.sessionContainsDate(1L, TEST_DATE)).thenReturn(true);
        when(repo.validClassSection(2L, 3L)).thenReturn(true);
        when(repo.studentLocked(1L, TEST_DATE)).thenReturn(false);
        when(repo.activeStudent(50L)).thenReturn(true);
        when(repo.enrolled(1L, 50L, 2L, 3L)).thenReturn(false); // Student not enrolled in section!

        var batch = new StudentMarkBatchRequest(1L, 2L, 3L, TEST_DATE, List.of(new StudentMarkEntry(50L, "PRESENT", null)));

        assertThatThrownBy(() -> service.markStudents(batch, IDEMPOTENCY_KEY, adminPrincipal(), request))
                .isInstanceOf(AttendanceException.class)
                .matches(e -> ((AttendanceException) e).status().value() == 403);
    }

    // ==========================================
    // 5. CORRECTION AUTHORIZATION & AUDIT (REQ-ATT-003)
    // ==========================================
    @Test
    void correctionRequiresAuthorizedPermission() {
        SecurityPrincipal student = new SecurityPrincipal(20L, "student", "Student", List.of("STUDENT"), Set.of("ATTENDANCE:READ"), "hash");
        var correction = new AttendanceCorrectionRequest("ABSENT", "Medical leave certificate");

        assertThatThrownBy(() -> service.correctStudent(1L, correction, student, request))
                .isInstanceOf(AttendanceException.class)
                .matches(e -> ((AttendanceException) e).status().value() == 403);
    }

    @Test
    void correctionRejectsIdenticalStatus() {
        StudentAttendance existing = sampleRecord(1L, 50L, "PRESENT", false);
        when(repo.studentAttendance(1L)).thenReturn(Optional.of(existing));
        when(repo.studentLocked(1L, TEST_DATE)).thenReturn(false);

        var correction = new AttendanceCorrectionRequest("PRESENT", "Typo correction");

        assertThatThrownBy(() -> service.correctStudent(1L, correction, adminPrincipal(), request))
                .isInstanceOf(AttendanceException.class)
                .hasMessageContaining("must change the recorded status");
    }

    @Test
    void validCorrectionUpdatesStatusAuditsAndReturnsUpdatedRecord() {
        StudentAttendance existing = sampleRecord(1L, 50L, "PRESENT", false);
        StudentAttendance updated = sampleRecord(1L, 50L, "ABSENT", true);

        when(repo.studentAttendance(1L)).thenReturn(Optional.of(existing)).thenReturn(Optional.of(updated));
        when(repo.studentLocked(1L, TEST_DATE)).thenReturn(false);

        var correction = new AttendanceCorrectionRequest("ABSENT", "Doctor appointment noted");
        StudentAttendance result = service.correctStudent(1L, correction, adminPrincipal(), request);

        assertThat(result.status()).isEqualTo("ABSENT");
        assertThat(result.corrected()).isTrue();

        verify(repo).addStudentCorrection(1L, "PRESENT", "ABSENT", "Doctor appointment noted", 1L);
        verify(repo).correctStudent(1L, "ABSENT", 1L);
        verify(audit).record(eq("STUDENT_ATTENDANCE_CORRECTED"), eq("ATTENDANCE"), eq("1"), eq(adminPrincipal()), isNull(), anyMap(), anyMap(), eq(request));
    }

    // ==========================================
    // 6. DATE FILTERING (REQ-ATT-004)
    // ==========================================
    @Test
    void rejectsDateFilterWhereFromIsAfterTo() {
        LocalDate from = LocalDate.parse("2026-10-10");
        LocalDate to = LocalDate.parse("2026-10-01");

        assertThatThrownBy(() -> service.studentRecords(from, to, null, null, null, null, null, null, adminPrincipal()))
                .isInstanceOf(AttendanceException.class)
                .hasMessageContaining("Must be on or before to");
    }

    @Test
    void studentRecordsPassesAllFiltersToRepository() {
        LocalDate from = LocalDate.parse("2026-10-01");
        LocalDate to = LocalDate.parse("2026-10-31");

        when(repo.studentRecords(eq(from), eq(to), eq(1L), eq(2L), eq(3L), eq(50L), isNull(), eq("PRESENT"), isNull(), isNull(), isNull()))
                .thenReturn(List.of(sampleRecord(1L, 50L, "PRESENT", false)));

        List<StudentAttendance> rows = service.studentRecords(from, to, 1L, 2L, 3L, 50L, null, "PRESENT", adminPrincipal());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().studentId()).isEqualTo(50L);
    }

    @Test
    void accountantCannotViewStudentAttendanceRecords() {
        SecurityPrincipal accountant = new SecurityPrincipal(40L, "accountant", "Accountant",
                List.of("ACCOUNTANT"), Set.of("ATTENDANCE:READ"), "hash");
        assertThatThrownBy(() -> service.studentRecords(null, null, 1L, 2L, 3L, null, null, null, accountant))
                .isInstanceOf(AttendanceException.class)
                .matches(e -> ((AttendanceException) e).status().value() == 403);
    }

    // ==========================================
    // 7. LOCKED DATES
    // ==========================================
    @Test
    void markingRejectedWhenDateIsLocked() {
        when(repo.exists("academic_sessions", 1L)).thenReturn(true);
        when(repo.sessionContainsDate(1L, TEST_DATE)).thenReturn(true);
        when(repo.validClassSection(2L, 3L)).thenReturn(true);
        when(repo.studentLocked(1L, TEST_DATE)).thenReturn(true); // Locked!

        var batch = new StudentMarkBatchRequest(1L, 2L, 3L, TEST_DATE, List.of(new StudentMarkEntry(50L, "PRESENT", null)));

        assertThatThrownBy(() -> service.markStudents(batch, IDEMPOTENCY_KEY, adminPrincipal(), request))
                .isInstanceOf(AttendanceException.class)
                .matches(e -> ((AttendanceException) e).status().value() == 409)
                .hasMessageContaining("Attendance date is locked");
    }

    @Test
    void correctionRejectedWhenDateIsLocked() {
        StudentAttendance existing = sampleRecord(1L, 50L, "PRESENT", false);
        when(repo.studentAttendance(1L)).thenReturn(Optional.of(existing));
        when(repo.studentLocked(1L, TEST_DATE)).thenReturn(true); // Locked!

        var correction = new AttendanceCorrectionRequest("ABSENT", "Late reason");

        assertThatThrownBy(() -> service.correctStudent(1L, correction, adminPrincipal(), request))
                .isInstanceOf(AttendanceException.class)
                .matches(e -> ((AttendanceException) e).status().value() == 409)
                .hasMessageContaining("Attendance date is locked");
    }

    // ==========================================
    // 8. TIMEZONE / DATE BOUNDARIES
    // ==========================================
    @Test
    void dateOutsideSessionBoundaryIsRejected() {
        when(repo.exists("academic_sessions", 1L)).thenReturn(true);
        when(repo.sessionContainsDate(1L, TEST_DATE)).thenReturn(false); // Outside session boundary!

        var batch = new StudentMarkBatchRequest(1L, 2L, 3L, TEST_DATE, List.of(new StudentMarkEntry(50L, "PRESENT", null)));

        assertThatThrownBy(() -> service.markStudents(batch, IDEMPOTENCY_KEY, adminPrincipal(), request))
                .isInstanceOf(AttendanceException.class)
                .hasMessageContaining("fall within the selected academic session");
    }

    // ==========================================
    // 9. HISTORICAL INTEGRITY & SCOPE (REQ-ATT-005)
    // ==========================================
    @Test
    void studentUserCanOnlyAccessOwnAttendanceRecords() {
        SecurityPrincipal student = new SecurityPrincipal(20L, "student20", "Student Twenty", List.of("STUDENT"), Set.of("ATTENDANCE:READ"), "hash");

        when(repo.studentRecords(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(20L), isNull(), isNull()))
                .thenReturn(List.of(sampleRecord(1L, 50L, "PRESENT", false)));

        List<StudentAttendance> rows = service.studentRecords(null, null, null, null, null, null, null, null, student);
        assertThat(rows).hasSize(1);
        verify(repo).studentRecords(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(20L), isNull(), isNull());
    }

    // ==========================================
    // 10. STAFF ATTENDANCE
    // ==========================================
    @Test
    void normalStaffMarkingSucceeds() {
        when(repo.staffLocked(TEST_DATE)).thenReturn(false);
        when(repo.activeStaff(100L)).thenReturn(true);

        StaffAttendance record = sampleStaffRecord(1L, 100L, "PRESENT", false);
        when(repo.markStaff(eq(TEST_DATE), eq(100L), eq("PRESENT"), isNull(), eq(1L), anyString()))
                .thenReturn(new StaffMarkResult(record, true));

        var batch = new StaffMarkBatchRequest(TEST_DATE, List.of(new StaffMarkEntry(100L, "PRESENT", null)));
        StaffMarkBatchResult result = service.markStaff(batch, IDEMPOTENCY_KEY, adminPrincipal(), request);

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.alreadyMarked()).isEqualTo(0);
        assertThat(result.records()).hasSize(1);
        verify(audit).record(eq("STAFF_ATTENDANCE_MARKED"), eq("STAFF_ATTENDANCE"), eq("1"), eq(adminPrincipal()), isNull(), isNull(), anyMap(), eq(request));
    }

    @Test
    void staffCorrectionAuditsAndUpdates() {
        StaffAttendance existing = sampleStaffRecord(1L, 100L, "PRESENT", false);
        StaffAttendance updated = sampleStaffRecord(1L, 100L, "ON_LEAVE", true);

        when(repo.staffAttendance(1L)).thenReturn(Optional.of(existing)).thenReturn(Optional.of(updated));
        when(repo.staffLocked(TEST_DATE)).thenReturn(false);

        var correction = new StaffAttendanceCorrectionRequest("ON_LEAVE", "Casual leave approved");
        StaffAttendance result = service.correctStaff(1L, correction, adminPrincipal(), request);

        assertThat(result.status()).isEqualTo("ON_LEAVE");
        verify(repo).addStaffCorrection(1L, "PRESENT", "ON_LEAVE", "Casual leave approved", 1L);
        verify(repo).correctStaff(1L, "ON_LEAVE");
        verify(audit).record(eq("STAFF_ATTENDANCE_CORRECTED"), eq("STAFF_ATTENDANCE"), eq("1"), eq(adminPrincipal()), isNull(), anyMap(), anyMap(), eq(request));
    }

    // ==========================================
    // 11. DATE LOCKS
    // ==========================================
    @Test
    void dateLockCreationAndUnlockAudited() {
        var lockReq = new DateLockRequest("STUDENT", 1L, TEST_DATE, "Holiday shutdown");
        when(repo.exists("academic_sessions", 1L)).thenReturn(true);
        when(repo.createLock(lockReq, 1L)).thenReturn(5L);
        DateLock dateLock = new DateLock(5L, "STUDENT", 1L, TEST_DATE, "Holiday shutdown", 1L, "Admin", LocalDateTime.now());
        when(repo.lock(5L)).thenReturn(Optional.of(dateLock));

        DateLock created = service.lock(lockReq, adminPrincipal(), request);
        assertThat(created.id()).isEqualTo(5L);
        verify(audit).record(eq("ATTENDANCE_DATE_LOCKED"), eq("ATTENDANCE_DATE_LOCK"), eq("5"), eq(adminPrincipal()), isNull(), isNull(), anyMap(), eq(request));

        service.unlock(5L, adminPrincipal(), request);
        verify(repo).removeLock(5L);
        verify(audit).record(eq("ATTENDANCE_DATE_UNLOCKED"), eq("ATTENDANCE_DATE_LOCK"), eq("5"), eq(adminPrincipal()), isNull(), anyMap(), anyMap(), eq(request));
    }

    // ==========================================
    // HELPERS
    // ==========================================
    private static SecurityPrincipal adminPrincipal() {
        return new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"),
                Set.of("ATTENDANCE:READ", "ATTENDANCE:MARK", "ATTENDANCE:CORRECT", "ATTENDANCE:MANAGE",
                        "STAFF_ATTENDANCE:READ", "STAFF_ATTENDANCE:MARK", "STAFF_ATTENDANCE:CORRECT", "STAFF_ATTENDANCE:MANAGE"), "hash");
    }

    private static SecurityPrincipal teacherPrincipal() {
        return new SecurityPrincipal(10L, "teacher10", "Teacher Ten", List.of("TEACHER"),
                Set.of("ATTENDANCE:READ", "ATTENDANCE:MARK"), "hash");
    }

    private static StudentAttendance sampleRecord(long id, long studentId, String status, boolean corrected) {
        return new StudentAttendance(id, studentId, "ADM-001", "Jane Doe", 1L, 2L, "Class 1", 3L, "Section A",
                TEST_DATE, status, 1L, "Teacher Ten", LocalDateTime.now(), null, corrected);
    }

    private static StaffAttendance sampleStaffRecord(long id, long staffId, String status, boolean corrected) {
        return new StaffAttendance(id, staffId, "EMP-001", "John Smith", TEST_DATE, status, 1L, "Admin",
                LocalDateTime.now(), null, corrected);
    }
}
