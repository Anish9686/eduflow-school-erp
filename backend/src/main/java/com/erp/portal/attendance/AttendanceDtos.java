package com.erp.portal.attendance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AttendanceDtos {
    private AttendanceDtos() { }

    public record StudentMarkBatchRequest(@NotNull @Min(1) Long sessionId,
                                         @NotNull @Min(1) Long classId,
                                         @NotNull @Min(1) Long sectionId,
                                         @NotNull LocalDate attendanceDate,
                                         @NotEmpty @Size(max=500) List<@Valid StudentMarkEntry> entries) { }
    public record StudentMarkEntry(@NotNull @Min(1) Long studentId,
                                   @NotBlank @Pattern(regexp="PRESENT|ABSENT|LATE|HALF_DAY|HOLIDAY|EXCUSED") String status,
                                   @Size(max=255) String remarks) { }
    public record AttendanceCorrectionRequest(@NotBlank @Pattern(regexp="PRESENT|ABSENT|LATE|HALF_DAY|HOLIDAY|EXCUSED") String status,
                                              @NotBlank @Size(min=4,max=500) String reason) { }
    public record StudentAttendance(long id, long studentId, String admissionNo, String studentName,
                                    long sessionId, long classId, String className, long sectionId,
                                    String sectionName, LocalDate attendanceDate, String status,
                                    long markedBy, String markerName, LocalDateTime markedAt,
                                    String remarks, boolean corrected) { }
    public record RosterStudent(long studentId, String admissionNo, String studentName, String status,
                                String remarks, Long attendanceId, boolean corrected) { }
    public record EligibleStudent(long id, String admissionNo, String name) { }
    public record EnrollmentRequest(@NotNull @Min(1) Long sessionId, @NotNull @Min(1) Long studentId,
                                    @NotNull @Min(1) Long classId, @NotNull @Min(1) Long sectionId,
                                    @Size(max=20) String rollNo) { }
    public record Enrollment(long id, long sessionId, long studentId, String admissionNo,
                             String studentName, long classId, String className, long sectionId,
                             String sectionName, String rollNo, LocalDateTime enrolledAt) { }
    public record AttendanceCorrection(long id, long attendanceId, String oldStatus, String newStatus,
                                       String reason, long correctedBy, String correctedByName,
                                       LocalDateTime correctedAt) { }

    public record StaffMarkBatchRequest(@NotNull LocalDate attendanceDate,
                                        @NotEmpty @Size(max=500) List<@Valid StaffMarkEntry> entries) { }
    public record StaffMarkEntry(@NotNull @Min(1) Long staffId,
                                 @NotBlank @Pattern(regexp="PRESENT|ABSENT|LATE|HALF_DAY|ON_LEAVE") String status,
                                 @Size(max=255) String remarks) { }
    public record StaffAttendanceCorrectionRequest(@NotBlank @Pattern(regexp="PRESENT|ABSENT|LATE|HALF_DAY|ON_LEAVE") String status,
                                                   @NotBlank @Size(min=4,max=500) String reason) { }
    public record StaffAttendance(long id, long staffId, String employeeNo, String staffName,
                                  LocalDate attendanceDate, String status, Long markedBy,
                                  String markerName, LocalDateTime markedAt, String remarks,
                                  boolean corrected) { }
    public record StaffAttendanceCorrection(long id, long attendanceId, String oldStatus,
                                            String newStatus, String reason, long correctedBy,
                                            String correctedByName, LocalDateTime correctedAt) { }

    public record DateLockRequest(@NotBlank @Pattern(regexp="STUDENT|STAFF") String scope,
                                  Long sessionId, @NotNull LocalDate attendanceDate,
                                  @NotBlank @Size(min=4,max=500) String reason) { }
    public record DateLock(long id, String scope, Long sessionId, LocalDate attendanceDate,
                           String reason, long lockedBy, String lockedByName,
                           LocalDateTime lockedAt) { }
    public record MarkBatchResult(int created, int alreadyMarked, List<StudentAttendance> records) { }
    public record StaffMarkBatchResult(int created, int alreadyMarked, List<StaffAttendance> records) { }
    public record AttendanceSummary(long total,long present,long absent,long late,long halfDay,long excused,long holiday) { }
}
