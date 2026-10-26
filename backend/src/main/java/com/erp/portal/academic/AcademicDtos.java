package com.erp.portal.academic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public final class AcademicDtos {
    private AcademicDtos() { }

    public record SessionRequest(@NotBlank @Size(max = 30) String code,
                                 @NotBlank @Size(max = 100) String name,
                                 @NotNull LocalDate startDate,
                                 @NotNull LocalDate endDate,
                                 boolean active) { }
    public record SessionResponse(long id, String code, String name, LocalDate startDate,
                                  LocalDate endDate, boolean active, LocalDateTime createdAt) { }

    public record ClassRequest(@NotBlank @Size(max = 50) String name, @Min(0) int orderSeq) { }
    public record ClassResponse(long id, String name, int orderSeq, LocalDateTime createdAt) { }

    public record SectionRequest(@Positive long classId, @NotBlank @Size(max = 10) String name,
                                 @Positive Integer capacity, @Size(max = 20) String roomNo) { }
    public record SectionResponse(long id, long classId, String className, String name,
                                  Integer capacity, String roomNo, LocalDateTime createdAt) { }

    public record SubjectRequest(@NotBlank @Size(max = 30) String code,
                                 @NotBlank @Size(max = 100) String name,
                                 @Size(max = 255) String description) { }
    public record SubjectResponse(long id, String code, String name, String description, LocalDateTime createdAt) { }

    public record ClassSubjectRequest(@Positive long sessionId, @Positive long classId,
                                      @Positive long subjectId, boolean active) { }
    public record ClassSubjectResponse(long id, long sessionId, long classId, String className,
                                       long subjectId, String subjectCode, String subjectName,
                                       boolean active, LocalDateTime createdAt) { }

    public record TeacherAssignmentRequest(@Positive long sessionId, @Positive long sectionId,
                                           @Positive long subjectId, @Positive long staffId) { }
    public record TeacherAssignmentResponse(long id, long sessionId, long classId, String className,
                                            long sectionId, String sectionName, long subjectId,
                                            String subjectName, long staffId, String teacherName,
                                            String status, LocalDateTime assignedAt) { }
    public record TeacherOption(long id,String employeeNo,String name,String email) { }

    public record TimetableRequest(@Positive long sessionId, @Positive long classId,
                                   @Positive long sectionId, @Positive long subjectId,
                                   @Positive Long teacherId, @Min(1) @Max(7) int dayOfWeek,
                                   @NotNull LocalTime startTime, @NotNull LocalTime endTime,
                                   @Size(max = 20) String roomNo) { }
    public record TimetableResponse(long id, long sessionId, long classId, String className,
                                    long sectionId, String sectionName, long subjectId,
                                    String subjectName, Long teacherId, String teacherName,
                                    int dayOfWeek, LocalTime startTime, LocalTime endTime,
                                    String roomNo) { }

    public record HomeworkRequest(@Positive long sessionId, @Positive long sectionId,
                                  @Positive long subjectId, @NotBlank @Size(max = 255) String title,
                                  String description, @NotNull LocalDate dueDate) { }
    public record HomeworkResponse(long id, long sessionId, long classId, String className,
                                   long sectionId, String sectionName, long subjectId,
                                   String subjectName, long assignedBy, String teacherName,
                                   String title, String description, LocalDate dueDate,
                                   LocalDateTime createdAt) { }

    public record SyllabusRequest(@Positive long sessionId, @Positive long sectionId,
                                  @Positive long subjectId, @NotBlank @Size(max = 255) String topic,
                                  @NotBlank String status) { }
    public record SyllabusResponse(long id, long sessionId, long classId, String className,
                                   long sectionId, String sectionName, long subjectId,
                                   String subjectName, String topic, String status,
                                   LocalDateTime completedAt, long recordedBy,
                                   String actorName, LocalDateTime createdAt,
                                   LocalDateTime updatedAt) { }
}
