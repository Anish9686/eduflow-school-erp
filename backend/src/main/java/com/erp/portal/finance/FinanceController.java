package com.erp.portal.finance;

import com.erp.portal.finance.FinanceDtos.*;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance")
public class FinanceController {
    private final FinanceService service;
    public FinanceController(FinanceService service) { this.service = service; }

    @GetMapping("/options") @PreAuthorize("hasAnyAuthority('FEE:READ','FEE:MANAGE','FEE:COLLECT','FEE:REFUND','FEE_DISCOUNT:REQUEST','FEE_DISCOUNT:APPROVE','EXPENSE:MANAGE','EXPENSE:APPROVE','FINANCE_REPORT:READ','FINANCE_REPORT:EXPORT')")
    public FinanceOptions options(@AuthenticationPrincipal SecurityPrincipal actor) { return service.options(actor); }
    @GetMapping("/structures") @PreAuthorize("hasAnyAuthority('FEE:READ','FEE:MANAGE','FEE:COLLECT')")
    public List<FeeStructure> structures() { return service.structures(); }
    @PostMapping("/structures") @PreAuthorize("hasAuthority('FEE:MANAGE')")
    public FeeStructure createStructure(@Valid @RequestBody FeeStructureRequest body, @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) { 
        return service.createStructure(body, actor, request); 
    }
    @GetMapping("/demands") @PreAuthorize("hasAnyAuthority('FEE:READ','FEE:MANAGE','FEE:COLLECT','FEE_DISCOUNT:REQUEST')")
    public List<FeeDemand> demands(@AuthenticationPrincipal SecurityPrincipal actor) { return service.demands(actor); }
    @PostMapping("/demands") @PreAuthorize("hasAuthority('FEE:MANAGE')")
    public FeeDemand generateDemand(@Valid @RequestBody DemandRequest body, @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) { 
        return service.generateDemand(body, actor, request); 
    }
    @DeleteMapping("/demands/{id}") @PreAuthorize("hasAuthority('FEE:MANAGE')")
    public ResponseEntity<Void> deleteDemand(@PathVariable @Min(1) long id, @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) {
        service.deleteDemand(id, actor, request);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/payments") @PreAuthorize("hasAnyAuthority('FEE:READ','FEE:COLLECT','FEE:REFUND')")
    public List<Payment> payments(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @AuthenticationPrincipal SecurityPrincipal actor) { return service.payments(from, to, actor); }
    @PostMapping("/payments") @PreAuthorize("hasAuthority('FEE:COLLECT')")
    public Payment postPayment(@Valid @RequestBody PaymentRequest body, @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) { 
        return service.postPayment(body, actor, request); 
    }
    @GetMapping("/receipts/{paymentId}") @PreAuthorize("hasAnyAuthority('FEE:READ','FEE:COLLECT','FEE:REFUND')")
    public Payment receipt(@PathVariable @Min(1) long paymentId, @AuthenticationPrincipal SecurityPrincipal actor) { return service.receipt(paymentId, actor); }
    @PostMapping("/payments/{paymentId}/{operation}") @PreAuthorize("hasAuthority('FEE:REFUND')")
    public ResponseEntity<Void> adjustPayment(@PathVariable @Min(1) long paymentId, @PathVariable String operation,
                                               @Valid @RequestBody DecisionRequest body, @AuthenticationPrincipal SecurityPrincipal actor,
                                               HttpServletRequest request) { service.adjustPayment(paymentId, operation, body, actor, request); return ResponseEntity.noContent().build(); }
    @GetMapping("/discounts") @PreAuthorize("hasAnyAuthority('FEE:READ','FEE_DISCOUNT:REQUEST','FEE_DISCOUNT:APPROVE')")
    public List<Map<String, Object>> discounts(@AuthenticationPrincipal SecurityPrincipal actor) { return service.discounts(actor); }
    @PostMapping("/discounts") @PreAuthorize("hasAuthority('FEE_DISCOUNT:REQUEST')")
    public Map<String, Long> requestDiscount(@Valid @RequestBody DiscountRequest body, @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) { 
        return Map.of("id", service.requestDiscount(body, actor, request)); 
    }
    @PostMapping("/discounts/{id}/{decision}") @PreAuthorize("hasAuthority('FEE_DISCOUNT:APPROVE')")
    public ResponseEntity<Void> decideDiscount(@PathVariable @Min(1) long id, @PathVariable String decision,
                                                @Valid @RequestBody DecisionRequest body, @AuthenticationPrincipal SecurityPrincipal actor,
                                                HttpServletRequest request) { 
        requireDecision(decision); 
        service.decideDiscount(id, decision.equals("approve"), body, actor, request); 
        return ResponseEntity.noContent().build(); 
    }
    @GetMapping("/expenses") @PreAuthorize("hasAnyAuthority('EXPENSE:MANAGE','EXPENSE:APPROVE','FINANCE_REPORT:READ')")
    public List<Map<String, Object>> expenses(@AuthenticationPrincipal SecurityPrincipal actor) { return service.expenses(actor); }
    @PostMapping("/expenses") @PreAuthorize("hasAuthority('EXPENSE:MANAGE')")
    public Map<String, Long> createExpense(@Valid @RequestBody ExpenseRequest body, @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) { return Map.of("id", service.createExpense(body, actor, request)); }
    @PostMapping("/expenses/{id}/submit") @PreAuthorize("hasAuthority('EXPENSE:MANAGE')")
    public ResponseEntity<Void> submitExpense(@PathVariable @Min(1) long id, @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) { service.submitExpense(id, actor, request); return ResponseEntity.noContent().build(); }
    @PostMapping("/expenses/{id}/{decision}") @PreAuthorize("hasAuthority('EXPENSE:APPROVE')")
    public ResponseEntity<Void> decideExpense(@PathVariable @Min(1) long id, @PathVariable String decision,
                                               @Valid @RequestBody DecisionRequest body, @AuthenticationPrincipal SecurityPrincipal actor,
                                               HttpServletRequest request) { 
        requireDecision(decision); 
        service.decideExpense(id, decision.equals("approve"), body, actor, request); 
        return ResponseEntity.noContent().build(); 
    }
        private static void requireDecision(String decision) {
            if (!decision.equals("approve") && !decision.equals("reject")) {
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be approve or reject");
            }
        }
    @GetMapping("/reports") @PreAuthorize("hasAuthority('FINANCE_REPORT:READ')")
    public FinanceSummary summary(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @AuthenticationPrincipal SecurityPrincipal actor) { return service.summary(from, to, actor); }
    @GetMapping("/reconciliation") @PreAuthorize("hasAuthority('FINANCE_REPORT:READ')")
    public List<Reconciliation> reconciliation(@AuthenticationPrincipal SecurityPrincipal actor) { return service.reconcile(actor); }
    @GetMapping(value = "/export", produces = {"text/csv", "text/plain", "*/*"}) @PreAuthorize("hasAuthority('FINANCE_REPORT:EXPORT')")
    public ResponseEntity<String> export(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @AuthenticationPrincipal SecurityPrincipal actor) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=finance-payments.csv")
                .contentType(MediaType.parseMediaType("text/csv")).body(service.exportPayments(from, to, actor));
    }
}
