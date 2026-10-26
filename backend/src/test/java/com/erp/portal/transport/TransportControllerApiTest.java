package com.erp.portal.transport;

import com.erp.portal.common.api.GlobalExceptionHandler;
import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.transport.TransportDtos.*;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TransportController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({TransportExceptionHandler.class, GlobalExceptionHandler.class})
class TransportControllerApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean TransportService transportService;

    private UsernamePasswordAuthenticationToken operationalToken;
    private UsernamePasswordAuthenticationToken studentToken;

    @BeforeEach
    void setUp() {
        var operationalPrincipal = new SecurityPrincipal(
                1L, "admin", "Admin",
                List.of("SUPER_ADMIN"),
                Set.of("TRANSPORT:READ", "TRANSPORT:CREATE", "TRANSPORT:UPDATE", "TRANSPORT:DELETE"),
                "token"
        );
        operationalToken = new UsernamePasswordAuthenticationToken(operationalPrincipal, null, List.of(
                new SimpleGrantedAuthority("TRANSPORT:READ"),
                new SimpleGrantedAuthority("TRANSPORT:CREATE"),
                new SimpleGrantedAuthority("TRANSPORT:UPDATE")
        ));

        var studentPrincipal = new SecurityPrincipal(
                50L, "student", "Student User",
                List.of("STUDENT"),
                Set.of("TRANSPORT:READ"),
                "token"
        );
        studentToken = new UsernamePasswordAuthenticationToken(studentPrincipal, null, List.of(
                new SimpleGrantedAuthority("TRANSPORT:READ")
        ));

        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(operationalToken);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/transport/overview - operational user gets stats")
    void overviewSuccess() throws Exception {
        var overview = new TransportOverviewResponse(10, 8, 180, 70, 2, 1, 0, 0, List.of());
        when(transportService.getOverview()).thenReturn(overview);

        mockMvc.perform(get("/api/v1/transport/overview").with(authentication(operationalToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveBuses").value(10))
                .andExpect(jsonPath("$.totalActiveRoutes").value(8))
                .andExpect(jsonPath("$.totalAssignedStudents").value(180));
    }

    @Test
    @DisplayName("GET /api/v1/transport/overview - non-operational user rejected with 403 Forbidden")
    void overviewForbiddenForStudent() throws Exception {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(studentToken);
        mockMvc.perform(get("/api/v1/transport/overview").with(authentication(studentToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/v1/transport/buses - list buses with filters")
    void listBusesSuccess() throws Exception {
        var bus = new BusResponse(
                1L, "School Bus 01", "DL-01-AB-1234", "F-01", "MINIBUS",
                30, 20, 10, 1L, "Route 1 North", "R01",
                10L, "Driver Ramesh", "9876543210", 11L, "Conductor Suresh", "9876543211",
                "ACTIVE", "INST-01", LocalDateTime.now(), LocalDateTime.now()
        );
        when(transportService.listBuses(isNull(), eq("ACTIVE"), eq(1L), eq(0), eq(50)))
                .thenReturn(List.of(bus));

        mockMvc.perform(get("/api/v1/transport/buses")
                        .with(authentication(operationalToken))
                        .param("status", "ACTIVE")
                        .param("routeId", "1")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].registrationNo").value("DL-01-AB-1234"))
                .andExpect(jsonPath("$[0].capacity").value(30));
    }

    @Test
    @DisplayName("POST /api/v1/transport/buses - create bus returns 201 Created")
    void createBusSuccess() throws Exception {
        var req = new CreateBusRequest("Bus 02", "DL-01-CD-5678", "F-02", "STANDARD", 40, 1L, 10L, 11L, "INST-01");
        var res = new BusResponse(
                2L, "Bus 02", "DL-01-CD-5678", "F-02", "STANDARD",
                40, 0, 40, 1L, "Route 1 North", "R01",
                10L, "Driver Ramesh", "9876543210", 11L, "Conductor Suresh", "9876543211",
                "ACTIVE", "INST-01", LocalDateTime.now(), LocalDateTime.now()
        );
        when(transportService.createBus(any(), any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/transport/buses")
                        .with(authentication(operationalToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.registrationNo").value("DL-01-CD-5678"));
    }

    @Test
    @DisplayName("POST /api/v1/transport/buses - duplicate registration conflict returns 409")
    void createBusDuplicateConflict() throws Exception {
        var req = new CreateBusRequest("Bus Duplicate", "DL-01-DUPLICATE", null, "STANDARD", 40, null, null, null, null);
        when(transportService.createBus(any(), any(), any()))
                .thenThrow(TransportException.conflict("registrationNo", "Bus with registration DL-01-DUPLICATE already exists"));

        mockMvc.perform(post("/api/v1/transport/buses")
                        .with(authentication(operationalToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Bus with registration DL-01-DUPLICATE already exists"));
    }

    @Test
    @DisplayName("PATCH /api/v1/transport/buses/{id}/deactivate - deactivates bus with 204 NoContent")
    void deactivateBusSuccess() throws Exception {
        mockMvc.perform(patch("/api/v1/transport/buses/1/deactivate")
                        .with(authentication(operationalToken)))
                .andExpect(status().isNoContent());
    }
}
