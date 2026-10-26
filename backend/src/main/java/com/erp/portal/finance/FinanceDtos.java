package com.erp.portal.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class FinanceDtos {
    private FinanceDtos() {}

    public record FeeStructureRequest(
            @Min(1) long sessionId,
            @Min(1) long classId,
            @Min(1) long categoryId,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            LocalDate dueDate,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @NotBlank @Pattern(regexp = "ONE_TIME|MONTHLY|QUARTERLY|ANNUALLY") String frequency) {}

    public record FeeStructure(long id, long sessionId, long classId, String className,
                               long categoryId, String categoryName, BigDecimal amount,
                               LocalDate dueDate, LocalDate effectiveFrom, LocalDate effectiveTo,
                               String frequency, boolean active) {}

    public record DemandRequest(@Min(1) long studentId, @Min(1) long feeStructureId,
                                @NotNull LocalDate billingPeriod) {}

    public record FeeDemand(long id, long studentId, String studentName, long feeStructureId,
                            long sessionId, LocalDate billingPeriod, BigDecimal amountDue,
                            BigDecimal amountPaid, BigDecimal discountAmount, BigDecimal balance,
                            LocalDate dueDate, String status) {}

    public record PaymentRequest(@Min(1) long demandId, @NotBlank @Size(max = 40) String referenceNo,
                                 @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                 @NotBlank @Pattern(regexp = "CASH|UPI|CARD|CHEQUE|NEFT|RTGS|DD|OTHER") String paymentMethod,
                                 @NotNull LocalDate paymentDate, @Size(max = 500) String remarks) {}

    public record Payment(long id, long studentId, long demandId, String receiptNo, String referenceNo,
                          BigDecimal amount, String paymentMethod, LocalDate paymentDate,
                          String status, LocalDateTime issuedAt) {}

    public record DiscountRequest(@Min(1) long demandId,
                                  @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                  @NotBlank @Size(max = 500) String reason) {}

    public record ExpenseRequest(@NotBlank @Size(max = 100) String category,
                                 @NotBlank @Size(max = 500) String description,
                                 @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                                 @NotNull LocalDate expenseDate,
                                 @Size(max = 200) String vendor,
                                 @Size(max = 1000) String receiptUrl) {}

    public record DecisionRequest(@NotBlank @Size(max = 500) String reason) {}
    public record FinanceSummary(BigDecimal collections, BigDecimal refunds, BigDecimal expenses,
                                 BigDecimal outstanding, long paymentCount, long expenseCount) {}
    public record Reconciliation(String recordType, long recordId, BigDecimal recordedAmount,
                                 BigDecimal calculatedAmount, BigDecimal difference) {}
        public record FinanceOptions(java.util.List<MapOption> sessions, java.util.List<MapOption> classes,
                                                                 java.util.List<MapOption> categories, java.util.List<MapOption> students) {}
        public record MapOption(long id, String label, Long sessionId, Long classId) {}
}