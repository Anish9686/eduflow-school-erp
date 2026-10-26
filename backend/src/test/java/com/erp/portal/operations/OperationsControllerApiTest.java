package com.erp.portal.operations;

import com.erp.portal.common.api.GlobalExceptionHandler;
import com.erp.portal.operations.OperationsDtos.*;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OperationsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({OperationsExceptionHandler.class, GlobalExceptionHandler.class})
class OperationsControllerApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean OperationsService operationsService;

    private UsernamePasswordAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        var principal = new SecurityPrincipal(
                1L, "admin", "Admin",
                List.of("SUPER_ADMIN"),
                Set.of("INVENTORY:READ", "INVENTORY:MANAGE", "LIBRARY:READ", "LIBRARY:MANAGE", "LIBRARY:ISSUE", "LIBRARY:RETURN", "HOSTEL:READ", "HOSTEL:MANAGE"),
                "token"
        );
        authToken = new UsernamePasswordAuthenticationToken(principal, null, List.of(
                new SimpleGrantedAuthority("INVENTORY:READ"),
                new SimpleGrantedAuthority("INVENTORY:MANAGE"),
                new SimpleGrantedAuthority("LIBRARY:ISSUE"),
                new SimpleGrantedAuthority("LIBRARY:RETURN"),
                new SimpleGrantedAuthority("HOSTEL:MANAGE")
        ));
    }

    @Test
    @DisplayName("GET /api/v1/operations/inventory/items - lists inventory items")
    void listInventoryItems() throws Exception {
        var item = new ItemResponse(1L, "A4 Paper Reams", "INV-001", "Stationery", "BOX", 50, 10, "Office paper");
        when(operationsService.items()).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/operations/inventory/items").with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].code").value("INV-001"))
                .andExpect(jsonPath("$[0].currentStock").value(50));
    }

    @Test
    @DisplayName("POST /api/v1/operations/inventory/items - creates inventory item")
    void createInventoryItem() throws Exception {
        var req = new CreateItemRequest("Whiteboard Markers", "INV-002", "Stationery", "PACK", 100, 20, "Dry erase");
        var res = new ItemResponse(2L, "Whiteboard Markers", "INV-002", "Stationery", "PACK", 100, 20, "Dry erase");
        when(operationsService.createItem(any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/operations/inventory/items")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.code").value("INV-002"));
    }

    @Test
    @DisplayName("POST /api/v1/operations/inventory/movements - negative stock movement rejected with 409 Conflict")
    void stockMovementNegativeStockRejected() throws Exception {
        var req = new StockMovementRequest("OUT", 500, "REF-DISPATCH-99", "Dispatch for exams");
        when(operationsService.stockMovement(anyLong(), any(), any()))
                .thenThrow(new OperationsException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Stock movement would result in negative inventory"));

        mockMvc.perform(post("/api/v1/operations/inventory/movements")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value("Stock movement would result in negative inventory"));
    }

    @Test
    @DisplayName("POST /api/v1/operations/library/issues - issues book to student")
    void issueLibraryBookSuccess() throws Exception {
        var req = new IssueRequest("STUDENT", 10L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), "ISSUED");
        var res = new IssueResponse(1L, 5L, "STUDENT", 10L, "ISSUED", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), BigDecimal.ZERO);
        when(operationsService.issueLibrary(anyLong(), any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/operations/library/issues")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("ISSUED"));
    }

    @Test
    @DisplayName("POST /api/v1/operations/library/issues/{id}/return - returns library book with 204 NoContent")
    void returnLibraryBookSuccess() throws Exception {
        var req = new ReturnRequest(LocalDate.of(2026, 10, 14), BigDecimal.ZERO, "Returned in good condition");

        mockMvc.perform(post("/api/v1/operations/library/issues/1/return")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/operations/hostel/rooms - creates hostel room")
    void createHostelRoomSuccess() throws Exception {
        var req = new CreateRoomRequest("R-204", "2", "Block A", 4, "STANDARD", true);
        var res = new RoomResponse(10L, "R-204", "2", "Block A", 4, "STANDARD", true);
        when(operationsService.createRoom(any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/operations/hostel/rooms")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.roomNo").value("R-204"))
                .andExpect(jsonPath("$.capacity").value(4));
    }
}
