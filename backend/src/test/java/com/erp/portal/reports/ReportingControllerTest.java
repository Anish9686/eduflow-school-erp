package com.erp.portal.reports;

import com.erp.portal.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReportingController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReportingControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockBean ReportingService service;

    @Test void reportPassesFiltersPaginationAndSortingToService() throws Exception {
        when(service.report(eq("student-directory"), anyMap(), isNull())).thenReturn(new ReportDtos.ReportPage(
                "student-directory", List.of(new ReportDtos.ColumnDefinition("admissionNo", "Admission no.")),
                List.of(Map.of("admissionNo", "A-001")), 1, 10, 11, 2, "admissionNo", "DESC"));

        mvc.perform(get("/api/v1/reports/student-directory").param("status", "ACTIVE").param("dateFrom", "2026-10-01")
                        .param("page", "1").param("size", "10").param("sortBy", "admissionNo").param("direction", "DESC"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].admissionNo").value("A-001"))
                .andExpect(jsonPath("$.totalElements").value(11));
        verify(service).report(eq("student-directory"), argThat(params -> "ACTIVE".equals(params.get("status"))
                && "2026-10-01".equals(params.get("dateFrom")) && "1".equals(params.get("page"))
                && "DESC".equals(params.get("direction"))), isNull());
    }

    @Test void exportReturnsDownloadHeadersAndBody() throws Exception {
        when(service.export(eq("student-directory"), anyMap(), eq("CSV"), eq(false), isNull()))
                .thenReturn(ReportingService.ExportResponse.file(new ReportingService.ExportFile(
                        "student-directory.csv", "text/csv;charset=UTF-8", "Admission no.\r\nA-001\r\n".getBytes(), 1)));

        mvc.perform(get("/api/v1/reports/student-directory/export").param("format", "CSV"))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("student-directory.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv")).andExpect(content().string(org.hamcrest.Matchers.containsString("A-001")));
    }

    @Test void asyncExportReturnsAcceptedJob() throws Exception {
        when(service.export(eq("student-directory"), anyMap(), eq("XLSX"), eq(true), isNull()))
                .thenReturn(ReportingService.ExportResponse.job(new ReportDtos.ExportJob("job-1", "student-directory", "QUEUED", "/api/v1/reports/exports/job-1")));
        mvc.perform(get("/api/v1/reports/student-directory/export").param("format", "XLSX").param("async", "true"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.jobId").value("job-1"));
    }

    @Test void forbiddenServiceResultIsReturnedAsForbidden() throws Exception {
        when(service.report(eq("student-directory"), anyMap(), isNull()))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Report read permission is required"));
        mvc.perform(get("/api/v1/reports/student-directory")).andExpect(status().isForbidden());
    }
}
