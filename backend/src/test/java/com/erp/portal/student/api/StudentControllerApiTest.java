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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StudentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({StudentExceptionHandler.class, GlobalExceptionHandler.class})
class StudentControllerApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean StudentService studentService;

    private SecurityPrincipal adminPrincipal;
    private UsernamePasswordAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        adminPrincipal = new SecurityPrincipal(
                1L, "admin", "System Administrator",
                List.of("SUPER_ADMIN"),
                Set.of("STUDENT:READ", "STUDENT:CREATE", "STUDENT:UPDATE", "ADMISSION:READ", "ADMISSION:UPDATE"),
                "session-token-hash"
        );
        authToken = new UsernamePasswordAuthenticationToken(
                adminPrincipal, null,
                List.of(new SimpleGrantedAuthority("STUDENT:READ"),
                        new SimpleGrantedAuthority("STUDENT:CREATE"),
                        new SimpleGrantedAuthority("STUDENT:UPDATE"))
        );
    }

    @Test
    @DisplayName("GET /api/v1/students - list with pagination, filtering, and sorting")
    void listStudentsSuccess() throws Exception {
        var summary = new StudentSummary(
                10L, "ADM-2026-001", "Aarav", "Sharma",
                "ACTIVE", LocalDate.of(2026, 6, 1), LocalDateTime.now()
        );
        var pageResponse = new PageResponse<>(
                List.of(summary), 0, 25, 1L, 1, "createdAt", "DESC"
        );

        when(studentService.list(eq("Aarav"), eq("ACTIVE"), eq("createdAt"), eq("DESC"), eq(0), eq(25), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/students")
                        .with(authentication(authToken))
                        .param("search", "Aarav")
                        .param("status", "ACTIVE")
                        .param("sortBy", "createdAt")
                        .param("direction", "DESC")
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.content[0].admissionNo").value("ADM-2026-001"))
                .andExpect(jsonPath("$.content[0].firstName").value("Aarav"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/students - negative validation on invalid page, size, and status")
    void listStudentsValidationFailure() throws Exception {
        mockMvc.perform(get("/api/v1/students")
                        .with(authentication(authToken))
                        .param("page", "-1")
                        .param("size", "150")
                        .param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/students - creates student and returns 201 Created with Location header")
    void createStudentSuccess() throws Exception {
        var request = new CreateStudentRequest(
                "ADM-2026-002", "Diya", "Patel",
                LocalDate.of(2012, 4, 15), "FEMALE", "B+", "Hindu", "General",
                "9876543210", "diya.patel@example.com", "123 Elm Street", "https://img.example.com/diya.jpg"
        );
        var response = new StudentResponse(
                20L, "ADM-2026-002", "Diya", "Patel",
                LocalDate.of(2012, 4, 15), "FEMALE", "B+", "Hindu", "General",
                "9876543210", "diya.patel@example.com", "123 Elm Street", "https://img.example.com/diya.jpg",
                "ACTIVE", LocalDate.now(), null, null,
                LocalDateTime.now(), LocalDateTime.now(), List.of()
        );

        when(studentService.create(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/students")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/students/20"))
                .andExpect(jsonPath("$.id").value(20))
                .andExpect(jsonPath("$.admissionNo").value("ADM-2026-002"))
                .andExpect(jsonPath("$.firstName").value("Diya"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/students - negative validation fails on blank admissionNo and blank firstName")
    void createStudentValidationFailure() throws Exception {
        var invalidRequest = new CreateStudentRequest(
                "", "", null, null, "INVALID_GENDER", null, null, null, null, "not-an-email", null, null
        );

        mockMvc.perform(post("/api/v1/students")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/students - conflict returns 409 when admission number is already taken")
    void createStudentDuplicateConflict() throws Exception {
        var request = new CreateStudentRequest(
                "ADM-DUPLICATE", "Test", "Student",
                LocalDate.of(2010, 1, 1), "MALE", "O+", "Christian", "General",
                null, null, null, null
        );

        when(studentService.create(any(), any(), any()))
                .thenThrow(StudentModuleException.conflict("admissionNo", "Student admission number is already assigned; use a unique number"));

        mockMvc.perform(post("/api/v1/students")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Student admission number is already assigned; use a unique number"));
    }

    @Test
    @DisplayName("GET /api/v1/students/{id} - detail returns 200 with student and guardians")
    void getStudentDetailSuccess() throws Exception {
        var guardian = new GuardianResponse(
                100L, "Rajesh Sharma", "9876543210", "rajesh@example.com", "Address", "Engineer", "FATHER", true
        );
        var response = new StudentResponse(
                10L, "ADM-2026-001", "Aarav", "Sharma",
                LocalDate.of(2010, 5, 20), "MALE", "A+", "Hindu", "General",
                "9876543210", "aarav@example.com", "Address", null,
                "ACTIVE", LocalDate.of(2026, 6, 1), null, null,
                LocalDateTime.now(), LocalDateTime.now(), List.of(guardian)
        );

        when(studentService.detail(eq(10L), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/students/10")
                        .with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.firstName").value("Aarav"))
                .andExpect(jsonPath("$.guardians[0].name").value("Rajesh Sharma"))
                .andExpect(jsonPath("$.guardians[0].relation").value("FATHER"));
    }

    @Test
    @DisplayName("GET /api/v1/students/{id} - not found returns 404")
    void getStudentDetailNotFound() throws Exception {
        when(studentService.detail(eq(999L), any()))
                .thenThrow(StudentModuleException.notFound());

        mockMvc.perform(get("/api/v1/students/999")
                        .with(authentication(authToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("PUT /api/v1/students/{id} - update returns 200 with updated student")
    void updateStudentSuccess() throws Exception {
        var updateReq = new UpdateStudentRequest(
                "Aarav Updated", "Sharma",
                LocalDate.of(2010, 5, 20), "MALE", "A+", "Hindu", "General",
                "9876543211", "aarav.new@example.com", "Updated Address", null
        );
        var updatedResponse = new StudentResponse(
                10L, "ADM-2026-001", "Aarav Updated", "Sharma",
                LocalDate.of(2010, 5, 20), "MALE", "A+", "Hindu", "General",
                "9876543211", "aarav.new@example.com", "Updated Address", null,
                "ACTIVE", LocalDate.of(2026, 6, 1), null, null,
                LocalDateTime.now(), LocalDateTime.now(), List.of()
        );

        when(studentService.update(eq(10L), any(), any(), any())).thenReturn(updatedResponse);

        mockMvc.perform(put("/api/v1/students/10")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Aarav Updated"))
                .andExpect(jsonPath("$.phone").value("9876543211"));
    }

    @Test
    @DisplayName("POST /api/v1/students/{id}/guardians - add guardian returns updated guardian list")
    void addGuardianSuccess() throws Exception {
        var guardianReq = new GuardianRequest("Sunita Sharma", "9876543212", "sunita@example.com", "Address", "Teacher", "MOTHER", false);
        var guardianRes = new GuardianResponse(101L, "Sunita Sharma", "9876543212", "sunita@example.com", "Address", "Teacher", "MOTHER", false);

        when(studentService.addGuardian(eq(10L), any(), any(), any())).thenReturn(List.of(guardianRes));

        mockMvc.perform(post("/api/v1/students/10/guardians")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(guardianReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sunita Sharma"))
                .andExpect(jsonPath("$[0].relation").value("MOTHER"));
    }

    @Test
    @DisplayName("DELETE /api/v1/students/{id}/guardians/{guardianId} - removes guardian with 204 NoContent")
    void removeGuardianSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/students/10/guardians/101")
                        .with(authentication(authToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/students/{id}/transfer - transfers student and records destination school")
    void transferStudentWorkflow() throws Exception {
        var req = new TransferRequest("St. Xavier High School", LocalDate.of(2026, 10, 1), "Relocation to new city");
        var res = new StudentResponse(
                10L, "ADM-2026-001", "Aarav", "Sharma",
                null, "MALE", null, null, null, null, null, null, null,
                "TRANSFERRED", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 10, 1), "Relocation to new city",
                LocalDateTime.now(), LocalDateTime.now(), List.of()
        );

        when(studentService.transfer(eq(10L), any(), any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/students/10/transfer")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TRANSFERRED"))
                .andExpect(jsonPath("$.exitReason").value("Relocation to new city"));
    }

    @Test
    @DisplayName("POST /api/v1/students/{id}/exit - exits student from institution")
    void exitStudentWorkflow() throws Exception {
        var req = new ExitRequest("Graduated secondary education", LocalDate.of(2026, 10, 1));
        var res = new StudentResponse(
                10L, "ADM-2026-001", "Aarav", "Sharma",
                null, "MALE", null, null, null, null, null, null, null,
                "EXITED", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 10, 1), "Graduated secondary education",
                LocalDateTime.now(), LocalDateTime.now(), List.of()
        );

        when(studentService.exit(eq(10L), any(), any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/students/10/exit")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXITED"));
    }

    @Test
    @DisplayName("POST /api/v1/students/{id}/documents - multipart file upload returns 201 Created")
    void uploadDocumentSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "birth_certificate.pdf", "application/pdf", "%PDF-1.4 test binary data".getBytes()
        );
        var docRes = new StudentDocumentResponse(
                50L, "BIRTH_CERTIFICATE", "birth_certificate.pdf", 28L, "application/pdf",
                LocalDateTime.now(), 1L
        );

        when(studentService.uploadDocument(eq(10L), eq("BIRTH_CERTIFICATE"), any(), any(), any()))
                .thenReturn(docRes);

        mockMvc.perform(multipart("/api/v1/students/10/documents")
                        .file(file)
                        .param("docType", "BIRTH_CERTIFICATE")
                        .with(authentication(authToken)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/students/10/documents/50"))
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.fileName").value("birth_certificate.pdf"));
    }

    @Test
    @DisplayName("GET /api/v1/students/{id}/documents/{documentId}/content - downloads document resource")
    void downloadDocumentSuccess() throws Exception {
        var resource = new ByteArrayResource("%PDF-1.4 sample content".getBytes());
        var downloaded = new StudentService.DocumentDownload(resource, "birth_cert.pdf", "application/pdf");

        when(studentService.downloadDocument(eq(10L), eq(50L), any())).thenReturn(downloaded);

        mockMvc.perform(get("/api/v1/students/10/documents/50/content")
                        .with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().bytes("%PDF-1.4 sample content".getBytes()));
    }

    @Test
    @DisplayName("GET /api/v1/students/{id}/history - returns audit history events")
    void getStudentHistorySuccess() throws Exception {
        var event = new StudentHistoryResponse(
                1L, "STATUS_CHANGE", "ACTIVE", "TRANSFERRED",
                "Relocated to another city", 1L, LocalDateTime.now()
        );
        when(studentService.history(eq(10L), any())).thenReturn(List.of(event));

        mockMvc.perform(get("/api/v1/students/10/history")
                        .with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("STATUS_CHANGE"))
                .andExpect(jsonPath("$[0].previousValue").value("ACTIVE"))
                .andExpect(jsonPath("$[0].newValue").value("TRANSFERRED"));
    }
}
