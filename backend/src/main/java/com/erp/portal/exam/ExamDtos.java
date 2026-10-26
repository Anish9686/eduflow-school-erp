package com.erp.portal.exam;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class ExamDtos {

    public record CreateExamRequest(
            @NotBlank(message = "Exam name is required") @Size(max = 150) String name,
            @NotBlank(message = "Exam type is required") String examType,
            @NotNull(message = "Session is required") @Min(1) Long sessionId,
            @NotNull(message = "Class is required") @Min(1) Long classId,
            Long sectionId,
            @NotNull(message = "Start date is required") LocalDate startDate,
            @NotNull(message = "End date is required") LocalDate endDate,
            Long gradingSchemeId,
            String instructions
    ) {}

    public record UpdateExamRequest(
            @NotBlank(message = "Exam name is required") @Size(max = 150) String name,
            @NotBlank(message = "Exam type is required") String examType,
            @NotNull(message = "Session is required") @Min(1) Long sessionId,
            @NotNull(message = "Class is required") @Min(1) Long classId,
            Long sectionId,
            @NotNull(message = "Start date is required") LocalDate startDate,
            @NotNull(message = "End date is required") LocalDate endDate,
            @NotBlank(message = "Status is required") String status,
            Long gradingSchemeId,
            String instructions
    ) {}

    public record ExamSummaryResponse(
            long id,
            String name,
            String examType,
            long sessionId,
            String sessionCode,
            long classId,
            String className,
            Long sectionId,
            String sectionName,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            Long gradingSchemeId,
            LocalDateTime publishedAt,
            Long publishedBy,
            LocalDateTime createdAt
    ) {}

    public record ExamDetailResponse(
            long id,
            String name,
            String examType,
            long sessionId,
            String sessionCode,
            long classId,
            String className,
            Long sectionId,
            String sectionName,
            LocalDate startDate,
            LocalDate endDate,
            String status,
            Long gradingSchemeId,
            String gradingSchemeName,
            String instructions,
            LocalDateTime publishedAt,
            String publishedByName,
            String createdByName,
            LocalDateTime createdAt,
            List<ScheduleResponse> schedules,
            List<SubmissionSummaryResponse> submissions
    ) {}

    public record CreateScheduleRequest(
            @NotNull @Min(1) Long classId,
            @NotNull @Min(1) Long sectionId,
            @NotNull @Min(1) Long subjectId,
            @NotNull LocalDate examDate,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull @DecimalMin("1.0") BigDecimal maxMarks,
            @NotNull @DecimalMin("0.0") BigDecimal passMarks,
            String roomNo
    ) {}

    public record UpdateScheduleRequest(
            @NotNull LocalDate examDate,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull @DecimalMin("1.0") BigDecimal maxMarks,
            @NotNull @DecimalMin("0.0") BigDecimal passMarks,
            String roomNo
    ) {}

    public record ScheduleResponse(
            long id,
            long examId,
            long classId,
            String className,
            long sectionId,
            String sectionName,
            long subjectId,
            String subjectCode,
            String subjectName,
            LocalDate examDate,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal maxMarks,
            BigDecimal passMarks,
            String roomNo
    ) {}

    public record StudentMarkInput(
            @NotNull @Min(1) Long studentId,
            BigDecimal marksObtained,
            @NotBlank String status,
            String teacherRemarks
    ) {}

    public record SaveMarksRequest(
            @NotNull @Min(1) Long sectionId,
            @NotNull @Min(1) Long subjectId,
            @NotEmpty List<StudentMarkInput> marks
    ) {}

    public record SubmitMarksRequest(
            @NotNull @Min(1) Long sectionId,
            @NotNull @Min(1) Long subjectId
    ) {}

    public record ReviewSubmissionRequest(
            String comments
    ) {}

    public record SubmissionSummaryResponse(
            long id,
            long examId,
            String examName,
            long sectionId,
            String sectionName,
            long classId,
            String className,
            long subjectId,
            String subjectName,
            long teacherId,
            String teacherName,
            String status,
            int studentCount,
            LocalDateTime submittedAt,
            LocalDateTime reviewedAt,
            String reviewedByName,
            String reviewComments
    ) {}

    public record MarkRowResponse(
            Long id,
            long studentId,
            String admissionNo,
            String rollNo,
            String studentName,
            BigDecimal marksObtained,
            BigDecimal maxMarks,
            BigDecimal passMarks,
            BigDecimal percentage,
            String grade,
            String status,
            String teacherRemarks
    ) {}

    public record SubjectMarksEntryResponse(
            long examId,
            String examName,
            long sectionId,
            String sectionName,
            long classId,
            String className,
            long subjectId,
            String subjectName,
            BigDecimal maxMarks,
            BigDecimal passMarks,
            String submissionStatus,
            String reviewComments,
            List<MarkRowResponse> marks
    ) {}

    public record CorrectionRequest(
            @NotNull @Min(1) Long studentId,
            @NotNull @Min(1) Long subjectId,
            BigDecimal marksObtained,
            @NotBlank String status,
            @NotBlank String reason
    ) {}

    public record CorrectionResponse(
            long id,
            long examId,
            long studentId,
            String studentName,
            long subjectId,
            String subjectName,
            BigDecimal previousMarks,
            BigDecimal newMarks,
            String previousStatus,
            String newStatus,
            String reason,
            String correctedByName,
            LocalDateTime correctedAt
    ) {}

    public record StudentSubjectResultDto(
            long subjectId,
            String subjectCode,
            String subjectName,
            BigDecimal marksObtained,
            BigDecimal maxMarks,
            BigDecimal passMarks,
            BigDecimal percentage,
            String grade,
            String status,
            boolean isPass,
            String teacherRemarks
    ) {}

    public record StudentResultResponse(
            long examId,
            String examName,
            String examType,
            String sessionCode,
            long studentId,
            String studentName,
            String admissionNo,
            String rollNo,
            String className,
            String sectionName,
            BigDecimal totalMaxMarks,
            BigDecimal totalMarksObtained,
            BigDecimal percentage,
            String overallGrade,
            BigDecimal gpa,
            String resultStatus,
            Integer rankInSection,
            Integer rankInClass,
            boolean isPublished,
            LocalDateTime publishedAt,
            List<StudentSubjectResultDto> subjects
    ) {}

    public record ReportCardResponse(
            String schoolName,
            String schoolAddress,
            String academicYear,
            String examinationName,
            long studentId,
            String studentName,
            String admissionNo,
            String rollNo,
            String className,
            String sectionName,
            List<StudentSubjectResultDto> subjects,
            BigDecimal totalMaxMarks,
            BigDecimal totalMarksObtained,
            BigDecimal percentage,
            String overallGrade,
            BigDecimal gpa,
            String resultStatus,
            Integer rankInSection,
            Integer rankInClass,
            BigDecimal attendancePercentage,
            LocalDateTime publishedAt,
            LocalDateTime generatedAt
    ) {}

    public record GradeDistributionDto(
            String grade,
            int count,
            BigDecimal percentage
    ) {}

    public record SubjectPerformanceDto(
            long subjectId,
            String subjectName,
            BigDecimal averageMarks,
            BigDecimal highestMarks,
            BigDecimal lowestMarks,
            BigDecimal passPercentage
    ) {}

    public record AttentionStudentDto(
            long studentId,
            String studentName,
            String admissionNo,
            BigDecimal percentage,
            String resultStatus,
            int failedSubjectCount
    ) {}

    public record ExamAnalyticsSummary(
            long examId,
            String examName,
            int totalStudents,
            int appearedStudents,
            int passedStudents,
            int failedStudents,
            BigDecimal passPercentage,
            BigDecimal averagePercentage,
            BigDecimal highestPercentage,
            BigDecimal lowestPercentage,
            List<GradeDistributionDto> gradeDistribution,
            List<SubjectPerformanceDto> subjectPerformances,
            List<AttentionStudentDto> studentsNeedingAttention
    ) {}

    public record ExamDashboardOverview(
            int totalExams,
            int upcomingExams,
            int ongoingExams,
            int pendingSubmissionsCount,
            int awaitingReviewCount,
            int publishedExamsCount,
            List<ExamSummaryResponse> recentExams,
            List<SubmissionSummaryResponse> actionRequiredSubmissions
    ) {}

    public record GradingScaleRuleDto(
            Long id,
            String grade,
            BigDecimal minPercentage,
            BigDecimal maxPercentage,
            BigDecimal gradePoint,
            boolean isPass,
            String remarks,
            int orderSeq
    ) {}

    public record GradingSchemeResponse(
            long id,
            String name,
            String description,
            boolean isDefault,
            List<GradingScaleRuleDto> rules
    ) {}

    public record CreateGradingSchemeRequest(
            @NotBlank String name,
            String description,
            @NotEmpty List<GradingScaleRuleDto> rules
    ) {}
}
