package com.erp.portal.exam;

import com.erp.portal.common.api.GlobalExceptionHandler;
import com.erp.portal.exam.ExamDtos.*;
import com.erp.portal.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExamController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ExamExceptionHandler.class, GlobalExceptionHandler.class})
class ExamControllerApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ExamService examService;

    private UsernamePasswordAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        var principal = new SecurityPrincipal(
                1L, "admin", "Admin",
                List.of("SUPER_ADMIN"),
                Set.of("EXAM:READ", "EXAM:CREATE", "EXAM:UPDATE", "EXAM:DELETE", "EXAM:SCHEDULE", "MARKS:ENTER", "EXAM:PUBLISH"),
                "token"
        );
        authToken = new UsernamePasswordAuthenticationToken(principal, null, List.of(
                new SimpleGrantedAuthority("EXAM:READ"),
                new SimpleGrantedAuthority("EXAM:CREATE"),
                new SimpleGrantedAuthority("EXAM:SCHEDULE"),
                new SimpleGrantedAuthority("MARKS:ENTER"),
                new SimpleGrantedAuthority("EXAM:PUBLISH")
        ));
    }

    @Test
    @DisplayName("GET /api/v1/exams - list exams with query filters")
    void listExamsSuccess() throws Exception {
        var summary = new ExamSummaryResponse(
                1L, "Midterm Exam 2026", "MIDTERM", 1L, "2026-27", 1L, "Grade 10", 10L, "Section A",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), "SCHEDULED",
                null, null, null, LocalDateTime.now()
        );
        when(examService.listExams(eq(1L), eq(1L), eq("MIDTERM"), eq("SCHEDULED"), isNull(), any()))
                .thenReturn(List.of(summary));

        mockMvc.perform(get("/api/v1/exams")
                        .with(authentication(authToken))
                        .param("sessionId", "1")
                        .param("classId", "1")
                        .param("examType", "MIDTERM")
                        .param("status", "SCHEDULED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Midterm Exam 2026"))
                .andExpect(jsonPath("$[0].examType").value("MIDTERM"));
    }

    @Test
    @DisplayName("POST /api/v1/exams - create exam returns 201 Created")
    void createExamSuccess() throws Exception {
        var req = new CreateExamRequest(
                "Final Exam 2027", "FINAL", 1L, 1L, 10L,
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 15),
                null, "Standard exam rules apply"
        );
        var res = new ExamSummaryResponse(
                5L, "Final Exam 2027", "FINAL", 1L, "2026-27", 1L, "Grade 10", 10L, "Section A",
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 15), "DRAFT",
                null, null, null, LocalDateTime.now()
        );
        when(examService.createExam(any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/exams")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Final Exam 2027"));
    }

    @Test
    @DisplayName("POST /api/v1/exams - invalid start date after end date returns 400")
    void createExamInvalidDates() throws Exception {
        var req = new CreateExamRequest(
                "Final Exam 2027", "FINAL", 1L, 1L, 10L,
                LocalDate.of(2027, 3, 20), LocalDate.of(2027, 3, 15),
                null, null
        );
        when(examService.createExam(any(), any()))
                .thenThrow(ExamException.invalid("startDate", "Start date must be before or equal to end date"));

        mockMvc.perform(post("/api/v1/exams")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Start date must be before or equal to end date"));
    }

    @Test
    @DisplayName("POST /api/v1/exams/{id}/schedules - schedule exam returns 201 Created")
    void createScheduleSuccess() throws Exception {
        var req = new CreateScheduleRequest(
                1L, 10L, 5L,
                LocalDate.of(2026, 10, 5), LocalTime.of(9, 30), LocalTime.of(12, 30),
                new BigDecimal("100.0"), new BigDecimal("35.0"), "Hall 1"
        );
        var res = new ScheduleResponse(
                15L, 1L, 1L, "Grade 10", 10L, "Section A", 5L, "MATH101", "Mathematics",
                LocalDate.of(2026, 10, 5), LocalTime.of(9, 30), LocalTime.of(12, 30),
                new BigDecimal("100.0"), new BigDecimal("35.0"), "Hall 1"
        );
        when(examService.createSchedule(eq(1L), any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/exams/1/schedules")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.subjectName").value("Mathematics"));
    }

    @Test
    @DisplayName("POST /api/v1/exams/{id}/schedules - schedule time conflict returns 409")
    void createScheduleConflict() throws Exception {
        var req = new CreateScheduleRequest(
                1L, 10L, 5L,
                LocalDate.of(2026, 10, 5), LocalTime.of(9, 30), LocalTime.of(12, 30),
                new BigDecimal("100.0"), new BigDecimal("35.0"), "Hall 1"
        );
        when(examService.createSchedule(eq(1L), any(), any()))
                .thenThrow(ExamException.conflict("schedule", "A schedule already exists for this section on this date and time"));

        mockMvc.perform(post("/api/v1/exams/1/schedules")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("A schedule already exists for this section on this date and time"));
    }

    @Test
    @DisplayName("DELETE /api/v1/exams/{id} - delete exam returns 204 NoContent")
    void deleteExamSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/exams/1")
                        .with(authentication(authToken)))
                .andExpect(status().isNoContent());
    }
}
