package com.erp.portal.finance;

import com.erp.portal.finance.FinanceDtos.DecisionRequest;
import com.erp.portal.finance.FinanceDtos.DiscountRequest;
import com.erp.portal.finance.FinanceDtos.ExpenseRequest;
import com.erp.portal.finance.FinanceDtos.DemandRequest;
import com.erp.portal.finance.FinanceDtos.FeeStructureRequest;
import com.erp.portal.finance.FinanceDtos.PaymentRequest;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.TestPropertySource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:finance_integration;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;NON_KEYWORDS=VALUE")
@Sql(scripts = "/finance-test-schema.sql")
class FinanceTransactionIntegrationTest {
    @Autowired FinanceService finance;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;
    private final HttpServletRequest request = null;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM audit_logs");
        jdbc.update("DELETE FROM finance_adjustments");
        jdbc.update("DELETE FROM receipts");
        jdbc.update("DELETE FROM payments");
        jdbc.update("DELETE FROM fee_discounts");
        jdbc.update("DELETE FROM expenses");
        jdbc.update("DELETE FROM fee_demand_items");
        jdbc.update("DELETE FROM fee_demands");
        jdbc.update("DELETE FROM fee_structure_items");
        jdbc.update("DELETE FROM student_enrollments");
        jdbc.update("DELETE FROM fee_structures");
        jdbc.update("DELETE FROM students");
        jdbc.update("DELETE FROM fee_categories");
        jdbc.update("DELETE FROM classes");
        jdbc.update("DELETE FROM academic_sessions");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users(id,username,email,password_hash,full_name) VALUES(1,'cashier','cashier@test.invalid','hash','Cashier')");
        jdbc.update("INSERT INTO users(id,username,email,password_hash,full_name) VALUES(2,'requester','requester@test.invalid','hash','Requester')");
        jdbc.update("INSERT INTO users(id,username,email,password_hash,full_name) VALUES(3,'approver','approver@test.invalid','hash','Approver')");
        jdbc.update("INSERT INTO academic_sessions(id,code,name,start_date,end_date) VALUES(1,'2026-27','2026-2027','2026-04-01','2027-03-31')");
        jdbc.update("INSERT INTO classes(id,name,order_seq) VALUES(1,'Class 1',1)");
        jdbc.update("INSERT INTO fee_categories(id,name) VALUES(1,'Tuition')");
        jdbc.update("INSERT INTO students(id,first_name,last_name,user_id,status) VALUES(1,'Taylor','Student',42,'ACTIVE')");
        jdbc.update("INSERT INTO student_enrollments(student_id,session_id,class_id) VALUES(1,1,1)");
        jdbc.update("INSERT INTO fee_structures(id,session_id,class_id,category_id,amount,effective_from,frequency) VALUES(1,1,1,1,100.00,'2026-04-01','MONTHLY')");
        jdbc.update("INSERT INTO fee_demands(id,student_id,fee_structure_id,session_id,amount_due,billing_period,status) VALUES(1,1,1,1,100.00,'2026-10-01','PENDING')");
    }

    @Test
    void paymentAndReceiptCommitTogetherAndIdenticalReferenceReplays() {
        var body = payment("PAY-ATOMIC", "100.00");
        var posted = finance.postPayment(body, cashier(), request);
        var replay = finance.postPayment(body, cashier(), request);
        assertThat(replay.id()).isEqualTo(posted.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM receipts", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount_paid FROM fee_demands WHERE id=1", BigDecimal.class)).isEqualByComparingTo("100.00");
    }

        @Test
        void feeStructureVersionsClosePriorDatesAndMonthlyDemandIsIdempotent() {
        var version = finance.createStructure(new FeeStructureRequest(1, 1, 1, new BigDecimal("125.00"),
            LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-01"), null, "MONTHLY"), cashier(), request);
        assertThat(version.effectiveFrom()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(jdbc.queryForObject("SELECT effective_to FROM fee_structures WHERE id=1", LocalDate.class)).isEqualTo(LocalDate.parse("2026-10-31"));
        var generated = finance.generateDemand(new DemandRequest(1, version.id(), LocalDate.parse("2026-11-19")), cashier(), request);
        assertThat(generated.billingPeriod()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(generated.amountDue()).isEqualByComparingTo("125.00");
        assertThatThrownBy(() -> finance.generateDemand(new DemandRequest(1, version.id(), LocalDate.parse("2026-11-02")), cashier(), request))
            .hasMessageContaining("already exists");
    }

    @Test
    void oneTimeFeeDemandUsesExactDateAndEnforcesEffectiveDates() {
        var version = finance.createStructure(new FeeStructureRequest(1, 1, 1, new BigDecimal("150.00"),
            LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-15"), LocalDate.parse("2026-12-31"), "ONE_TIME"), cashier(), request);
        var generated = finance.generateDemand(new DemandRequest(1, version.id(), LocalDate.parse("2026-10-16")), cashier(), request);
        assertThat(generated.billingPeriod()).isEqualTo(LocalDate.parse("2026-10-16"));
        assertThat(generated.amountDue()).isEqualByComparingTo("150.00");

        assertThatThrownBy(() -> finance.generateDemand(new DemandRequest(1, version.id(), LocalDate.parse("2026-10-14")), cashier(), request))
            .hasMessageContaining("outside the fee structure's effective dates");
    }

    @Test
    void concurrentDuplicateSubmissionCreatesOnePaymentAndReceipt() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return finance.postPayment(payment("PAY-RACE", "60.00"), cashier(), request); });
            var second = pool.submit(() -> { start.await(); return finance.postPayment(payment("PAY-RACE", "60.00"), cashier(), request); });
            start.countDown();
            var firstResult = first.get();
            var secondResult = second.get();
            assertThat(secondResult.id()).isEqualTo(firstResult.id());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM receipts", Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void receiptConstraintFailureRollsBackPaymentAndDemandMutation() {
        jdbc.update("INSERT INTO payments(student_id,session_id,receipt_no,reference_no,amount,payment_method,payment_date,collected_by) VALUES(1,1,'LEGACY','LEGACY',1,'CASH','2026-10-01',1)");
        long legacyId = jdbc.queryForObject("SELECT id FROM payments WHERE reference_no='LEGACY'", Long.class);
        jdbc.update("INSERT INTO receipts(payment_id,receipt_number,issued_by) VALUES(?,?,1)", legacyId, "R-ROLLBACK");
        assertThatThrownBy(() -> finance.postPayment(payment("ROLLBACK", "10.00"), cashier(), request))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM receipts", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount_paid FROM fee_demands WHERE id=1", BigDecimal.class)).isEqualByComparingTo("0.00");
    }

    @Test
    void rejectsInvalidAmountOverpaymentAndConflictingReference() {
        assertThatThrownBy(() -> finance.postPayment(payment("ZERO", "0.00"), cashier(), request)).hasMessageContaining("positive");
        assertThatThrownBy(() -> finance.postPayment(payment("OVER", "100.01"), cashier(), request)).hasMessageContaining("exceeds");
        finance.postPayment(payment("UNIQUE", "10.00"), cashier(), request);
        assertThatThrownBy(() -> finance.postPayment(payment("UNIQUE", "11.00"), cashier(), request)).hasMessageContaining("already been used");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isEqualTo(1);
    }

    @Test
    void discountAndExpenseWorkflowsEnforceSeparationOfDuties() {
        long discountId = finance.requestDiscount(new DiscountRequest(1, new BigDecimal("20.00"), "Need-based"), requester(), request);
        assertThatThrownBy(() -> finance.decideDiscount(discountId, true, new DecisionRequest("Approve"), requester(), request))
                .hasMessageContaining("Requester cannot approve");
        finance.decideDiscount(discountId, true, new DecisionRequest("Approved by manager"), approver(), request);
        assertThat(jdbc.queryForObject("SELECT approval_status FROM fee_discounts WHERE id=?", String.class, discountId)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT discount_amount FROM fee_demands WHERE id=1", BigDecimal.class)).isEqualByComparingTo("20.00");
        long rejectedDiscountId = finance.requestDiscount(new DiscountRequest(1, new BigDecimal("10.00"), "Second request"), requester(), request);
        finance.decideDiscount(rejectedDiscountId, false, new DecisionRequest("Insufficient evidence"), approver(), request);
        assertThat(jdbc.queryForObject("SELECT approval_status FROM fee_discounts WHERE id=?", String.class, rejectedDiscountId)).isEqualTo("REJECTED");
        jdbc.update("INSERT INTO fee_discounts(student_id,demand_id,amount,reason,requested_by) VALUES(1,1,1.00,'Other request',3)");
        assertThat(finance.discounts(requester())).hasSize(2);
        assertThat(finance.discounts(approver())).hasSize(3);

        long expenseId = finance.createExpense(new ExpenseRequest("Supplies", "Classroom materials", new BigDecimal("15.25"),
                LocalDate.parse("2026-10-06"), "Stationery", null), requester(), request);
        finance.submitExpense(expenseId, requester(), request);
        assertThatThrownBy(() -> finance.decideExpense(expenseId, true, new DecisionRequest("Self approval"), requester(), request))
                .hasMessageContaining("Requester cannot approve");
        finance.decideExpense(expenseId, true, new DecisionRequest("Verified"), approver(), request);
        assertThat(jdbc.queryForObject("SELECT approval_status FROM expenses WHERE id=?", String.class, expenseId)).isEqualTo("APPROVED");
        long rejectedExpenseId = finance.createExpense(new ExpenseRequest("Travel", "Unapproved trip", new BigDecimal("8.00"),
            LocalDate.parse("2026-10-06"), null, null), requester(), request);
        finance.submitExpense(rejectedExpenseId, requester(), request);
        finance.decideExpense(rejectedExpenseId, false, new DecisionRequest("Missing approval"), approver(), request);
        assertThat(jdbc.queryForObject("SELECT approval_status FROM expenses WHERE id=?", String.class, rejectedExpenseId)).isEqualTo("REJECTED");
        jdbc.update("INSERT INTO expenses(category,description,amount,expense_date,requested_by) VALUES('Other','Private expense',1.00,'2026-10-06',3)");
        assertThat(finance.expenses(requester())).hasSize(2);
        assertThat(finance.expenses(approver())).hasSize(3);
    }

    @Test
    void refundPreservesPaymentReceiptAndAdjustmentEvidence() {
        var payment = finance.postPayment(payment("REFUND-1", "25.00"), cashier(), request);
        finance.adjustPayment(payment.id(), "refund", new DecisionRequest("Duplicate bank transfer"), approver(), request);
        var second = finance.postPayment(payment("REVERSE-1", "25.00"), cashier(), request);
        finance.adjustPayment(second.id(), "reverse", new DecisionRequest("Cash entry correction"), approver(), request);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, payment.id())).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, second.id())).isEqualTo("REVERSED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM receipts WHERE payment_id=?", Integer.class, payment.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM receipts", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM finance_adjustments", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action IN ('FINANCE_PAYMENT_REFUND','FINANCE_PAYMENT_REVERSAL')", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT amount_paid FROM fee_demands WHERE id=1", BigDecimal.class)).isEqualByComparingTo("0.00");
        assertThat(finance.reconcile(approver())).isEmpty();
    }

    @Test
    @WithMockUser(authorities = "FEE:COLLECT")
    void cashierCannotRefundOrExportAndDeniedRequestsDoNotMutate() throws Exception {
        var payment = finance.postPayment(payment("AUTH-1", "10.00"), cashier(), request);
        mockMvc.perform(post("/api/v1/finance/payments/{id}/refund", payment.id()).contentType("application/json").content("{\"reason\":\"not allowed\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/finance/export").param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id=?", String.class, payment.id())).isEqualTo("POSTED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM finance_adjustments", Integer.class)).isZero();
    }

    @Test
    @WithMockUser(authorities = "FEE:COLLECT")
    void cashierCanReadDemandThroughFinanceApi() throws Exception {
        mockMvc.perform(get("/api/v1/finance/demands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].balance").value(100.0));
    }

    @Test
    @WithMockUser(authorities = "FINANCE_REPORT:EXPORT")
    void financeExportPermissionReturnsOnlyRequestedDateRange() throws Exception {
        finance.postPayment(payment("EXPORT-1", "12.50"), cashier(), request);
        finance.postPayment(new PaymentRequest(1, "EXPORT-OUTSIDE", new BigDecimal("5.00"), "CASH", LocalDate.parse("2026-11-01"), null), cashier(), request);
        mockMvc.perform(get("/api/v1/finance/export").param("from", "2026-10-01").param("to", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("EXPORT-1")))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("EXPORT-OUTSIDE"))));
    }

    @Test
    void financeCsvExportNeutralizesSpreadsheetFormulaReferences() {
        finance.postPayment(payment("=1+1", "12.50"), cashier(), request);

        assertThat(finance.exportPayments(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), cashier()))
                .contains("'=1+1");
    }

    @Test
    void studentFeeReadsAndExportsAreRestrictedToOwnRecords() {
        jdbc.update("INSERT INTO students(id,first_name,last_name,user_id,status) VALUES(2,'Other','Student',99,'ACTIVE')");
        jdbc.update("INSERT INTO fee_demands(id,student_id,fee_structure_id,session_id,amount_due,billing_period,status) VALUES(2,2,1,1,100.00,'2026-10-01','PENDING')");
        finance.postPayment(payment("OWN-FEE", "12.50"), cashier(), request);
        finance.postPayment(new PaymentRequest(2, "OTHER-FEE", new BigDecimal("20.00"), "CASH", LocalDate.parse("2026-10-06"), null), cashier(), request);
        var student = new SecurityPrincipal(42, "student", "Student", List.of("STUDENT"), Set.of("FEE:READ"), "student-hash");

        assertThat(finance.demands(student)).extracting("id").containsExactly(1L);
        assertThat(finance.payments(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), student))
                .extracting("referenceNo").containsExactly("OWN-FEE");
        assertThat(finance.exportPayments(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), student))
                .contains("OWN-FEE").doesNotContain("OTHER-FEE");
        assertThat(finance.summary(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), student).collections())
                .isEqualByComparingTo("12.50");
        assertThat(finance.summary(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), student).outstanding())
                .isEqualByComparingTo("87.50");
        jdbc.update("INSERT INTO fee_discounts(student_id,demand_id,amount,reason,requested_by) VALUES(1,1,1.00,'Own',2)");
        jdbc.update("INSERT INTO fee_discounts(student_id,demand_id,amount,reason,requested_by) VALUES(2,2,1.00,'Other',2)");
        jdbc.update("UPDATE fee_demands SET amount_paid=amount_paid+1 WHERE id IN (1,2)");
        assertThat(finance.discounts(student)).extracting("studentId").containsExactly(1L);
        assertThat(finance.reconcile(student)).extracting("recordId").containsExactly(1L);
    }

        @Test
        void financeSummaryDerivesCollectionsAndApprovedExpensesFromTransactions() {
        finance.postPayment(payment("REPORT-1", "25.00"), cashier(), request);
        long expenseId = finance.createExpense(new ExpenseRequest("Utilities", "Power", new BigDecimal("7.75"),
            LocalDate.parse("2026-10-06"), null, null), requester(), request);
        finance.submitExpense(expenseId, requester(), request);
        finance.decideExpense(expenseId, true, new DecisionRequest("Invoice checked"), approver(), request);
        var result = finance.summary(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), approver());
        assertThat(result.collections()).isEqualByComparingTo("25.00");
        assertThat(result.expenses()).isEqualByComparingTo("7.75");
        assertThat(result.outstanding()).isEqualByComparingTo("75.00");
        assertThat(result.paymentCount()).isEqualTo(1);
        assertThat(result.expenseCount()).isEqualTo(1);
    }

    @Test
    void deleteDemandRemovesDemandAndItemsWhenUnpaid() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fee_demands WHERE id=1", Integer.class)).isEqualTo(1);
        finance.deleteDemand(1, cashier(), request);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fee_demands WHERE id=1", Integer.class)).isEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fee_demand_items WHERE fee_demand_id=1", Integer.class)).isEqualTo(0);
    }

    @Test
    void deleteDemandFailsWhenPaymentsExist() {
        finance.postPayment(payment("DELETE-PAY", "50.00"), cashier(), request);
        assertThatThrownBy(() -> finance.deleteDemand(1, cashier(), request))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Cannot delete a fee demand with posted payments");
    }

    @Test
    @WithMockUser(authorities = "FINANCE_REPORT:EXPORT")
    void exportPaymentsReturnsCsv() throws Exception {
        finance.postPayment(payment("EXPORT-1", "30.00"), cashier(), request);
        mockMvc.perform(get("/api/v1/finance/export")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .header("Accept", "text/csv, text/plain, */*"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("EXPORT-1")));
    }

    @Test
    @WithMockUser(authorities = "FEE:READ")
    void getReceiptReturnsPaymentDetails() throws Exception {
        var p = finance.postPayment(payment("REC-TEST", "45.00"), cashier(), request);
        mockMvc.perform(get("/api/v1/finance/receipts/" + p.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNo").value(p.receiptNo()))
                .andExpect(jsonPath("$.amount").value(45.0));
    }

    @Test
    void expensesScopesByRequesterWhenNotApproved() {
        long e1 = finance.createExpense(new ExpenseRequest("Supplies", "Items A", new BigDecimal("10.00"),
                LocalDate.now(), "Vendor A", null), requester(), request);
        long e2 = finance.createExpense(new ExpenseRequest("Supplies", "Items B", new BigDecimal("20.00"),
                LocalDate.now(), "Vendor B", null), cashier(), request);

        List<Map<String, Object>> requesterExpenses = finance.expenses(requester());
        assertThat(requesterExpenses).extracting(m -> (Long) m.get("id")).contains(e1).doesNotContain(e2);

        List<Map<String, Object>> approverExpenses = finance.expenses(approver());
        assertThat(approverExpenses).extracting(m -> (Long) m.get("id")).contains(e1, e2);
    }

    private static PaymentRequest payment(String reference, String amount) {
        return new PaymentRequest(1, reference, new BigDecimal(amount), "CASH", LocalDate.parse("2026-10-06"), null);
    }
    private static SecurityPrincipal cashier() {
        return new SecurityPrincipal(1, "cashier", "Cashier", List.of("ACCOUNTANT"), Set.of("FEE:COLLECT"), "hash");
    }
    private static SecurityPrincipal requester() {
        return new SecurityPrincipal(2, "requester", "Requester", List.of("ACCOUNTANT"), Set.of("FEE_DISCOUNT:REQUEST", "EXPENSE:MANAGE"), "hash");
    }
    private static SecurityPrincipal approver() {
        return new SecurityPrincipal(3, "approver", "Approver", List.of("MANAGEMENT"), Set.of("FEE_DISCOUNT:APPROVE", "EXPENSE:APPROVE", "FEE:REFUND"), "hash");
    }
}
