package com.erp.portal.exam;

import com.erp.portal.exam.ExamDtos.*;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exams")
@Validated
public class ExamController {

    private final ExamService service;

    public ExamController(ExamService service) {
        this.service = service;
    }

    // =========================================================================
    // EXAMINATIONS
    // =========================================================================

    @GetMapping({"", "/"})
    @PreAuthorize("hasAnyAuthority('EXAM:READ', 'RESULT:READ')")
    public List<ExamSummaryResponse> listExams(
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String examType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.listExams(sessionId, classId, examType, status, search, actor);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('EXAM:READ', 'RESULT:READ')")
    public ExamDetailResponse getExamDetail(
            @PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.getExamDetail(id, actor);
    }

    @PostMapping({"", "/"})
    @PreAuthorize("hasAuthority('EXAM:CREATE')")
    public ResponseEntity<ExamSummaryResponse> createExam(
            @Valid @RequestBody CreateExamRequest req,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createExam(req, actor));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EXAM:UPDATE')")
    public ExamSummaryResponse updateExam(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody UpdateExamRequest req,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.updateExam(id, req, actor);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('EXAM:DELETE')")
    public ResponseEntity<Void> deleteExam(
            @PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        service.deleteExam(id, actor);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // SCHEDULES
    // =========================================================================

    @GetMapping("/{id}/schedules")
    @PreAuthorize("hasAnyAuthority('EXAM:READ', 'RESULT:READ')")
    public List<ScheduleResponse> listSchedules(
            @PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.listSchedules(id, actor);
    }

    @PostMapping("/{id}/schedules")
    @PreAuthorize("hasAuthority('EXAM:SCHEDULE')")
    public ResponseEntity<ScheduleResponse> createSchedule(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody CreateScheduleRequest req,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSchedule(id, req, actor));
    }

    @PutMapping("/schedules/{scheduleId}")
    @PreAuthorize("hasAuthority('EXAM:SCHEDULE')")
    public ScheduleResponse updateSchedule(
            @PathVariable @Min(1) long scheduleId,
            @Valid @RequestBody UpdateScheduleRequest req,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.updateSchedule(scheduleId, req, actor);
    }

    @DeleteMapping("/schedules/{scheduleId}")
    @PreAuthorize("hasAuthority('EXAM:SCHEDULE')")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable @Min(1) long scheduleId,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        service.deleteSchedule(scheduleId, actor);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // MARKS ENTRY & SUBMISSION
    // =========================================================================

    @GetMapping("/{id}/marks")
    @PreAuthorize("hasAnyAuthority('RESULT:READ', 'RESULT:CREATE', 'RESULT:UPDATE')")
    public SubjectMarksEntryResponse getSubjectMarks(
            @PathVariable @Min(1) long id,
            @RequestParam @Min(1) long sectionId,
            @RequestParam @Min(1) long subjectId,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.getSubjectMarks(id, sectionId, subjectId, actor);
    }

    @PostMapping("/{id}/marks/draft")
    @PreAuthorize("hasAnyAuthority('RESULT:CREATE', 'RESULT:UPDATE')")
    public SubjectMarksEntryResponse saveDraftMarks(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody SaveMarksRequest req,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.saveDraftMarks(id, req, actor);
    }

    @PostMapping("/{id}/marks/submit")
    @PreAuthorize("hasAuthority('RESULT:SUBMIT')")
    public SubmissionSummaryResponse submitMarks(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody SubmitMarksRequest req,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.submitMarks(id, req, actor);
    }

    @GetMapping("/submissions")
    @PreAuthorize("hasAnyAuthority('RESULT:REVIEW', 'RESULT:SUBMIT')")
    public List<SubmissionSummaryResponse> listSubmissions(
            @RequestParam(required = false) Long examId,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.listSubmissions(examId, status, actor);
    }

    @PostMapping("/submissions/{submissionId}/review")
    @PreAuthorize("hasAuthority('RESULT:REVIEW')")
    public SubmissionSummaryResponse reviewSubmission(
            @PathVariable @Min(1) long submissionId,
            @RequestParam String action,
            @RequestBody(required = false) ReviewSubmissionRequest req,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        String comments = (req != null) ? req.comments() : null;
        return service.reviewSubmission(submissionId, action, comments, actor);
    }

    // =========================================================================
    // RESULTS CALCULATION & PUBLICATION
    // =========================================================================

    @PostMapping("/{id}/calculate")
    @PreAuthorize("hasAuthority('RESULT:APPROVE')")
    public List<StudentResultResponse> calculateResults(
            @PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.calculateExamResults(id, actor);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('RESULT:PUBLISH')")
    public ExamSummaryResponse publishResults(
            @PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.publishResults(id, actor);
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAuthority('RESULT:UNPUBLISH')")
    public ExamSummaryResponse unpublishResults(
            @PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.unpublishResults(id, actor);
    }

    @PostMapping("/{id}/corrections")
    @PreAuthorize("hasAnyAuthority('RESULT:UPDATE', 'RESULT:PUBLISH')")
    public CorrectionResponse correctPublishedResult(
            @PathVariable @Min(1) long id,
            @Valid @RequestBody CorrectionRequest req,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.correctPublishedResult(id, req, actor);
    }

    @GetMapping("/{id}/corrections")
    @PreAuthorize("hasAnyAuthority('RESULT:READ', 'RESULT:REVIEW')")
    public List<CorrectionResponse> listCorrections(
            @PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.listCorrections(id, actor);
    }

    // =========================================================================
    // RESULTS & REPORT CARDS
    // =========================================================================

    @GetMapping("/{id}/results")
    @PreAuthorize("hasAnyAuthority('RESULT:READ', 'RESULT:REPORT:READ')")
    public List<StudentResultResponse> getResults(
            @PathVariable @Min(1) long id,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long studentId,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.getResults(id, sectionId, studentId, actor);
    }

    @GetMapping("/{id}/report-card")
    @PreAuthorize("hasAnyAuthority('RESULT:REPORT:READ', 'RESULT:READ')")
    public ReportCardResponse getReportCard(
            @PathVariable @Min(1) long id,
            @RequestParam @Min(1) long studentId,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.getReportCard(id, studentId, actor);
    }

    // =========================================================================
    // ANALYTICS & DASHBOARD
    // =========================================================================

    @GetMapping("/{id}/analytics")
    @PreAuthorize("hasAuthority('RESULT:READ')")
    public ExamAnalyticsSummary getExamAnalytics(
            @PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.getExamAnalytics(id, actor);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyAuthority('EXAM:READ', 'RESULT:READ')")
    public ExamDashboardOverview getDashboard(
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.getDashboardOverview(actor);
    }

    // =========================================================================
    // GRADING SCHEMES
    // =========================================================================

    @GetMapping("/grading-schemes")
    @PreAuthorize("hasAnyAuthority('EXAM:READ', 'RESULT:READ')")
    public List<GradingSchemeResponse> listGradingSchemes(
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return service.listGradingSchemes(actor);
    }

    @PostMapping("/grading-schemes")
    @PreAuthorize("hasAuthority('EXAM:CREATE')")
    public ResponseEntity<GradingSchemeResponse> createGradingScheme(
            @Valid @RequestBody CreateGradingSchemeRequest req,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createGradingScheme(req, actor));
    }
}
