package com.erp.portal.student.api;

import com.erp.portal.common.api.GlobalExceptionHandler;
import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.student.StudentModuleException;
import com.erp.portal.student.api.StudentDtos.*;
import com.erp.portal.student.service.StudentService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdmissionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({StudentExceptionHandler.class, GlobalExceptionHandler.class})
class AdmissionControllerApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean StudentService studentService;

    private UsernamePasswordAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        var principal = new SecurityPrincipal(
                1L, "admin", "System Administrator",
                List.of("SUPER_ADMIN"),
                Set.of("ADMISSION:READ", "ADMISSION:CREATE", "ADMISSION:UPDATE"),
                "token-hash"
        );
        authToken = new UsernamePasswordAuthenticationToken(
                principal, null,
                List.of(new SimpleGrantedAuthority("ADMISSION:READ"),
                        new SimpleGrantedAuthority("ADMISSION:CREATE"),
                        new SimpleGrantedAuthority("ADMISSION:UPDATE"))
        );
    }

    @Test
    @DisplayName("GET /api/v1/admissions - list with pagination and filtering")
    void listAdmissionsSuccess() throws Exception {
        var admission = new AdmissionResponse(
                5L, "APP-2026-001", 10L, "Sneha Rao",
                LocalDate.of(2026, 6, 1), "PENDING", LocalDateTime.now()
        );
        var page = new PageResponse<>(List.of(admission), 0, 25, 1L, 1, "appliedOn", "DESC");

        when(studentService.admissions(eq("PENDING"), eq("Sneha"), eq(0), eq(25), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admissions")
                        .with(authentication(authToken))
                        .param("status", "PENDING")
                        .param("search", "Sneha")
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].applicationNo").value("APP-2026-001"))
                .andExpect(jsonPath("$.content[0].applicantName").value("Sneha Rao"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/v1/admissions - invalid status pattern returns 400 validation error")
    void listAdmissionsInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/v1/admissions")
                        .with(authentication(authToken))
                        .param("status", "UNKNOWN_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/admissions - create admission returns 201 Created and Location header")
    void createAdmissionSuccess() throws Exception {
        var studentReq = new CreateStudentRequest(
                "ADM-TEMP-01", "Kavya", "Nair",
                LocalDate.of(2013, 2, 10), "FEMALE", "O+", "Hindu", "General",
                "9876543220", "kavya@example.com", "City Center", null
        );
        var req = new CreateAdmissionRequest("APP-2026-002", studentReq);
        var res = new AdmissionResponse(
                12L, "APP-2026-002", 22L, "Kavya Nair",
                LocalDate.now(), "DRAFT", LocalDateTime.now()
        );

        when(studentService.createAdmission(any(), any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/admissions")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/admissions/12"))
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.applicationNo").value("APP-2026-002"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("POST /api/v1/admissions - duplicate application number conflict returns 409")
    void createAdmissionConflict() throws Exception {
        var req = new CreateAdmissionRequest("APP-DUPLICATE", new CreateStudentRequest(
                "ADM-99", "Name", null, null, null, null, null, null, null, null, null, null
        ));

        when(studentService.createAdmission(any(), any(), any()))
                .thenThrow(StudentModuleException.conflict("applicationNo", "Application number already exists; use a unique application number"));

        mockMvc.perform(post("/api/v1/admissions")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Application number already exists; use a unique application number"));
    }

    @Test
    @DisplayName("GET /api/v1/admissions/{id} - admission details returns 200")
    void getAdmissionDetailSuccess() throws Exception {
        var res = new AdmissionResponse(
                5L, "APP-2026-001", 10L, "Sneha Rao",
                LocalDate.of(2026, 6, 1), "PENDING", LocalDateTime.now()
        );
        when(studentService.admission(eq(5L), any())).thenReturn(res);

        mockMvc.perform(get("/api/v1/admissions/5")
                        .with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.applicationNo").value("APP-2026-001"));
    }

    @Test
    @DisplayName("GET /api/v1/admissions/{id} - admission not found returns 404")
    void getAdmissionNotFound() throws Exception {
        when(studentService.admission(eq(999L), any()))
                .thenThrow(StudentModuleException.admissionNotFound());

        mockMvc.perform(get("/api/v1/admissions/999")
                        .with(authentication(authToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADMISSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/admissions/{id}/submit - submit admission workflow changes status to PENDING")
    void submitAdmissionWorkflow() throws Exception {
        var res = new AdmissionResponse(
                5L, "APP-2026-001", 10L, "Sneha Rao",
                LocalDate.of(2026, 6, 1), "PENDING", LocalDateTime.now()
        );
        when(studentService.submitAdmission(eq(5L), any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/admissions/5/submit")
                        .with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admissions/{id}/status - admission decision workflow (APPROVED)")
    void decideAdmissionWorkflowApproved() throws Exception {
        var decisionReq = new AdmissionDecisionRequest("APPROVED", "All credentials and entrance evaluation passed");
        var res = new AdmissionResponse(
                5L, "APP-2026-001", 10L, "Sneha Rao",
                LocalDate.of(2026, 6, 1), "APPROVED", LocalDateTime.now()
        );

        when(studentService.decideAdmission(eq(5L), any(), any(), any())).thenReturn(res);

        mockMvc.perform(patch("/api/v1/admissions/5/status")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decisionReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admissions/{id}/status - invalid status in decision returns 400")
    void decideAdmissionInvalidStatus() throws Exception {
        var invalidDecision = new AdmissionDecisionRequest("NOT_A_VALID_STATUS", "Note");

        mockMvc.perform(patch("/api/v1/admissions/5/status")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDecision)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
