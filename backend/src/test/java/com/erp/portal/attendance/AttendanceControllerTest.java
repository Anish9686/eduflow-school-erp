package com.erp.portal.attendance;

import com.erp.portal.attendance.AttendanceDtos.*;
import com.erp.portal.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AttendanceController.class)
@AutoConfigureMockMvc(addFilters = false)
class AttendanceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AttendanceService service;

    private static final LocalDate TEST_DATE = LocalDate.parse("2026-10-06");

    @Test
    void markStudentsReturns201AndBatchResult() throws Exception {
        var batch = new StudentMarkBatchRequest(1L, 2L, 3L, TEST_DATE, List.of(new StudentMarkEntry(50L, "PRESENT", "On time")));
        StudentAttendance rec = new StudentAttendance(1L, 50L, "ADM-001", "Student One", 1L, 2L, "Class 1", 3L, "Section A",
                TEST_DATE, "PRESENT", 10L, "Teacher", LocalDateTime.now(), "On time", false);
        var result = new MarkBatchResult(1, 0, List.of(rec));

        when(service.markStudents(any(), anyString(), any(), any())).thenReturn(result);

        mockMvc.perform(post("/api/v1/attendance/students/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "test-key-12345678")
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.alreadyMarked").value(0))
                .andExpect(jsonPath("$.records[0].status").value("PRESENT"));
    }

    @Test
    void queryStudentsReturnsList() throws Exception {
        StudentAttendance rec = new StudentAttendance(1L, 50L, "ADM-001", "Student One", 1L, 2L, "Class 1", 3L, "Section A",
                TEST_DATE, "PRESENT", 10L, "Teacher", LocalDateTime.now(), null, false);
        when(service.studentRecords(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(rec));

        mockMvc.perform(get("/api/v1/attendance/students")
                        .param("sessionId", "1")
                        .param("classId", "2")
                        .param("sectionId", "3")
                        .param("status", "PRESENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentName").value("Student One"));
    }

    @Test
    void correctStudentReturnsUpdatedRecord() throws Exception {
        var req = new AttendanceCorrectionRequest("ABSENT", "Medical leave noted");
        StudentAttendance rec = new StudentAttendance(1L, 50L, "ADM-001", "Student One", 1L, 2L, "Class 1", 3L, "Section A",
                TEST_DATE, "ABSENT", 10L, "Teacher", LocalDateTime.now(), null, true);
        when(service.correctStudent(eq(1L), any(), any(), any())).thenReturn(rec);

        mockMvc.perform(post("/api/v1/attendance/students/1/corrections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABSENT"))
                .andExpect(jsonPath("$.corrected").value(true));
    }

    @Test
    void conflictHandledByAttendanceExceptionHandler() throws Exception {
        var batch = new StudentMarkBatchRequest(1L, 2L, 3L, TEST_DATE, List.of(new StudentMarkEntry(50L, "PRESENT", null)));
        when(service.markStudents(any(), anyString(), any(), any()))
                .thenThrow(AttendanceException.conflict("Attendance already exists for this student and date; use the correction workflow"));

        mockMvc.perform(post("/api/v1/attendance/students/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Attendance already exists for this student and date; use the correction workflow"));
    }

    @Test
    void lockedDateReturnsConflict() throws Exception {
        var batch = new StudentMarkBatchRequest(1L, 2L, 3L, TEST_DATE, List.of(new StudentMarkEntry(50L, "PRESENT", null)));
        when(service.markStudents(any(), anyString(), any(), any()))
                .thenThrow(AttendanceException.conflict("Attendance date is locked for this session"));

        mockMvc.perform(post("/api/v1/attendance/students/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Attendance date is locked for this session"));
    }

    @Test
    void rosterEndpointReturnsStudents() throws Exception {
        var rosterItem = new RosterStudent(50L, "ADM-001", "Student One", "PRESENT", null, 1L, false);
        when(service.roster(eq(1L), eq(2L), eq(3L), eq(TEST_DATE), any())).thenReturn(List.of(rosterItem));

        mockMvc.perform(get("/api/v1/attendance/roster")
                        .param("sessionId", "1")
                        .param("classId", "2")
                        .param("sectionId", "3")
                        .param("attendanceDate", "2026-10-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentName").value("Student One"))
                .andExpect(jsonPath("$[0].status").value("PRESENT"));
    }

    @Test
    void summaryEndpointReturnsStats() throws Exception {
        var summary = new AttendanceSummary(30, 25, 3, 1, 1, 0, 0);
        when(service.summary(eq(TEST_DATE), eq(1L), eq(2L), eq(3L), any())).thenReturn(summary);

        mockMvc.perform(get("/api/v1/attendance/summary")
                        .param("attendanceDate", "2026-10-06")
                        .param("sessionId", "1")
                        .param("classId", "2")
                        .param("sectionId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(30))
                .andExpect(jsonPath("$.present").value(25))
                .andExpect(jsonPath("$.absent").value(3));
    }

    @Test
    void staffMarkingAndCorrection() throws Exception {
        var staffBatch = new StaffMarkBatchRequest(TEST_DATE, List.of(new StaffMarkEntry(100L, "PRESENT", null)));
        var staffRec = new StaffAttendance(1L, 100L, "EMP-001", "Teacher Smith", TEST_DATE, "PRESENT", 1L, "Admin", LocalDateTime.now(), null, false);
        when(service.markStaff(any(), anyString(), any(), any())).thenReturn(new StaffMarkBatchResult(1, 0, List.of(staffRec)));

        mockMvc.perform(post("/api/v1/attendance/staff/mark")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staffBatch)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.records[0].staffName").value("Teacher Smith"));
    }
}
