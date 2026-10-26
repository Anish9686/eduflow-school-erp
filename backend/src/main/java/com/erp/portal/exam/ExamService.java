package com.erp.portal.exam;

import com.erp.portal.exam.ExamDtos.*;
import com.erp.portal.security.SecurityPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
@Transactional
public class ExamService {

    private final ExamRepository repo;

    public ExamService(ExamRepository repo) {
        this.repo = repo;
    }

    // =========================================================================
    // EXAMINATIONS
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ExamSummaryResponse> listExams(Long sessionId, Long classId, String examType, String status, String search, SecurityPrincipal actor) {
        verifyActor(actor);
        boolean isStudent = hasRole(actor, "STUDENT");
        boolean isParent = hasRole(actor, "PARENT", "PARENT_GUARDIAN");

        if (isStudent || isParent) {
            // Students and parents must only see published exams
            status = "PUBLISHED";
        }

        return repo.listExams(sessionId, classId, examType, status, search);
    }

    @Transactional(readOnly = true)
    public ExamDetailResponse getExamDetail(long examId, SecurityPrincipal actor) {
        verifyActor(actor);
        ExamDetailResponse exam = repo.findExamDetail(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));

        boolean isStudent = hasRole(actor, "STUDENT");
        boolean isParent = hasRole(actor, "PARENT", "PARENT_GUARDIAN");

        if ((isStudent || isParent) && !"PUBLISHED".equalsIgnoreCase(exam.status())) {
            throw ExamException.forbidden("Access denied: Examination results are not published yet.");
        }

