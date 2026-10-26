package com.erp.portal.academic;

import com.erp.portal.academic.AcademicDtos.*;
import com.erp.portal.common.api.GlobalExceptionHandler;
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

@WebMvcTest(AcademicController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AcademicExceptionHandler.class, GlobalExceptionHandler.class})
class AcademicControllerApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AcademicService academicService;

    private UsernamePasswordAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        var principal = new SecurityPrincipal(
                1L, "admin", "Admin",
                List.of("SUPER_ADMIN"),
                Set.of("ACADEMIC:READ", "ACADEMIC:MANAGE", "TIMETABLE:READ", "TIMETABLE:MANAGE", "HOMEWORK:READ", "HOMEWORK:CREATE", "SYLLABUS:READ", "SYLLABUS:UPDATE"),
                "token"
        );
        authToken = new UsernamePasswordAuthenticationToken(principal, null, List.of(
                new SimpleGrantedAuthority("ACADEMIC:READ"),
                new SimpleGrantedAuthority("ACADEMIC:MANAGE"),
                new SimpleGrantedAuthority("TIMETABLE:MANAGE")
        ));
    }

    @Test
    @DisplayName("GET /api/v1/academics/sessions - lists all academic sessions")
    void listSessions() throws Exception {
        var s = new SessionResponse(1L, "2026-27", "Academic Year 2026-2027", LocalDate.of(2026, 4, 1), LocalDate.of(2027, 3, 31), true, LocalDateTime.now());
        when(academicService.sessions()).thenReturn(List.of(s));

        mockMvc.perform(get("/api/v1/academics/sessions").with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].code").value("2026-27"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/academics/sessions - creates a session and returns 201 Created")
    void createSession() throws Exception {
        var req = new SessionRequest("2027-28", "Year 2027-2028", LocalDate.of(2027, 4, 1), LocalDate.of(2028, 3, 31), false);
        var res = new SessionResponse(2L, "2027-28", "Year 2027-2028", LocalDate.of(2027, 4, 1), LocalDate.of(2028, 3, 31), false, LocalDateTime.now());
        when(academicService.createSession(any(), any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/academics/sessions")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.code").value("2027-28"));
    }

    @Test
    @DisplayName("GET /api/v1/academics/classes - lists all classes")
    void listClasses() throws Exception {
        var c = new ClassResponse(1L, "Grade 10", 10, LocalDateTime.now());
        when(academicService.classes()).thenReturn(List.of(c));

        mockMvc.perform(get("/api/v1/academics/classes").with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Grade 10"));
    }

    @Test
    @DisplayName("POST /api/v1/academics/classes - creates class and returns 201 Created")
    void createClass() throws Exception {
        var req = new ClassRequest("Grade 11", 11);
        var res = new ClassResponse(2L, "Grade 11", 11, LocalDateTime.now());
        when(academicService.createClass(any(), any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/academics/classes")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Grade 11"));
    }

    @Test
    @DisplayName("GET /api/v1/academics/sections - filters sections by classId")
    void listSectionsWithClassFilter() throws Exception {
        var s = new SectionResponse(10L, 1L, "Grade 10", "Section A", 40, "Room 101", LocalDateTime.now());
        when(academicService.sections(eq(1L), any())).thenReturn(List.of(s));

        mockMvc.perform(get("/api/v1/academics/sections")
                        .with(authentication(authToken))
                        .param("classId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].name").value("Section A"));
    }

    @Test
    @DisplayName("POST /api/v1/academics/timetable - creates timetable entry with 201 Created")
    void createTimetableSuccess() throws Exception {
        var req = new TimetableRequest(1L, 1L, 10L, 5L, 20L, 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "Room 101");
        var res = new TimetableResponse(100L, 1L, 1L, "Grade 10", 10L, "Section A", 5L, "Mathematics", 20L, "John Doe", 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "Room 101");
        when(academicService.addTimetable(any(), any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/academics/timetable")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.subjectName").value("Mathematics"));
    }

    @Test
    @DisplayName("POST /api/v1/academics/timetable - timetable schedule overlap conflict returns 409")
    void createTimetableConflict() throws Exception {
        var req = new TimetableRequest(1L, 1L, 10L, 5L, 20L, 1, LocalTime.of(9, 0), LocalTime.of(10, 0), "Room 101");
        when(academicService.addTimetable(any(), any(), any()))
                .thenThrow(AcademicException.conflict("timetable", "Timetable slot overlaps with another scheduled class"));

        mockMvc.perform(post("/api/v1/academics/timetable")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Timetable slot overlaps with another scheduled class"));
    }

    @Test
    @DisplayName("DELETE /api/v1/academics/timetable/{id} - deletes timetable with 204 NoContent")
    void deleteTimetableSuccess() throws Exception {
        mockMvc.perform(delete("/api/v1/academics/timetable/100")
                        .with(authentication(authToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/academics/homework - creates homework assignment")
    void createHomeworkSuccess() throws Exception {
        var req = new HomeworkRequest(1L, 10L, 5L, "Chapter 3 Exercises", "Solve 1 to 10", LocalDate.of(2026, 10, 25));
        var res = new HomeworkResponse(1L, 1L, 1L, "Grade 10", 10L, "Section A", 5L, "Mathematics", 20L, "John Doe", "Chapter 3 Exercises", "Solve 1 to 10", LocalDate.of(2026, 10, 25), LocalDateTime.now());
        when(academicService.createHomework(any(), any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/academics/homework")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Chapter 3 Exercises"));
    }
}
