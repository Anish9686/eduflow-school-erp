package com.erp.portal.attendance;

import com.erp.portal.attendance.AttendanceDtos.*;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/attendance")
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    // ==========================================
    // LOOKUP OPTIONS
    // ==========================================

    @GetMapping("/options/sessions")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:READ','ATTENDANCE:MARK','ATTENDANCE:MANAGE','STAFF_ATTENDANCE:READ','STAFF_ATTENDANCE:MARK','STAFF_ATTENDANCE:MANAGE')")
    public List<AttendanceRepository.Lookup> sessions() {
        return service.sessions();
    }

    @GetMapping("/options/classes")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:READ','ATTENDANCE:MARK','ATTENDANCE:MANAGE')")
    public List<AttendanceRepository.Lookup> classes() {
        return service.classes();
    }

    @GetMapping("/options/sections")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:READ','ATTENDANCE:MARK','ATTENDANCE:MANAGE')")
    public List<AttendanceRepository.SectionLookup> sections(@RequestParam(required = false) @Min(1) Long classId) {
        return service.sections(classId);
    }

    @GetMapping("/options/staff")
    @PreAuthorize("hasAnyAuthority('STAFF_ATTENDANCE:READ','STAFF_ATTENDANCE:MARK','STAFF_ATTENDANCE:MANAGE')")
    public List<AttendanceRepository.Lookup> staff() {
        return service.staff();
    }

    // ==========================================
    // STUDENT ATTENDANCE (REQ-ATT-001..005)
    // ==========================================

    @PostMapping({"", "/students/mark"})
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:MARK','ATTENDANCE:MANAGE')")
    public MarkBatchResult markStudents(
            @Valid @RequestBody StudentMarkBatchRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        String key = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey.trim()
                : "att-" + UUID.randomUUID().toString();
        return service.markStudents(request, key, actor, servletRequest);
    }

    @GetMapping({"", "/students"})
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:READ','ATTENDANCE:MARK','ATTENDANCE:MANAGE')")
    public List<StudentAttendance> studentRecords(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Min(1) Long sessionId,
            @RequestParam(required = false) @Min(1) Long classId,
            @RequestParam(required = false) @Min(1) Long sectionId,
            @RequestParam(required = false) @Min(1) Long studentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate attendanceDate,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.studentRecords(from, to, sessionId, classId, sectionId, studentId, attendanceDate, status, actor);
    }

    @GetMapping("/roster")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:READ','ATTENDANCE:MARK','ATTENDANCE:MANAGE')")
    public List<RosterStudent> roster(
            @RequestParam @Min(1) long sessionId,
            @RequestParam @Min(1) long classId,
            @RequestParam @Min(1) long sectionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate attendanceDate,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.roster(sessionId, classId, sectionId, attendanceDate, actor);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:READ','ATTENDANCE:MARK','ATTENDANCE:MANAGE')")
    public AttendanceSummary summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate attendanceDate,
            @RequestParam(required = false) @Min(1) Long sessionId,
            @RequestParam(required = false) @Min(1) Long classId,
            @RequestParam(required = false) @Min(1) Long sectionId,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.summary(attendanceDate, sessionId, classId, sectionId, actor);
    }

    @PostMapping({"/students/{id}/corrections", "/students/{id}/correction"})
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:CORRECT','ATTENDANCE:MANAGE')")
    public StudentAttendance correctStudent(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody AttendanceCorrectionRequest request,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        return service.correctStudent(id, request, actor, servletRequest);
    }

    @PatchMapping("/students/{id}/correction")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:CORRECT','ATTENDANCE:MANAGE')")
    public StudentAttendance patchCorrectStudent(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody AttendanceCorrectionRequest request,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        return service.correctStudent(id, request, actor, servletRequest);
    }

    @GetMapping("/students/{id}/corrections")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:READ','ATTENDANCE:CORRECT','ATTENDANCE:MANAGE')")
    public List<AttendanceCorrection> studentCorrectionHistory(
            @PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.studentCorrectionHistory(id, actor);
    }

    // ==========================================
    // ROSTER & ENROLLMENT (Attendance relationship)
    // ==========================================

    @GetMapping("/enrollments")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:READ','ATTENDANCE:MARK','ATTENDANCE:MANAGE')")
    public List<Enrollment> enrollments(
            @RequestParam @Min(1) long sessionId,
            @RequestParam(required = false) @Min(1) Long classId,
            @RequestParam(required = false) @Min(1) Long sectionId,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.enrollments(sessionId, classId, sectionId, actor);
    }

    @PostMapping("/enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ATTENDANCE:MANAGE')")
    public Enrollment enroll(
            @Valid @RequestBody EnrollmentRequest request,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        return service.enroll(request, actor, servletRequest);
    }

    @GetMapping("/eligible-students")
    @PreAuthorize("hasAuthority('ATTENDANCE:MANAGE')")
    public List<EligibleStudent> eligibleStudents(
            @RequestParam @Min(1) long sessionId,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.eligibleStudents(sessionId, search, actor);
    }

    // ==========================================
    // STAFF ATTENDANCE
    // ==========================================

    @PostMapping("/staff/mark")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('STAFF_ATTENDANCE:MARK','STAFF_ATTENDANCE:MANAGE')")
    public StaffMarkBatchResult markStaff(
            @Valid @RequestBody StaffMarkBatchRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        String key = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey.trim()
                : "staff-att-" + UUID.randomUUID().toString();
        return service.markStaff(request, key, actor, servletRequest);
    }

    @GetMapping("/staff")
    @PreAuthorize("hasAnyAuthority('STAFF_ATTENDANCE:READ','STAFF_ATTENDANCE:MARK','STAFF_ATTENDANCE:MANAGE')")
    public List<StaffAttendance> staffRecords(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Min(1) Long staffId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.staffRecords(from, to, staffId, status, actor);
    }

    @PostMapping({"/staff/{id}/corrections", "/staff/{id}/correction"})
    @PreAuthorize("hasAnyAuthority('STAFF_ATTENDANCE:CORRECT','STAFF_ATTENDANCE:MANAGE')")
    public StaffAttendance correctStaff(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody StaffAttendanceCorrectionRequest request,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        return service.correctStaff(id, request, actor, servletRequest);
    }

    @PatchMapping("/staff/{id}/correction")
    @PreAuthorize("hasAnyAuthority('STAFF_ATTENDANCE:CORRECT','STAFF_ATTENDANCE:MANAGE')")
    public StaffAttendance patchCorrectStaff(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody StaffAttendanceCorrectionRequest request,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        return service.correctStaff(id, request, actor, servletRequest);
    }

    @GetMapping("/staff/{id}/corrections")
    @PreAuthorize("hasAnyAuthority('STAFF_ATTENDANCE:READ','STAFF_ATTENDANCE:MANAGE')")
    public List<StaffAttendanceCorrection> staffCorrectionHistory(
            @PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.staffCorrectionHistory(id, actor);
    }

    // ==========================================
    // DATE LOCKS
    // ==========================================

    @GetMapping("/locks")
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:MANAGE','STAFF_ATTENDANCE:MANAGE')")
    public List<DateLock> locks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.locks(from, to, actor);
    }

    @PostMapping("/locks")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:MANAGE','STAFF_ATTENDANCE:MANAGE')")
    public DateLock lock(
            @Valid @RequestBody DateLockRequest request,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        return service.lock(request, actor, servletRequest);
    }

    @DeleteMapping("/locks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('ATTENDANCE:MANAGE','STAFF_ATTENDANCE:MANAGE')")
    public void unlock(
            @PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest servletRequest) {
        service.unlock(id, actor, servletRequest);
    }
}
