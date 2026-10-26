package com.erp.portal.reports;

import com.erp.portal.attendance.AttendanceService;
import com.erp.portal.finance.FinanceService;
import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.student.api.StudentDtos.PageResponse;
import com.erp.portal.student.api.StudentDtos.StudentSummary;
import com.erp.portal.student.service.StudentService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportingServiceTest {
    private final StudentService students = mock(StudentService.class);
    private final ReportingService service = new ReportingService(students, mock(AttendanceService.class), mock(FinanceService.class));
    private final StudentSummary student = new StudentSummary(8, "A-008", "Ada", "Lovelace", "ACTIVE",
            LocalDate.of(2025, 4, 1), LocalDateTime.of(2025, 4, 1, 9, 0));

    @Test void reportUsesExistingStudentScopeAndReturnsRequestedPage() {
        SecurityPrincipal actor = principal("STUDENT:READ");
        when(students.list(eq("Ada"), eq("ACTIVE"), eq("firstName"), eq("DESC"), eq(1), eq(10), same(actor)))
                .thenReturn(new PageResponse<>(List.of(student), 1, 10, 11, 2, "firstName", "DESC"));

        ReportDtos.ReportPage result = service.report("student-directory",
                Map.of("search", "Ada", "status", "ACTIVE", "page", "1", "size", "10", "sortBy", "firstName", "direction", "DESC"), actor);

        assertEquals(11, result.totalElements());
        assertEquals("Ada", result.content().get(0).get("firstName"));
        verify(students).list("Ada", "ACTIVE", "firstName", "DESC", 1, 10, actor);
    }

    @Test void reportAndExportRejectMissingPermissions() {
        SecurityPrincipal actor = principal();
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.report("student-directory", Map.of(), actor)).getStatusCode().value());
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.export("student-directory", Map.of(), "CSV", false, actor)).getStatusCode().value());
        verifyNoInteractions(students);
    }

    @Test void exportRequiresSeparateExportGrant() {
        SecurityPrincipal actor = principal("STUDENT:READ");
        assertEquals(403, assertThrows(ResponseStatusException.class,
                () -> service.export("student-directory", Map.of(), "CSV", false, actor)).getStatusCode().value());
    }

    @Test void authorizedExportUsesSameScopedStudentQuery() {
        SecurityPrincipal actor = principal("STUDENT:READ", "REPORT:EXPORT");
        when(students.list(isNull(), isNull(), eq("createdAt"), eq("ASC"), eq(0), eq(100), same(actor)))
                .thenReturn(new PageResponse<>(List.of(student), 0, 100, 1, 1, "createdAt", "ASC"));

        ReportingService.ExportResponse response = service.export("student-directory", Map.of(), "CSV", false, actor);

        assertNotNull(response.file());
        assertTrue(new String(response.file().body()).contains("A-008"));
        verify(students).list(null, null, "createdAt", "ASC", 0, 100, actor);
    }

    @Test void csvExportNeutralizesFormulaTextInStudentData() {
        SecurityPrincipal actor = principal("STUDENT:READ", "REPORT:EXPORT");
        StudentSummary formulaStudent = new StudentSummary(8, "A-008", "=1+1", "Lovelace", "ACTIVE",
                LocalDate.of(2025, 4, 1), LocalDateTime.of(2025, 4, 1, 9, 0));
        when(students.list(isNull(), isNull(), eq("createdAt"), eq("ASC"), eq(0), eq(100), same(actor)))
                .thenReturn(new PageResponse<>(List.of(formulaStudent), 0, 100, 1, 1, "createdAt", "ASC"));

        ReportingService.ExportResponse response = service.export("student-directory", Map.of(), "CSV", false, actor);

        assertTrue(new String(response.file().body()).contains("'=1+1"));
    }

    @Test void reportValidatesPaginationAndSortFields() {
        SecurityPrincipal actor = principal("STUDENT:READ");
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.report("student-directory", Map.of("size", "101"), actor)).getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.report("student-directory", Map.of("sortBy", "id desc"), actor)).getStatusCode().value());
    }

    @Test void largeExportsRejectSynchronousDownloadAndOfferAsyncJob() {
        SecurityPrincipal actor = principal("STUDENT:READ", "REPORT:EXPORT");
        List<StudentSummary> batch = java.util.stream.IntStream.range(0, 100)
                .mapToObj(index -> new StudentSummary(index + 1L, "A-" + index, "Student", "", "ACTIVE",
                        LocalDate.of(2025, 4, 1), LocalDateTime.of(2025, 4, 1, 9, 0)))
                .toList();
        when(students.list(isNull(), isNull(), eq("createdAt"), eq("ASC"), anyInt(), eq(100), same(actor)))
                .thenAnswer(invocation -> new PageResponse<>(batch, invocation.getArgument(4), 100, 5_100, 51, "createdAt", "ASC"));

        assertEquals(413, assertThrows(ResponseStatusException.class,
                () -> service.export("student-directory", Map.of(), "CSV", false, actor)).getStatusCode().value());
        ReportingService.ExportResponse queued = service.export("student-directory", Map.of(), "CSV", true, actor);
        assertEquals("QUEUED", queued.job().status());
    }

    private static SecurityPrincipal principal(String... permissions) {
        return new SecurityPrincipal(7, "report-user", "Report User", List.of(), Set.of(permissions), "session");
    }
}
