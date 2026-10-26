package com.erp.portal.finance;

import com.erp.portal.common.api.GlobalExceptionHandler;
import com.erp.portal.finance.FinanceDtos.*;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FinanceController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FinanceControllerApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean FinanceService financeService;

    private UsernamePasswordAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        var principal = new SecurityPrincipal(
                1L, "accountant", "Accountant User",
                List.of("ACCOUNTANT"),
                Set.of("FEE:READ", "FEE:MANAGE", "FEE:COLLECT", "FEE:REFUND", "FEE_DISCOUNT:REQUEST", "FEE_DISCOUNT:APPROVE", "EXPENSE:MANAGE", "EXPENSE:APPROVE", "FINANCE_REPORT:READ", "FINANCE_REPORT:EXPORT"),
                "token"
        );
        authToken = new UsernamePasswordAuthenticationToken(principal, null, List.of(
                new SimpleGrantedAuthority("FEE:READ"),
                new SimpleGrantedAuthority("FEE:MANAGE"),
                new SimpleGrantedAuthority("FEE:COLLECT"),
                new SimpleGrantedAuthority("FEE_DISCOUNT:REQUEST"),
                new SimpleGrantedAuthority("EXPENSE:MANAGE")
        ));
    }

    @Test
    @DisplayName("GET /api/v1/finance/structures - lists fee structures")
    void listStructuresSuccess() throws Exception {
        var fs = new FeeStructure(
                1L, 1L, 1L, "Grade 10", 1L, "Tuition",
                new BigDecimal("5000.00"), LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 4, 1), null, "MONTHLY", true
        );
        when(financeService.structures()).thenReturn(List.of(fs));

        mockMvc.perform(get("/api/v1/finance/structures").with(authentication(authToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].categoryName").value("Tuition"))
                .andExpect(jsonPath("$[0].amount").value(5000.00));
    }

    @Test
    @DisplayName("POST /api/v1/finance/structures - creates fee structure")
    void createStructureSuccess() throws Exception {
        var req = new FeeStructureRequest(
                1L, 1L, 1L, new BigDecimal("6000.00"), LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 4, 1), null, "MONTHLY"
        );
        var res = new FeeStructure(
                2L, 1L, 1L, "Grade 10", 1L, "Tuition",
                new BigDecimal("6000.00"), LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 4, 1), null, "MONTHLY", true
        );
        when(financeService.createStructure(any(), any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/finance/structures")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.amount").value(6000.00));
    }

    @Test
    @DisplayName("POST /api/v1/finance/demands - generates fee demand for student")
    void generateDemandSuccess() throws Exception {
        var req = new DemandRequest(10L, 1L, LocalDate.of(2026, 10, 1));
        var res = new FeeDemand(
                100L, 10L, "Aarav Sharma", 1L, 1L,
                LocalDate.of(2026, 10, 1), new BigDecimal("5000.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("5000.00"),
                LocalDate.of(2026, 10, 10), "PENDING"
        );
        when(financeService.generateDemand(any(), any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/finance/demands")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.amountDue").value(5000.00))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/finance/payments - posts payment collection")
    void postPaymentSuccess() throws Exception {
        var req = new PaymentRequest(
                100L, "REF-UPI-12345", new BigDecimal("5000.00"),
                "UPI", LocalDate.of(2026, 10, 5), "Online Fee Payment"
        );
        var payment = new Payment(
                50L, 10L, 100L, "RCP-2026-0001", "REF-UPI-12345",
                new BigDecimal("5000.00"), "UPI", LocalDate.of(2026, 10, 5),
                "POSTED", LocalDateTime.now()
        );
        when(financeService.postPayment(any(), any(), any())).thenReturn(payment);

        mockMvc.perform(post("/api/v1/finance/payments")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(50))
                .andExpect(jsonPath("$.receiptNo").value("RCP-2026-0001"))
                .andExpect(jsonPath("$.status").value("POSTED"));
    }

    @Test
    @DisplayName("POST /api/v1/finance/discounts - requests discount for student demand")
    void requestDiscountSuccess() throws Exception {
        var req = new DiscountRequest(100L, new BigDecimal("500.00"), "Sibling scholarship concession");
        when(financeService.requestDiscount(any(), any(), any())).thenReturn(10L);

        mockMvc.perform(post("/api/v1/finance/discounts")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @DisplayName("POST /api/v1/finance/discounts/{id}/{decision} - approves discount")
    void decideDiscountSuccess() throws Exception {
        var req = new DecisionRequest("Approved by Principal");

        mockMvc.perform(post("/api/v1/finance/discounts/10/approve")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/finance/discounts/{id}/{decision} - invalid decision returns 400")
    void decideDiscountInvalidDecision() throws Exception {
        var req = new DecisionRequest("Invalid decision word");

        mockMvc.perform(post("/api/v1/finance/discounts/10/maybe")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/v1/finance/expenses - creates expense record")
    void createExpenseSuccess() throws Exception {
        var req = new ExpenseRequest("UTILITIES", "Electricity bill September", new BigDecimal("12500.00"), LocalDate.of(2026, 10, 2), "Power Corp", null);
        when(financeService.createExpense(any(), any(), any())).thenReturn(77L);

        mockMvc.perform(post("/api/v1/finance/expenses")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77));
    }

    @Test
    @DisplayName("POST /api/v1/finance/expenses/{id}/submit - submits expense for approval")
    void submitExpenseSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/finance/expenses/77/submit")
                        .with(authentication(authToken)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /api/v1/finance/expenses/{id}/{decision} - approves expense")
    void decideExpenseSuccess() throws Exception {
        var req = new DecisionRequest("Verified and approved for payout");

        mockMvc.perform(post("/api/v1/finance/expenses/77/approve")
                        .with(authentication(authToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/v1/finance/export - exports payments CSV")
    void exportPaymentsCsv() throws Exception {
        when(financeService.exportPayments(any(), any(), any()))
                .thenReturn("Receipt No,Date,Student,Amount\r\nRCP-01,2026-10-01,Aarav,5000.00\r\n");

        mockMvc.perform(get("/api/v1/finance/export")
                        .with(authentication(authToken))
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=finance-payments.csv"))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RCP-01")));
    }
}
