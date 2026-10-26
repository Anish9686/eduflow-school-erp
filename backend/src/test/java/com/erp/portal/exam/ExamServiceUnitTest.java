package com.erp.portal.exam;

import com.erp.portal.exam.ExamDtos.*;
import com.erp.portal.security.SecurityPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamServiceUnitTest {

    @Mock ExamRepository repo;

    private ExamService service;

    private final SecurityPrincipal admin = new SecurityPrincipal(
            1L, "admin", "Admin", List.of("ADMIN"),
            Set.of("EXAM:READ", "EXAM:CREATE", "EXAM:UPDATE", "EXAM:DELETE", "EXAM:SCHEDULE"), "hash");

    private final SecurityPrincipal student = new SecurityPrincipal(
            100L, "student", "Student", List.of("STUDENT"),
            Set.of("EXAM:READ"), "hash");

    private final SecurityPrincipal teacher = new SecurityPrincipal(
            20L, "teacher", "Teacher", List.of("TEACHER"),
            Set.of("EXAM:READ", "RESULT:CREATE", "RESULT:UPDATE"), "hash");

    @BeforeEach
    void setUp() {
        service = new ExamService(repo);
    }

    @Test
    void createExamRejectsStartDateAfterEndDate() {
        CreateExamRequest req = new CreateExamRequest(
                "Final Exam", "ANNUAL", 1L, 2L, 3L,
                LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 1),
                1L, "Description");

        assertThatThrownBy(() -> service.createExam(req, admin))
                .isInstanceOf(ExamException.class)
                .matches(e -> ((ExamException) e).status() == 400)
                .hasMessageContaining("cannot be after end date");
    }

    @Test
    void nonAdminCannotCreateExam() {
        CreateExamRequest req = new CreateExamRequest(
                "Final Exam", "ANNUAL", 1L, 2L, 3L,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 10),
                1L, "Description");

        assertThatThrownBy(() -> service.createExam(req, teacher))
                .isInstanceOf(ExamException.class)
                .matches(e -> ((ExamException) e).status() == 403);
    }

    @Test
    void deleteExamRejectsWhenDependentMarksExist() {
        ExamSummaryResponse summary = new ExamSummaryResponse(
                5L, "Midterm", "TERM", 1L, "2026-27", 2L, "Class 2", 3L, "Section A",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15),
                "PLANNED", 1L, null, null, LocalDateTime.now());

        when(repo.findExamSummary(5L)).thenReturn(Optional.of(summary));
        when(repo.examHasDependentMarksOrResults(5L)).thenReturn(true);

        assertThatThrownBy(() -> service.deleteExam(5L, admin))
                .isInstanceOf(ExamException.class)
                .matches(e -> ((ExamException) e).status() == 400)
                .hasMessageContaining("Marks or published results are already associated");
    }

    @Test
    void studentCannotAccessUnpublishedExamDetail() {
        ExamDetailResponse unpublished = new ExamDetailResponse(
                5L, "Midterm", "TERM", 1L, "2026-27", 2L, "Class 2", 3L, "Section A",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15),
                "DRAFT", 1L, "Standard", "Desc", null, null, "Admin", LocalDateTime.now(), List.of(), List.of());

        when(repo.findExamDetail(5L)).thenReturn(Optional.of(unpublished));

        assertThatThrownBy(() -> service.getExamDetail(5L, student))
                .isInstanceOf(ExamException.class)
                .matches(e -> ((ExamException) e).status() == 403)
                .hasMessageContaining("results are not published yet");
    }

    @Test
    void createScheduleRejectsDateOutsideExamPeriod() {
        ExamSummaryResponse exam = new ExamSummaryResponse(
                1L, "Annual", "ANNUAL", 1L, "2026-27", 2L, "Class 2", 3L, "Section A",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20),
                "PLANNED", 1L, null, null, LocalDateTime.now());

        when(repo.findExamSummary(1L)).thenReturn(Optional.of(exam));

        CreateScheduleRequest req = new CreateScheduleRequest(
                2L, 3L, 4L,
                LocalDate.of(2026, 11, 25), // Outside exam period!
                LocalTime.of(9, 0), LocalTime.of(12, 0),
                new BigDecimal("100.00"), new BigDecimal("35.00"), "Room 101");

        assertThatThrownBy(() -> service.createSchedule(1L, req, admin))
                .isInstanceOf(ExamException.class)
                .matches(e -> ((ExamException) e).status() == 400)
                .hasMessageContaining("must be within examination period");
    }

    @Test
    void createScheduleRejectsStartTimeAfterEndTime() {
        ExamSummaryResponse exam = new ExamSummaryResponse(
                1L, "Annual", "ANNUAL", 1L, "2026-27", 2L, "Class 2", 3L, "Section A",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20),
                "PLANNED", 1L, null, null, LocalDateTime.now());

        when(repo.findExamSummary(1L)).thenReturn(Optional.of(exam));

        CreateScheduleRequest req = new CreateScheduleRequest(
                2L, 3L, 4L,
                LocalDate.of(2026, 11, 5),
                LocalTime.of(12, 0), LocalTime.of(9, 0), // End before start!
                new BigDecimal("100.00"), new BigDecimal("35.00"), "Room 101");

        assertThatThrownBy(() -> service.createSchedule(1L, req, admin))
                .isInstanceOf(ExamException.class)
                .matches(e -> ((ExamException) e).status() == 400)
                .hasMessageContaining("must precede end time");
    }

    @Test
    void createScheduleDetectsConflict() {
        ExamSummaryResponse exam = new ExamSummaryResponse(
                1L, "Annual", "ANNUAL", 1L, "2026-27", 2L, "Class 2", 3L, "Section A",
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20),
                "PLANNED", 1L, null, null, LocalDateTime.now());

        when(repo.findExamSummary(1L)).thenReturn(Optional.of(exam));
        when(repo.hasScheduleConflict(eq(1L), eq(3L), any(), any(), any(), isNull())).thenReturn(true);

        CreateScheduleRequest req = new CreateScheduleRequest(
                2L, 3L, 4L,
                LocalDate.of(2026, 11, 5),
                LocalTime.of(9, 0), LocalTime.of(12, 0),
                new BigDecimal("100.00"), new BigDecimal("35.00"), "Room 101");

        assertThatThrownBy(() -> service.createSchedule(1L, req, admin))
                .isInstanceOf(ExamException.class)
                .matches(e -> ((ExamException) e).status() == 400)
                .hasMessageContaining("Schedule conflict detected");
    }
}