        return exam;
    }

    public ExamSummaryResponse createExam(CreateExamRequest req, SecurityPrincipal actor) {
        verifyAdminOrSuperAdmin(actor);

        if (req.startDate().isAfter(req.endDate())) {
            throw ExamException.badRequest("Examination start date (" + req.startDate() + ") cannot be after end date (" + req.endDate() + ").");
        }

        long examId = repo.createExam(req, actor.userId());
        return repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Created examination could not be loaded"));
    }

    public ExamSummaryResponse updateExam(long examId, UpdateExamRequest req, SecurityPrincipal actor) {
        verifyAdminOrSuperAdmin(actor);

        repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));

        if (req.startDate().isAfter(req.endDate())) {
            throw ExamException.badRequest("Examination start date cannot be after end date.");
        }

        repo.updateExam(examId, req);
        return repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Updated examination could not be loaded"));
    }

    public void deleteExam(long examId, SecurityPrincipal actor) {
        verifyAdminOrSuperAdmin(actor);

        repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));

        if (repo.examHasDependentMarksOrResults(examId)) {
            throw ExamException.badRequest("Cannot delete examination: Marks or published results are already associated with this examination.");
        }

        repo.deleteExam(examId);
    }

    // =========================================================================
    // SCHEDULES
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ScheduleResponse> listSchedules(long examId, SecurityPrincipal actor) {
        verifyActor(actor);
        repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));
        return repo.listSchedules(examId);
    }

    public ScheduleResponse createSchedule(long examId, CreateScheduleRequest req, SecurityPrincipal actor) {
        verifyAdminOrSuperAdmin(actor);

        ExamSummaryResponse exam = repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));

        validateScheduleDatesAndTimes(exam, req.examDate(), req.startTime(), req.endTime(), req.maxMarks(), req.passMarks());

        if (repo.hasScheduleConflict(examId, req.sectionId(), req.examDate(), req.startTime(), req.endTime(), null)) {
            throw ExamException.badRequest("Schedule conflict detected: Another examination is already scheduled for this section during this time slot.");
        }

        long id = repo.createSchedule(examId, req);
        return repo.findSchedule(id)
                .orElseThrow(() -> ExamException.notFound("Created schedule could not be loaded"));
    }

    public ScheduleResponse updateSchedule(long scheduleId, UpdateScheduleRequest req, SecurityPrincipal actor) {
        verifyAdminOrSuperAdmin(actor);

        ScheduleResponse existing = repo.findSchedule(scheduleId)
                .orElseThrow(() -> ExamException.notFound("Schedule not found with ID: " + scheduleId));

        ExamSummaryResponse exam = repo.findExamSummary(existing.examId())
                .orElseThrow(() -> ExamException.notFound("Examination not found"));

        validateScheduleDatesAndTimes(exam, req.examDate(), req.startTime(), req.endTime(), req.maxMarks(), req.passMarks());

        if (repo.hasScheduleConflict(existing.examId(), existing.sectionId(), req.examDate(), req.startTime(), req.endTime(), scheduleId)) {
            throw ExamException.badRequest("Schedule conflict detected: Another examination is already scheduled for this section during this time slot.");
        }

        repo.updateSchedule(scheduleId, req);
        return repo.findSchedule(scheduleId)
                .orElseThrow(() -> ExamException.notFound("Updated schedule could not be loaded"));
    }

    public void deleteSchedule(long scheduleId, SecurityPrincipal actor) {
        verifyAdminOrSuperAdmin(actor);
        repo.findSchedule(scheduleId)
                .orElseThrow(() -> ExamException.notFound("Schedule not found with ID: " + scheduleId));
        repo.deleteSchedule(scheduleId);
    }

    private void validateScheduleDatesAndTimes(ExamSummaryResponse exam, LocalDate date, LocalTime start, LocalTime end, BigDecimal maxMarks, BigDecimal passMarks) {
        if (date.isBefore(exam.startDate()) || date.isAfter(exam.endDate())) {
            throw ExamException.badRequest("Scheduled date (" + date + ") must be within examination period (" + exam.startDate() + " to " + exam.endDate() + ").");
        }
        if (!start.isBefore(end)) {
            throw ExamException.badRequest("Examination start time (" + start + ") must precede end time (" + end + ").");
        }
        if (maxMarks == null || maxMarks.compareTo(BigDecimal.ZERO) <= 0) {
            throw ExamException.badRequest("Maximum marks must be greater than zero.");
        }
        if (passMarks == null || passMarks.compareTo(BigDecimal.ZERO) < 0 || passMarks.compareTo(maxMarks) > 0) {
            throw ExamException.badRequest("Passing marks (" + passMarks + ") must be between 0 and maximum marks (" + maxMarks + ").");
        }
    }

    // =========================================================================
    // MARKS ENTRY & SUBMISSION WORKFLOW
    // =========================================================================

    @Transactional(readOnly = true)
    public SubjectMarksEntryResponse getSubjectMarks(long examId, long sectionId, long subjectId, SecurityPrincipal actor) {
        verifyActor(actor);

        ExamSummaryResponse exam = repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));

        // Authorization check for teachers
        if (hasRole(actor, "TEACHER") && !isAdminOrSuperAdmin(actor)) {
            Long staffId = repo.findStaffIdForUser(actor.userId());
            if (staffId == null || !repo.isTeacherAssignedToSectionSubject(staffId, exam.sessionId(), sectionId, subjectId)) {
                throw ExamException.forbidden("Access denied: You are not assigned to teach this subject in this section.");
            }
        } else if (hasRole(actor, "STUDENT", "PARENT", "PARENT_GUARDIAN")) {
            throw ExamException.forbidden("Access denied: Marks entry is restricted to teachers and administrators.");
        }

        ScheduleResponse schedule = repo.findScheduleForSubject(examId, sectionId, subjectId)
                .orElseThrow(() -> ExamException.notFound("No examination schedule found for section ID " + sectionId + " and subject ID " + subjectId));

        List<MarkRowResponse> marks = repo.findExistingMarks(examId, schedule.id());
        if (marks.isEmpty()) {
            // Populate with enrolled students as initial empty rows
            List<ExamRepository.EnrolledStudent> students = repo.findEligibleStudentsForSection(exam.sessionId(), sectionId);
            marks = students.stream().map(s -> new MarkRowResponse(
                    null,
                    s.studentId(),
                    s.admissionNo(),
                    s.rollNo(),
                    s.studentName(),
                    null,
                    schedule.maxMarks(),
                    schedule.passMarks(),
                    null,
                    null,
                    "PRESENT",
                    null
            )).toList();
        }

        Optional<SubmissionSummaryResponse> sub = repo.findSubmission(examId, sectionId, subjectId);
        String subStatus = sub.map(SubmissionSummaryResponse::status).orElse("DRAFT");
        String comments = sub.map(SubmissionSummaryResponse::reviewComments).orElse(null);

        return new SubjectMarksEntryResponse(
                examId,
                exam.name(),
                sectionId,
                schedule.sectionName(),
                schedule.classId(),
                schedule.className(),
                subjectId,
                schedule.subjectName(),
                schedule.maxMarks(),
                schedule.passMarks(),
                subStatus,
                comments,
                marks
        );
    }

    public SubjectMarksEntryResponse saveDraftMarks(long examId, SaveMarksRequest req, SecurityPrincipal actor) {
        verifyActor(actor);

        ExamSummaryResponse exam = repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));

        Long teacherStaffId = null;
        if (hasRole(actor, "TEACHER") && !isAdminOrSuperAdmin(actor)) {
            teacherStaffId = repo.findStaffIdForUser(actor.userId());
            if (teacherStaffId == null || !repo.isTeacherAssignedToSectionSubject(teacherStaffId, exam.sessionId(), req.sectionId(), req.subjectId())) {
                throw ExamException.forbidden("Access denied: You are not assigned to teach this subject in this section.");
            }
        } else if (!isAdminOrSuperAdmin(actor)) {
            throw ExamException.forbidden("Access denied: Only assigned teachers or administrators can save marks.");
        }

        ScheduleResponse schedule = repo.findScheduleForSubject(examId, req.sectionId(), req.subjectId())
                .orElseThrow(() -> ExamException.notFound("No examination schedule found for section ID " + req.sectionId() + " and subject ID " + req.subjectId()));

        Optional<SubmissionSummaryResponse> sub = repo.findSubmission(examId, req.sectionId(), req.subjectId());
        if (sub.isPresent()) {
            String currentStatus = sub.get().status();
            if ("SUBMITTED".equalsIgnoreCase(currentStatus) || "APPROVED".equalsIgnoreCase(currentStatus)) {
                throw ExamException.badRequest("Cannot modify marks while submission is " + currentStatus + ". Administrative return/rejection is required first.");
            }
        }

        // Fetch grading rules for automatic grade calculation on each mark
        List<GradingScaleRuleDto> rules = repo.findGradingRules(exam.gradingSchemeId() != null ? exam.gradingSchemeId() : 1L);

        for (StudentMarkInput mark : req.marks()) {
            validateMark(mark, schedule.maxMarks());

            BigDecimal pct = null;
            String grade = null;
            if (mark.marksObtained() != null && "PRESENT".equalsIgnoreCase(mark.status())) {
                pct = mark.marksObtained().multiply(BigDecimal.valueOf(100))
                        .divide(schedule.maxMarks(), 2, RoundingMode.HALF_UP);
                grade = determineGrade(rules, pct);
            }

            repo.upsertStudentMark(
                    examId,
                    schedule.id(),
                    mark.studentId(),
                    req.subjectId(),
                    mark.marksObtained(),
                    schedule.maxMarks(),
                    schedule.passMarks(),
                    pct,
                    grade,
                    mark.status() != null ? mark.status().toUpperCase() : "PRESENT",
                    mark.teacherRemarks(),
                    actor.userId()
            );
        }

        if (teacherStaffId == null) {
            teacherStaffId = repo.findStaffIdForUser(actor.userId());
            if (teacherStaffId == null) teacherStaffId = 1L; // Fallback to system admin staff
        }

        repo.upsertSubmissionState(examId, req.sectionId(), req.subjectId(), teacherStaffId, "DRAFT", actor.userId());

        return getSubjectMarks(examId, req.sectionId(), req.subjectId(), actor);
    }

    public SubmissionSummaryResponse submitMarks(long examId, SubmitMarksRequest req, SecurityPrincipal actor) {
        verifyActor(actor);

        ExamSummaryResponse exam = repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));

        Long teacherStaffId = null;
        if (hasRole(actor, "TEACHER") && !isAdminOrSuperAdmin(actor)) {
            teacherStaffId = repo.findStaffIdForUser(actor.userId());
            if (teacherStaffId == null || !repo.isTeacherAssignedToSectionSubject(teacherStaffId, exam.sessionId(), req.sectionId(), req.subjectId())) {
                throw ExamException.forbidden("Access denied: You are not assigned to teach this subject in this section.");
            }
        } else if (!isAdminOrSuperAdmin(actor)) {
            throw ExamException.forbidden("Access denied: Only assigned teachers or administrators can submit marks.");
        }

        ScheduleResponse schedule = repo.findScheduleForSubject(examId, req.sectionId(), req.subjectId())
                .orElseThrow(() -> ExamException.notFound("No examination schedule found for section ID " + req.sectionId() + " and subject ID " + req.subjectId()));

        List<ExamRepository.EnrolledStudent> eligible = repo.findEligibleStudentsForSection(exam.sessionId(), req.sectionId());
        List<MarkRowResponse> enteredMarks = repo.findExistingMarks(examId, schedule.id());

        if (enteredMarks.isEmpty() || enteredMarks.size() < eligible.size()) {
            throw ExamException.badRequest("Cannot submit marks: All eligible students in the section must have marks recorded before submission.");
        }

        for (MarkRowResponse m : enteredMarks) {
            if ("PRESENT".equalsIgnoreCase(m.status()) && m.marksObtained() == null) {
                throw ExamException.badRequest("Cannot submit marks: Student " + m.studentName() + " is marked PRESENT but has no marks entered.");
            }
        }

        if (teacherStaffId == null) {
            teacherStaffId = repo.findStaffIdForUser(actor.userId());
            if (teacherStaffId == null) teacherStaffId = 1L;
        }

        repo.upsertSubmissionState(examId, req.sectionId(), req.subjectId(), teacherStaffId, "SUBMITTED", actor.userId());

        return repo.findSubmission(examId, req.sectionId(), req.subjectId())
                .orElseThrow(() -> ExamException.notFound("Submission record could not be loaded"));
    }

    @Transactional(readOnly = true)
    public List<SubmissionSummaryResponse> listSubmissions(Long examId, String status, SecurityPrincipal actor) {
        verifyActor(actor);

        Long teacherStaffId = null;
        if (hasRole(actor, "TEACHER") && !isAdminOrSuperAdmin(actor)) {
            teacherStaffId = repo.findStaffIdForUser(actor.userId());
            if (teacherStaffId == null) {
                return Collections.emptyList();
            }
        }

        return repo.listSubmissions(examId, status, teacherStaffId);
    }

    public SubmissionSummaryResponse reviewSubmission(long submissionId, String action, String comments, SecurityPrincipal actor) {
        verifyAdminOrSuperAdmin(actor);

        SubmissionSummaryResponse sub = repo.findSubmissionById(submissionId)
                .orElseThrow(() -> ExamException.notFound("Submission not found with ID: " + submissionId));

        // STRICT SECURITY REQUIREMENT: Prevent teachers from approving their own submissions
        Long actorStaffId = repo.findStaffIdForUser(actor.userId());
        if (actorStaffId != null && actorStaffId.equals(sub.teacherId())) {
            throw ExamException.forbidden("Security violation: Teachers are strictly prohibited from reviewing or approving their own submissions.");
        }

        String newStatus;
        if ("APPROVE".equalsIgnoreCase(action)) {
            newStatus = "APPROVED";
        } else if ("REJECT".equalsIgnoreCase(action) || "NEEDS_CORRECTION".equalsIgnoreCase(action)) {
            newStatus = "REJECTED";
            if (comments == null || comments.isBlank()) {
                throw ExamException.badRequest("Rejection reason or review comments are required when returning marks for correction.");
            }
        } else {
            throw ExamException.badRequest("Invalid review action. Allowed actions are APPROVE or REJECT.");
        }

        repo.updateSubmissionReview(submissionId, newStatus, actor.userId(), comments);

        return repo.findSubmissionById(submissionId)
                .orElseThrow(() -> ExamException.notFound("Updated submission could not be loaded"));
    }

    // =========================================================================
    // RESULTS CALCULATION & PUBLICATION
    // =========================================================================

    public List<StudentResultResponse> calculateExamResults(long examId, SecurityPrincipal actor) {
        verifyAdminOrSuperAdmin(actor);

        ExamSummaryResponse exam = repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));

        List<ScheduleResponse> schedules = repo.listSchedules(examId);
        if (schedules.isEmpty()) {
            throw ExamException.badRequest("Cannot calculate results: No subjects are scheduled for this examination.");
        }

        List<GradingScaleRuleDto> rules = repo.findGradingRules(exam.gradingSchemeId() != null ? exam.gradingSchemeId() : 1L);
        List<Long> studentIds = repo.findDistinctStudentIdsWithMarks(examId);

        if (studentIds.isEmpty()) {
            throw ExamException.badRequest("Cannot calculate results: No marks have been entered for this examination.");
        }

        record StudentCalc(
                long studentId,
                BigDecimal totalMax,
                BigDecimal totalObtained,
                BigDecimal percentage,
                String grade,
                BigDecimal gpa,
                String status,
                Long sectionId
        ) {}

        List<StudentCalc> calcs = new ArrayList<>();

        for (Long studentId : studentIds) {
            List<StudentSubjectResultDto> subMarks = repo.findSubjectResultsForStudent(examId, studentId);

            BigDecimal totalMax = BigDecimal.ZERO;
            BigDecimal totalObtained = BigDecimal.ZERO;
            boolean failedAny = false;

            for (StudentSubjectResultDto sm : subMarks) {
                totalMax = totalMax.add(sm.maxMarks());
                if ("PRESENT".equalsIgnoreCase(sm.status()) && sm.marksObtained() != null) {
                    totalObtained = totalObtained.add(sm.marksObtained());
                    if (sm.marksObtained().compareTo(sm.passMarks()) < 0) {
                        failedAny = true;
                    }
                } else {
                    failedAny = true; // Absent or missing counts as fail
                }
            }

            BigDecimal pct = (totalMax.compareTo(BigDecimal.ZERO) > 0)
                    ? totalObtained.multiply(BigDecimal.valueOf(100)).divide(totalMax, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            String grade = determineGrade(rules, pct);
            BigDecimal gpa = determineGpa(rules, pct);
            String resultStatus = failedAny ? "FAIL" : "PASS";
            Long secId = repo.getStudentSectionId(exam.sessionId(), studentId);

            calcs.add(new StudentCalc(studentId, totalMax, totalObtained, pct, grade, gpa, resultStatus, secId));
        }

        // Sort by percentage DESC, totalObtained DESC for ranking
        calcs.sort((a, b) -> {
            int c = b.percentage().compareTo(a.percentage());
            if (c != 0) return c;
            return b.totalObtained().compareTo(a.totalObtained());
        });

        // Compute class and section ranks
        Map<Long, Integer> sectionRankMap = new HashMap<>();
        for (int i = 0; i < calcs.size(); i++) {
            StudentCalc s = calcs.get(i);
            int classRank = i + 1;
            int secRank = sectionRankMap.getOrDefault(s.sectionId(), 0) + 1;
            if (s.sectionId() != null) {
                sectionRankMap.put(s.sectionId(), secRank);
            }

            repo.upsertOverallResult(
                    examId,
                    s.studentId(),
                    s.totalMax(),
                    s.totalObtained(),
                    s.percentage(),
                    s.grade(),
                    s.gpa(),
                    s.status(),
                    secRank,
                    classRank,
                    false
            );
        }

        return repo.findCalculatedResults(examId, null, null, false);
    }

    public ExamSummaryResponse publishResults(long examId, SecurityPrincipal actor) {
        verifyAdminOrSuperAdmin(actor);

        ExamSummaryResponse exam = repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));

        int totalSchedules = repo.countSchedules(examId);
        if (totalSchedules == 0) {
            throw ExamException.badRequest("Cannot publish results: Examination has no scheduled subjects.");
        }

        int approvedSubmissions = repo.countApprovedSubmissions(examId);
        int pendingSubmissions = repo.countPendingSubmissions(examId);

        if (pendingSubmissions > 0 || approvedSubmissions < totalSchedules) {
            throw ExamException.badRequest("Cannot publish results: All scheduled subjects must have approved marks submissions. Approved: " +
                    approvedSubmissions + " / " + totalSchedules + ".");
        }

        // Ensure calculated results exist and are updated
        calculateExamResults(examId, actor);

        repo.publishExamResults(examId, actor.userId());

        return repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Published examination could not be loaded"));
    }

    public ExamSummaryResponse unpublishResults(long examId, SecurityPrincipal actor) {
        verifyAdminOrSuperAdmin(actor);

        repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));

        repo.unpublishExamResults(examId);

        return repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Unpublished examination could not be loaded"));
    }

    public CorrectionResponse correctPublishedResult(long examId, CorrectionRequest req, SecurityPrincipal actor) {
        verifyAdminOrSuperAdmin(actor);

        ExamSummaryResponse exam = repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));

        MarkRowResponse prevMark = repo.findStudentMark(examId, req.studentId(), req.subjectId())
                .orElseThrow(() -> ExamException.notFound("Existing mark record not found for student ID " + req.studentId() + " and subject ID " + req.subjectId()));

        ScheduleResponse schedule = repo.findScheduleForSubject(examId, repo.getStudentSectionId(exam.sessionId(), req.studentId()), req.subjectId())
                .orElseThrow(() -> ExamException.notFound("Schedule not found"));

        validateMark(new StudentMarkInput(req.studentId(), req.marksObtained(), req.status(), null), schedule.maxMarks());

        // Record audit correction
        long corrId = repo.recordCorrection(
                examId,
                req.studentId(),
                req.subjectId(),
                prevMark.marksObtained(),
                req.marksObtained(),
                prevMark.status(),
                req.status().toUpperCase(),
                req.reason().trim(),
                actor.userId()
        );

        // Update student mark
        BigDecimal pct = null;
        String grade = null;
        if (req.marksObtained() != null && "PRESENT".equalsIgnoreCase(req.status())) {
            pct = req.marksObtained().multiply(BigDecimal.valueOf(100)).divide(schedule.maxMarks(), 2, RoundingMode.HALF_UP);
            List<GradingScaleRuleDto> rules = repo.findGradingRules(exam.gradingSchemeId() != null ? exam.gradingSchemeId() : 1L);
            grade = determineGrade(rules, pct);
        }

        repo.upsertStudentMark(
                examId,
                schedule.id(),
                req.studentId(),
                req.subjectId(),
                req.marksObtained(),
                schedule.maxMarks(),
                schedule.passMarks(),
                pct,
                grade,
                req.status().toUpperCase(),
                "Correction: " + req.reason().trim(),
                actor.userId()
        );

        // Recalculate results
        calculateExamResults(examId, actor);
        if ("PUBLISHED".equalsIgnoreCase(exam.status())) {
            repo.publishExamResults(examId, actor.userId());
        }

        List<CorrectionResponse> list = repo.listCorrections(examId);
        return list.stream().filter(c -> c.id() == corrId).findFirst()
                .orElse(new CorrectionResponse(
                        corrId, examId, req.studentId(), prevMark.studentName(),
                        req.subjectId(), "", prevMark.marksObtained(), req.marksObtained(),
                        prevMark.status(), req.status().toUpperCase(), req.reason(), actor.fullName(), LocalDateTime.now()
                ));
    }

    @Transactional(readOnly = true)
    public List<CorrectionResponse> listCorrections(long examId, SecurityPrincipal actor) {
        verifyAdminOrSuperAdmin(actor);
        return repo.listCorrections(examId);
    }

    // =========================================================================
    // STUDENT & PARENT RESULTS / REPORT CARDS
    // =========================================================================

    @Transactional(readOnly = true)
    public List<StudentResultResponse> getResults(long examId, Long sectionId, Long requestedStudentId, SecurityPrincipal actor) {
        verifyActor(actor);

        ExamSummaryResponse exam = repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));

        boolean isStudent = hasRole(actor, "STUDENT");
        boolean isParent = hasRole(actor, "PARENT", "PARENT_GUARDIAN");

        if (isStudent) {
            Long actorStudentId = repo.findStudentIdForUser(actor.userId());
            if (actorStudentId == null) {
                throw ExamException.forbidden("No student profile linked to your user account.");
            }
            if (requestedStudentId != null && !requestedStudentId.equals(actorStudentId)) {
                throw ExamException.forbidden("IDOR Violation: Students are strictly prohibited from viewing results of other students.");
            }
            requestedStudentId = actorStudentId;
        } else if (isParent) {
            List<Long> linked = repo.findLinkedStudentIdsForGuardian(actor.userId());
            if (linked.isEmpty()) {
                throw ExamException.forbidden("No children are linked to your guardian account.");
            }
            if (requestedStudentId != null) {
                if (!linked.contains(requestedStudentId)) {
                    throw ExamException.forbidden("Access denied: You are not authorized to view results for this student.");
                }
            } else {
                requestedStudentId = linked.getFirst();
            }
        }

        Boolean onlyPublished = (isStudent || isParent || hasRole(actor, "MANAGEMENT")) ? true : null;

        return repo.findCalculatedResults(examId, sectionId, requestedStudentId, onlyPublished);
    }

    @Transactional(readOnly = true)
    public ReportCardResponse getReportCard(long examId, long studentId, SecurityPrincipal actor) {
        verifyActor(actor);

        ExamSummaryResponse exam = repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));

        boolean isStudent = hasRole(actor, "STUDENT");
        boolean isParent = hasRole(actor, "PARENT", "PARENT_GUARDIAN");

        if (isStudent) {
            Long actorStudentId = repo.findStudentIdForUser(actor.userId());
            if (actorStudentId == null || !actorStudentId.equals(studentId)) {
                throw ExamException.forbidden("IDOR Violation: Students are strictly prohibited from accessing another student's report card.");
            }
        } else if (isParent) {
            List<Long> linked = repo.findLinkedStudentIdsForGuardian(actor.userId());
            if (!linked.contains(studentId)) {
                throw ExamException.forbidden("Access denied: You are not authorized to view report cards for this student.");
            }
        }

        if ((isStudent || isParent) && !"PUBLISHED".equalsIgnoreCase(exam.status())) {
            throw ExamException.forbidden("Report card is not available: Examination results have not been published.");
        }

        List<StudentResultResponse> results = repo.findCalculatedResults(examId, null, studentId, isStudent || isParent ? true : null);
        if (results.isEmpty()) {
            throw ExamException.notFound("No result record found for student ID " + studentId + " in examination " + examId);
        }

        StudentResultResponse r = results.getFirst();
        Map<String, String> inst = repo.getInstitutionDetails();
        BigDecimal attendancePct = repo.getStudentAttendancePercentage(exam.sessionId(), studentId);

        return new ReportCardResponse(
                inst.getOrDefault("name", "EduFlow Academy"),
                inst.getOrDefault("address", "123 Education Blvd, Knowledge City"),
                r.sessionCode(),
                r.examName(),
                r.studentId(),
                r.studentName(),
                r.admissionNo(),
                r.rollNo(),
                r.className(),
                r.sectionName(),
                r.subjects(),
                r.totalMaxMarks(),
                r.totalMarksObtained(),
                r.percentage(),
                r.overallGrade(),
                r.gpa(),
                r.resultStatus(),
                r.rankInSection(),
                r.rankInClass(),
                attendancePct,
                r.publishedAt(),
                LocalDateTime.now()
        );
    }

    // =========================================================================
    // ANALYTICS & DASHBOARD
    // =========================================================================

    @Transactional(readOnly = true)
    public ExamAnalyticsSummary getExamAnalytics(long examId, SecurityPrincipal actor) {
        verifyActor(actor);

        if (hasRole(actor, "STUDENT", "PARENT", "PARENT_GUARDIAN")) {
            throw ExamException.forbidden("Access denied: Students and parents cannot access academic examination analytics.");
        }

        if (hasRole(actor, "TEACHER") && !isAdminOrSuperAdmin(actor)) {
            Long staffId = repo.findStaffIdForUser(actor.userId());
            if (staffId == null || !repo.isTeacherAssignedToExam(staffId, examId)) {
                throw ExamException.forbidden("Access denied: Teachers can only view analytics for their assigned examinations.");
            }
        }

        repo.findExamSummary(examId)
                .orElseThrow(() -> ExamException.notFound("Examination not found with ID: " + examId));

        return repo.getExamAnalytics(examId);
    }

    @Transactional(readOnly = true)
    public ExamDashboardOverview getDashboardOverview(SecurityPrincipal actor) {
        verifyActor(actor);

        Long teacherStaffId = null;
        if (hasRole(actor, "TEACHER") && !isAdminOrSuperAdmin(actor)) {
            teacherStaffId = repo.findStaffIdForUser(actor.userId());
        }

        return repo.getDashboardOverview(teacherStaffId);
    }

    // =========================================================================
    // GRADING SCHEMES
    // =========================================================================

    @Transactional(readOnly = true)
    public List<GradingSchemeResponse> listGradingSchemes(SecurityPrincipal actor) {
        verifyActor(actor);
        return repo.listGradingSchemes();
    }

    public GradingSchemeResponse createGradingScheme(CreateGradingSchemeRequest req, SecurityPrincipal actor) {
        verifyAdminOrSuperAdmin(actor);
        long id = repo.createGradingScheme(req);
        return repo.listGradingSchemes().stream().filter(g -> g.id() == id).findFirst()
                .orElseThrow(() -> ExamException.notFound("Created grading scheme could not be found"));
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private void validateMark(StudentMarkInput m, BigDecimal maxMarks) {
        if ("PRESENT".equalsIgnoreCase(m.status())) {
            if (m.marksObtained() == null) {
                throw ExamException.badRequest("Marks obtained cannot be null for student with status PRESENT (Student ID: " + m.studentId() + ").");
            }
            if (m.marksObtained().compareTo(BigDecimal.ZERO) < 0) {
                throw ExamException.badRequest("Marks obtained cannot be negative (Student ID: " + m.studentId() + ").");
            }
            if (m.marksObtained().compareTo(maxMarks) > 0) {
                throw ExamException.badRequest("Marks obtained (" + m.marksObtained() + ") exceeds maximum marks (" + maxMarks + ") for student ID " + m.studentId() + ".");
            }
        }
    }

    private String determineGrade(List<GradingScaleRuleDto> rules, BigDecimal pct) {
        if (pct == null) return "-";
        for (GradingScaleRuleDto r : rules) {
            if (pct.compareTo(r.minPercentage()) >= 0 && pct.compareTo(r.maxPercentage()) <= 0) {
                return r.grade();
            }
        }
        return "F";
    }

    private BigDecimal determineGpa(List<GradingScaleRuleDto> rules, BigDecimal pct) {
        if (pct == null) return BigDecimal.ZERO;
        for (GradingScaleRuleDto r : rules) {
            if (pct.compareTo(r.minPercentage()) >= 0 && pct.compareTo(r.maxPercentage()) <= 0) {
                return r.gradePoint();
            }
        }
        return BigDecimal.ZERO;
    }

    private void verifyActor(SecurityPrincipal actor) {
        if (actor == null) {
            throw ExamException.forbidden("Authentication is required.");
        }
    }

    private void verifyAdminOrSuperAdmin(SecurityPrincipal actor) {
        verifyActor(actor);
        if (!isAdminOrSuperAdmin(actor)) {
            throw ExamException.forbidden("Administrative privileges are required for this action.");
        }
    }

    private boolean isAdminOrSuperAdmin(SecurityPrincipal actor) {
        return hasRole(actor, "SUPER_ADMIN", "ADMIN");
    }

    private boolean hasRole(SecurityPrincipal actor, String... roles) {
        if (actor == null || actor.roles() == null) return false;
        for (String r : roles) {
            if (actor.roles().contains(r)) return true;
        }
        return false;
    }
}
