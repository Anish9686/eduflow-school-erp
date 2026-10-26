package com.erp.portal.finance;

import com.erp.portal.administration.AuditService;
import com.erp.portal.finance.FinanceDtos.*;
import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.common.export.CsvSafety;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class FinanceService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public FinanceService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<FeeStructure> structures() {
        return jdbc.query("SELECT fs.id,fs.session_id,fs.class_id,c.name class_name,fs.category_id,fc.name category_name,fs.amount,fs.due_date,fs.effective_from,fs.effective_to,fs.frequency,fs.is_active " +
                        "FROM fee_structures fs JOIN classes c ON c.id=fs.class_id JOIN fee_categories fc ON fc.id=fs.category_id ORDER BY fs.session_id DESC,c.order_seq,fc.name",
                (r, n) -> new FeeStructure(r.getLong("id"), r.getLong("session_id"), r.getLong("class_id"), r.getString("class_name"),
                        r.getLong("category_id"), r.getString("category_name"), r.getBigDecimal("amount"), r.getObject("due_date", LocalDate.class),
                        r.getObject("effective_from", LocalDate.class), r.getObject("effective_to", LocalDate.class),
                        r.getString("frequency"), r.getBoolean("is_active")));
    }

        @Transactional(readOnly = true)
        public FinanceOptions options(SecurityPrincipal actor) {
                var sessions = jdbc.query("SELECT id,name FROM academic_sessions ORDER BY start_date DESC", (r, n) -> new MapOption(r.getLong("id"), r.getString("name"), null, null));
                var classes = jdbc.query("SELECT id,name FROM classes ORDER BY order_seq,name", (r, n) -> new MapOption(r.getLong("id"), r.getString("name"), null, null));
                var categories = jdbc.query("SELECT id,name FROM fee_categories WHERE is_active=1 ORDER BY name", (r, n) -> new MapOption(r.getLong("id"), r.getString("name"), null, null));
                StudentScope scope = studentScope(actor, "s");
                var students = jdbc.query("SELECT e.student_id,s.admission_no,s.first_name,s.last_name,e.session_id,e.class_id FROM student_enrollments e JOIN students s ON s.id=e.student_id WHERE s.status='ACTIVE' AND " + scope.predicate() + " ORDER BY s.first_name,s.last_name",
                                (r, n) -> {
                                        String name = (r.getString("first_name") + " " + (r.getString("last_name") == null ? "" : r.getString("last_name"))).trim();
                                        String admissionNo = r.getString("admission_no");
                                        String label = admissionNo != null && !admissionNo.isBlank() ? name + " (" + admissionNo + ")" : name;
                                        return new MapOption(r.getLong("student_id"), label, r.getLong("session_id"), r.getLong("class_id"));
                                }, scope.args().toArray());
                return new FinanceOptions(sessions, classes, categories, students);
        }

    @Transactional
    public FeeStructure createStructure(FeeStructureRequest x, SecurityPrincipal actor, HttpServletRequest request) {
        requireExists("academic_sessions", x.sessionId(), "Academic session");
        requireExists("classes", x.classId(), "Class");
        requireExists("fee_categories", x.categoryId(), "Fee category");
        if (x.effectiveTo() != null && x.effectiveTo().isBefore(x.effectiveFrom())) throw invalid("effectiveTo", "must be on or after effectiveFrom");
        jdbc.update("UPDATE fee_structures SET effective_to=? WHERE session_id=? AND class_id=? AND category_id=? AND is_active=1 " +
                        "AND effective_to IS NULL AND effective_from<?",
                x.effectiveFrom().minusDays(1), x.sessionId(), x.classId(), x.categoryId(), x.effectiveFrom());
        Long overlap = jdbc.queryForObject("SELECT COUNT(*) FROM fee_structures WHERE session_id=? AND class_id=? AND category_id=? AND is_active=1 " +
                        "AND COALESCE(effective_to,DATE '9999-12-31')>=? AND effective_from<=COALESCE(?,DATE '9999-12-31')",
                Long.class, x.sessionId(), x.classId(), x.categoryId(), x.effectiveFrom(), x.effectiveTo());
        if (overlap != null && overlap > 0) throw conflict("An active fee structure already covers part of this effective date range");
        long id = insert("INSERT INTO fee_structures(session_id,class_id,category_id,amount,due_date,effective_from,effective_to,frequency,created_by) VALUES(?,?,?,?,?,?,?,?,?)",
                x.sessionId(), x.classId(), x.categoryId(), x.amount(), x.dueDate(), x.effectiveFrom(), x.effectiveTo(), x.frequency(), actor.userId());
        audit.record("FINANCE_FEE_STRUCTURE_CREATED", "FEE_STRUCTURE", Long.toString(id), actor, null, null,
                Map.of("sessionId", x.sessionId(), "classId", x.classId(), "categoryId", x.categoryId(), "amount", x.amount()), request);
        return structures().stream().filter(row -> row.id() == id).findFirst().orElseThrow();
    }

    @Transactional
    public FeeDemand generateDemand(DemandRequest x, SecurityPrincipal actor, HttpServletRequest request) {
        var structure = jdbc.query("SELECT fs.session_id,fs.class_id,fs.amount,fs.due_date,fc.name category_name,fs.frequency,fs.effective_from,fs.effective_to FROM fee_structures fs " +
                        "JOIN fee_categories fc ON fc.id=fs.category_id WHERE fs.id=? AND fs.is_active=1",
                (r, n) -> new Object[]{r.getLong("session_id"), r.getLong("class_id"), r.getBigDecimal("amount"),
                        r.getObject("due_date", LocalDate.class), r.getString("category_name"), r.getString("frequency"),
                        r.getObject("effective_from", LocalDate.class), r.getObject("effective_to", LocalDate.class)}, x.feeStructureId())
                .stream().findFirst().orElseThrow(() -> notFound("Active fee structure"));
        long sessionId = (long) structure[0];
        long classId = (long) structure[1];
        LocalDate billingPeriod = normalizePeriod(x.billingPeriod(), (String) structure[5]);
        LocalDate effectiveFrom = (LocalDate) structure[6];
        LocalDate effectiveTo = (LocalDate) structure[7];
        if (x.billingPeriod().isBefore(effectiveFrom) || (effectiveTo != null && x.billingPeriod().isAfter(effectiveTo))) {
            throw invalid("billingPeriod", "is outside the fee structure's effective dates");
        }
        Integer enrolled = jdbc.queryForObject("SELECT COUNT(*) FROM student_enrollments WHERE student_id=? AND session_id=? AND class_id=?",
                Integer.class, x.studentId(), sessionId, classId);
        if (enrolled == null || enrolled == 0) throw invalid("studentId", "student must be enrolled in the fee structure's class and session");
        if ("ONE_TIME".equals(structure[5])) {
            Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM fee_demands WHERE student_id=? AND fee_structure_id=? AND session_id=?",
                    Integer.class, x.studentId(), x.feeStructureId(), sessionId);
            if (existing != null && existing > 0) throw conflict("A one-time fee demand already exists for this student and structure");
        }
        BigDecimal lineTotal = jdbc.queryForObject("SELECT COALESCE(SUM(amount),0) FROM fee_structure_items WHERE fee_structure_id=?", BigDecimal.class, x.feeStructureId());
        BigDecimal amountDue = lineTotal == null || lineTotal.signum() == 0 ? (BigDecimal) structure[2] : lineTotal;
        try {
            long id = insert("INSERT INTO fee_demands(student_id,fee_structure_id,session_id,amount_due,due_date,billing_period) VALUES(?,?,?,?,?,?)",
                    x.studentId(), x.feeStructureId(), sessionId, amountDue, structure[3], billingPeriod);
            int copied = jdbc.update("INSERT INTO fee_demand_items(fee_demand_id,fee_structure_item_id,description,amount_due) " +
                    "SELECT ?,id,item_name,amount FROM fee_structure_items WHERE fee_structure_id=?", id, x.feeStructureId());
            if (copied == 0) jdbc.update("INSERT INTO fee_demand_items(fee_demand_id,description,amount_due) VALUES(?,?,?)", id, structure[4], amountDue);
            audit.record("FINANCE_DEMAND_GENERATED", "FEE_DEMAND", Long.toString(id), actor, null, null,
                    Map.of("studentId", x.studentId(), "feeStructureId", x.feeStructureId(), "billingPeriod", billingPeriod), request);
            return demand(id);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("A demand already exists for this student, fee structure and billing period");
        }
    }

    @Transactional
    public void deleteDemand(long id, SecurityPrincipal actor, HttpServletRequest request) {
        DemandBalance balance = lockDemand(id);
        if (balance == null) throw notFound("Fee demand");
        if (balance.amountPaid().signum() > 0) {
            throw conflict("Cannot delete a fee demand with posted payments");
        }
        Integer discountCount = jdbc.queryForObject("SELECT COUNT(*) FROM fee_discounts WHERE demand_id=?", Integer.class, id);
        if (discountCount != null && discountCount > 0) {
            throw conflict("Cannot delete a fee demand with discount requests");
        }
        Integer paymentCount = jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE demand_id=?", Integer.class, id);
        if (paymentCount != null && paymentCount > 0) {
            throw conflict("Cannot delete a fee demand with associated payments");
        }
        jdbc.update("DELETE FROM fee_demand_items WHERE fee_demand_id=?", id);
        jdbc.update("DELETE FROM fee_demands WHERE id=?", id);
        audit.record("FINANCE_DEMAND_DELETED", "FEE_DEMAND", Long.toString(id), actor, null, null,
                Map.of("demandId", id, "studentId", balance.studentId()), request);
    }

    @Transactional(readOnly = true)
    public List<FeeDemand> demands(SecurityPrincipal actor) {
        StudentScope scope = studentScope(actor, "s");
        return jdbc.query("SELECT fd.id,fd.student_id,CONCAT(s.first_name,' ',COALESCE(s.last_name,'')) student_name,fd.fee_structure_id,fd.session_id,fd.billing_period,fd.amount_due,fd.amount_paid,fd.discount_amount,fd.due_date,fd.status " +
                        "FROM fee_demands fd JOIN students s ON s.id=fd.student_id WHERE " + scope.predicate() + " ORDER BY fd.due_date,fd.id DESC",
                (r, n) -> mapDemand(r.getLong("id"), r.getLong("student_id"), r.getString("student_name"), r.getLong("fee_structure_id"),
                        r.getLong("session_id"), r.getObject("billing_period", LocalDate.class), r.getBigDecimal("amount_due"),
                        r.getBigDecimal("amount_paid"), r.getBigDecimal("discount_amount"), r.getObject("due_date", LocalDate.class), r.getString("status")), scope.args().toArray());
    }

    @Transactional
    public Payment postPayment(PaymentRequest x, SecurityPrincipal actor, HttpServletRequest request) {
        String reference = x.referenceNo().trim();
        var existing = paymentByReference(reference);
        if (existing != null) return replayOrConflict(existing, x);
        DemandBalance balance = lockDemand(x.demandId());
        if (balance == null) throw notFound("Fee demand");
        existing = paymentByReference(reference);
        if (existing != null) return replayOrConflict(existing, x);
        BigDecimal outstanding = balance.amountDue().subtract(balance.amountPaid()).subtract(balance.discountAmount());
        if (x.amount().signum() <= 0 || x.amount().scale() > 2) throw invalid("amount", "must be positive and have no more than two decimal places");
        if (x.amount().compareTo(outstanding) > 0) throw conflict("Payment exceeds the outstanding demand balance");
        if (outstanding.signum() <= 0) throw conflict("Fee demand is already settled");
        String receiptNo = "R-" + reference;
                long id;
        try {
            id = insert("INSERT INTO payments(student_id,demand_id,session_id,receipt_no,reference_no,amount,payment_method,payment_date,remarks,collected_by) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?)", balance.studentId(), x.demandId(), balance.sessionId(), receiptNo, reference,
                    x.amount(), x.paymentMethod(), x.paymentDate(), x.remarks(), actor.userId());
        } catch (DataIntegrityViolationException exception) {
            Payment replay = paymentByReference(reference);
            if (replay != null) return replayOrConflict(replay, x);
            throw conflict("Payment reference or receipt number already exists");
        }
                jdbc.update("INSERT INTO receipts(payment_id,receipt_number,issued_by) VALUES(?,?,?)", id, receiptNo, actor.userId());
                BigDecimal paid = balance.amountPaid().add(x.amount());
                String status = paid.add(balance.discountAmount()).compareTo(balance.amountDue()) >= 0 ? "PAID" : "PARTIAL";
                jdbc.update("UPDATE fee_demands SET amount_paid=?,status=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?", paid, status, x.demandId());
        audit.record("FINANCE_PAYMENT_POSTED", "PAYMENT", Long.toString(id), actor, null, null,
                Map.of("demandId", x.demandId(), "referenceNo", reference, "amount", x.amount(), "receiptNo", receiptNo), request);
        return payment(id);
    }

    @Transactional(readOnly = true)
    public List<Payment> payments(LocalDate from, LocalDate to, SecurityPrincipal actor) {
                validateRange(from, to);
        StudentScope scope = studentScope(actor, "s");
        return jdbc.query("SELECT p.id,p.student_id,p.demand_id,p.receipt_no,p.reference_no,p.amount,p.payment_method,p.payment_date,p.status,r.issued_at " +
                        "FROM payments p JOIN students s ON s.id=p.student_id LEFT JOIN receipts r ON r.payment_id=p.id WHERE p.payment_date BETWEEN ? AND ? AND " + scope.predicate() + " ORDER BY p.payment_date DESC,p.id DESC",
                (r, n) -> paymentRow(r), concat(List.of(from, to), scope.args()).toArray());
    }

    @Transactional(readOnly = true)
    public Payment receipt(long paymentId, SecurityPrincipal actor) {
        StudentScope scope = studentScope(actor, "s");
        Payment value = jdbc.query("SELECT p.id,p.student_id,p.demand_id,p.receipt_no,p.reference_no,p.amount,p.payment_method,p.payment_date,p.status,r.issued_at " +
                        "FROM payments p JOIN students s ON s.id=p.student_id LEFT JOIN receipts r ON r.payment_id=p.id WHERE p.id=? AND " + scope.predicate(),
                (r, n) -> paymentRow(r), concat(List.of(paymentId), scope.args()).toArray()).stream().findFirst().orElse(null);
        if (value == null) throw notFound("Receipt");
        return value;
    }

    @Transactional
    public void adjustPayment(long paymentId, String type, DecisionRequest x, SecurityPrincipal actor, HttpServletRequest request) {
        if (!type.equals("refund") && !type.equals("reverse")) throw notFound("Finance operation");
        var payment = jdbc.query("SELECT id,student_id,demand_id,amount,status FROM payments WHERE id=? FOR UPDATE",
                (r, n) -> new Object[]{r.getLong("id"), r.getLong("student_id"), (Long) r.getObject("demand_id"), r.getBigDecimal("amount"), r.getString("status")}, paymentId)
                .stream().findFirst().orElseThrow(() -> notFound("Payment"));
        if (!"POSTED".equals(payment[4])) throw conflict("Only a posted payment can be refunded or reversed");
        String kind = type.equals("refund") ? "REFUND" : "REVERSAL";
        jdbc.update("INSERT INTO finance_adjustments(payment_id,adjustment_type,amount,reason,requested_by) VALUES(?,?,?,?,?)",
                paymentId, kind, payment[3], x.reason().trim(), actor.userId());
        jdbc.update("UPDATE payments SET status=?,refunded_by=?,refunded_at=CURRENT_TIMESTAMP(6),refund_reason=? WHERE id=?",
                type.equals("refund") ? "REFUNDED" : "REVERSED", actor.userId(), x.reason().trim(), paymentId);
        if (payment[2] != null) {
            DemandBalance balance = lockDemand((long) payment[2]);
            BigDecimal paid = balance.amountPaid().subtract((BigDecimal) payment[3]);
            String status = paid.signum() == 0 ? "PENDING"
                    : paid.add(balance.discountAmount()).compareTo(balance.amountDue()) >= 0 ? "PAID" : "PARTIAL";
            jdbc.update("UPDATE fee_demands SET amount_paid=?,status=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?", paid, status, payment[2]);
        }
        audit.record("FINANCE_PAYMENT_" + kind, "PAYMENT", Long.toString(paymentId), actor, null,
                Map.of("status", "POSTED"), Map.of("status", kind, "amount", payment[3], "reason", x.reason()), request);
    }

    @Transactional
    public long requestDiscount(DiscountRequest x, SecurityPrincipal actor, HttpServletRequest request) {
        DemandBalance balance = lockDemand(x.demandId());
        if (balance == null) throw notFound("Fee demand");
        BigDecimal outstanding = balance.amountDue().subtract(balance.amountPaid()).subtract(balance.discountAmount());
        if (x.amount().compareTo(outstanding) > 0) throw conflict("Discount exceeds the outstanding demand balance");
        long id = insert("INSERT INTO fee_discounts(student_id,demand_id,amount,reason,requested_by) VALUES(?,?,?,?,?)",
                balance.studentId(), x.demandId(), x.amount(), x.reason().trim(), actor.userId());
        audit.record("FINANCE_DISCOUNT_REQUESTED", "FEE_DISCOUNT", Long.toString(id), actor, null, null,
                Map.of("demandId", x.demandId(), "amount", x.amount(), "reason", x.reason()), request);
        return id;
    }

    @Transactional
    public void decideDiscount(long id, boolean approve, DecisionRequest x, SecurityPrincipal actor, HttpServletRequest request) {
        var discount = jdbc.query("SELECT demand_id,amount,requested_by,approval_status FROM fee_discounts WHERE id=? FOR UPDATE",
                (r, n) -> new Object[]{r.getLong("demand_id"), r.getBigDecimal("amount"), r.getLong("requested_by"), r.getString("approval_status")}, id)
                .stream().findFirst().orElseThrow(() -> notFound("Discount request"));
        if (!"PENDING".equals(discount[3])) throw conflict("Discount request has already been decided");
        if (approve && ((long) discount[2]) == actor.userId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requester cannot approve their own discount");
        if (approve) {
            DemandBalance balance = lockDemand((long) discount[0]);
            BigDecimal outstanding = balance.amountDue().subtract(balance.amountPaid()).subtract(balance.discountAmount());
            if (((BigDecimal) discount[1]).compareTo(outstanding) > 0) throw conflict("Approved discount exceeds the outstanding balance");
            jdbc.update("UPDATE fee_demands SET discount_amount=discount_amount+?,status=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
                    discount[1], balance.amountPaid().add(balance.discountAmount()).add((BigDecimal) discount[1]).compareTo(balance.amountDue()) >= 0 ? "PAID" : "PARTIAL", discount[0]);
        }
        jdbc.update("UPDATE fee_discounts SET approval_status=?,approved_by=?,approved_at=CURRENT_TIMESTAMP(6),rejection_reason=? WHERE id=?",
                approve ? "APPROVED" : "REJECTED", actor.userId(), approve ? null : x.reason().trim(), id);
        audit.record(approve ? "FINANCE_DISCOUNT_APPROVED" : "FINANCE_DISCOUNT_REJECTED", "FEE_DISCOUNT", Long.toString(id), actor,
                null, Map.of("status", "PENDING"), Map.of("status", approve ? "APPROVED" : "REJECTED", "reason", x.reason()), request);
    }

    @Transactional(readOnly = true)
        public List<Map<String, Object>> discounts(SecurityPrincipal actor) {
                StudentScope studentScope = studentScope(actor, "s");
                boolean learner = actor.roles().contains("STUDENT") || actor.roles().contains("PARENT_GUARDIAN");
                String where;
                List<Object> args;
                if (learner) {
                    where = " WHERE " + studentScope.predicate();
                    args = studentScope.args();
                } else if (actor.permissions().contains("FEE_DISCOUNT:APPROVE") || actor.permissions().contains("FEE:READ")) {
                    where = "";
                    args = List.of();
                } else {
                    where = " WHERE d.requested_by=?";
                    args = List.of(actor.userId());
                }
                return jdbc.queryForList("SELECT d.id,d.student_id AS studentId,d.demand_id AS demandId,d.amount,d.reason,d.requested_by AS requestedBy,d.approval_status AS approvalStatus,d.approved_by AS approvedBy,d.approved_at AS approvedAt,d.created_at AS createdAt FROM fee_discounts d JOIN students s ON s.id=d.student_id" + where + " ORDER BY d.created_at DESC,d.id DESC", args.toArray());
    }

    @Transactional
    public long createExpense(ExpenseRequest x, SecurityPrincipal actor, HttpServletRequest request) {
        long id = insert("INSERT INTO expenses(category,description,amount,expense_date,vendor,receipt_url,requested_by,approval_status) VALUES(?,?,?,?,?,?,?,'DRAFT')",
                x.category().trim(), x.description().trim(), x.amount(), x.expenseDate(), x.vendor(), x.receiptUrl(), actor.userId());
        audit.record("FINANCE_EXPENSE_CREATED", "EXPENSE", Long.toString(id), actor, null, null,
                Map.of("category", x.category(), "amount", x.amount(), "status", "DRAFT"), request);
        return id;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> expenses(SecurityPrincipal actor) {
        boolean unrestricted = actor.permissions().contains("EXPENSE:APPROVE") || actor.permissions().contains("FINANCE_REPORT:READ");
        String sql = "SELECT id,category,description,amount,expense_date AS expenseDate,vendor,receipt_url AS receiptUrl,approval_status AS approvalStatus,requested_by AS requestedBy,approved_by AS approvedBy,approved_at AS approvedAt,rejection_reason AS rejectionReason,created_at AS createdAt FROM expenses"
                + (unrestricted ? "" : " WHERE requested_by=?")
                + " ORDER BY expense_date DESC,id DESC";
        return unrestricted
                ? jdbc.queryForList(sql)
                : jdbc.queryForList(sql, actor.userId());
    }

    @Transactional
    public void submitExpense(long id, SecurityPrincipal actor, HttpServletRequest request) {
        int changed = jdbc.update("UPDATE expenses SET approval_status='PENDING',updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND requested_by=? AND approval_status='DRAFT'", id, actor.userId());
        if (changed == 0) throw conflict("Only the expense requester can submit a draft expense");
        audit.record("FINANCE_EXPENSE_SUBMITTED", "EXPENSE", Long.toString(id), actor, null,
                Map.of("status", "DRAFT"), Map.of("status", "PENDING"), request);
    }

    @Transactional
    public void decideExpense(long id, boolean approve, DecisionRequest x, SecurityPrincipal actor, HttpServletRequest request) {
        var rows = jdbc.query("SELECT requested_by,approval_status FROM expenses WHERE id=? FOR UPDATE",
                (r, n) -> new Object[]{r.getLong("requested_by"), r.getString("approval_status")}, id);
        if (rows.isEmpty()) throw notFound("Expense");
        if (!"PENDING".equals(rows.getFirst()[1])) throw conflict("Only pending expenses can be decided");
        if (approve && ((long) rows.getFirst()[0]) == actor.userId()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Requester cannot approve their own expense");
        jdbc.update("UPDATE expenses SET approval_status=?,approved_by=?,approved_at=CURRENT_TIMESTAMP(6),rejection_reason=?,updated_at=CURRENT_TIMESTAMP(6) WHERE id=?",
                approve ? "APPROVED" : "REJECTED", actor.userId(), approve ? null : x.reason().trim(), id);
        audit.record(approve ? "FINANCE_EXPENSE_APPROVED" : "FINANCE_EXPENSE_REJECTED", "EXPENSE", Long.toString(id), actor,
                null, Map.of("status", "PENDING"), Map.of("status", approve ? "APPROVED" : "REJECTED", "reason", x.reason()), request);
    }

    @Transactional(readOnly = true)
    public FinanceSummary summary(LocalDate from, LocalDate to, SecurityPrincipal actor) {
        validateRange(from, to);
        StudentScope scope = studentScope(actor, "s");
        boolean learner = actor != null && (actor.roles().contains("STUDENT") || actor.roles().contains("PARENT_GUARDIAN"));
        String paymentScope = learner ? " AND " + scope.predicate() : "";
        List<Object> paymentArgs = new java.util.ArrayList<>(List.of(from, to));
        if (learner) paymentArgs.addAll(scope.args());
        BigDecimal collections = money("SELECT COALESCE(SUM(p.amount),0) FROM payments p JOIN students s ON s.id=p.student_id WHERE p.status='POSTED' AND p.payment_date BETWEEN ? AND ?" + paymentScope, paymentArgs.toArray());
        BigDecimal refunds = money("SELECT COALESCE(SUM(fa.amount),0) FROM finance_adjustments fa JOIN payments p ON p.id=fa.payment_id JOIN students s ON s.id=p.student_id WHERE fa.adjustment_type='REFUND' AND DATE(fa.created_at) BETWEEN ? AND ?" + paymentScope, paymentArgs.toArray());
        BigDecimal expenses = learner ? BigDecimal.ZERO : money("SELECT COALESCE(SUM(amount),0) FROM expenses WHERE approval_status='APPROVED' AND expense_date BETWEEN ? AND ?", from, to);
        BigDecimal outstanding = money("SELECT COALESCE(SUM(fd.amount_due-fd.amount_paid-fd.discount_amount),0) FROM fee_demands fd JOIN students s ON s.id=fd.student_id WHERE fd.status IN ('PENDING','PARTIAL')" + (learner ? " AND " + scope.predicate() : ""), learner ? scope.args().toArray() : new Object[]{});
        Long paymentCount = jdbc.queryForObject("SELECT COUNT(*) FROM payments p JOIN students s ON s.id=p.student_id WHERE p.status='POSTED' AND p.payment_date BETWEEN ? AND ?" + paymentScope, Long.class, paymentArgs.toArray());
        Long expenseCount = learner ? 0L : jdbc.queryForObject("SELECT COUNT(*) FROM expenses WHERE approval_status='APPROVED' AND expense_date BETWEEN ? AND ?", Long.class, from, to);
        return new FinanceSummary(collections, refunds, expenses, outstanding, paymentCount == null ? 0 : paymentCount, expenseCount == null ? 0 : expenseCount);
    }

    @Transactional(readOnly = true)
    public List<Reconciliation> reconcile(SecurityPrincipal actor) {
        StudentScope scope = studentScope(actor, "s");
        boolean learner = actor != null && (actor.roles().contains("STUDENT") || actor.roles().contains("PARENT_GUARDIAN"));
        return jdbc.query("SELECT fd.id,fd.amount_paid,COALESCE(SUM(CASE WHEN p.status='POSTED' THEN p.amount ELSE 0 END),0) posted " +
                        "FROM fee_demands fd JOIN students s ON s.id=fd.student_id LEFT JOIN payments p ON p.demand_id=fd.id " + (learner ? "WHERE " + scope.predicate() + " " : "") +
                        "GROUP BY fd.id,fd.amount_paid HAVING fd.amount_paid<>posted ORDER BY fd.id",
                (r, n) -> new Reconciliation("FEE_DEMAND", r.getLong("id"), r.getBigDecimal("amount_paid"),
                        r.getBigDecimal("posted"), r.getBigDecimal("amount_paid").subtract(r.getBigDecimal("posted"))), learner ? scope.args().toArray() : new Object[]{});
    }

    @Transactional(readOnly = true)
    public String exportPayments(LocalDate from, LocalDate to, SecurityPrincipal actor) {
                validateRange(from, to);
        List<Payment> rows = payments(from, to, actor);
        StringBuilder csv = new StringBuilder("payment_id,student_id,demand_id,receipt_no,reference_no,amount,payment_method,payment_date,status\r\n");
        rows.forEach(row -> csv.append(row.id()).append(',').append(row.studentId()).append(',').append(row.demandId()).append(',')
                .append(csv(row.receiptNo())).append(',').append(csv(row.referenceNo())).append(',').append(row.amount()).append(',')
                .append(csv(row.paymentMethod())).append(',').append(row.paymentDate()).append(',').append(csv(row.status())).append("\r\n"));
        return csv.toString();
    }

    private FeeDemand demand(long id) {
        return jdbc.query("SELECT fd.id,fd.student_id,CONCAT(s.first_name,' ',COALESCE(s.last_name,'')) student_name,fd.fee_structure_id,fd.session_id,fd.billing_period,fd.amount_due,fd.amount_paid,fd.discount_amount,fd.due_date,fd.status " +
                        "FROM fee_demands fd JOIN students s ON s.id=fd.student_id WHERE fd.id=?",
                (r, n) -> mapDemand(r.getLong("id"), r.getLong("student_id"), r.getString("student_name"), r.getLong("fee_structure_id"),
                        r.getLong("session_id"), r.getObject("billing_period", LocalDate.class), r.getBigDecimal("amount_due"),
                        r.getBigDecimal("amount_paid"), r.getBigDecimal("discount_amount"), r.getObject("due_date", LocalDate.class), r.getString("status")), id)
                .stream().findFirst().orElseThrow(() -> notFound("Fee demand"));
    }

    private Payment payment(long id) { return paymentQuery("p.id=?", id); }
        private static Payment replayOrConflict(Payment existing, PaymentRequest request) {
                if (existing.demandId() == request.demandId() && existing.amount().compareTo(request.amount()) == 0
                                && existing.paymentMethod().equals(request.paymentMethod()) && existing.paymentDate().equals(request.paymentDate())) return existing;
                throw conflict("Payment reference has already been used for a different submission");
        }
    private Payment paymentByReference(String reference) { return paymentQuery("p.reference_no=?", reference); }
    private Payment paymentById(long id) { return paymentQuery("p.id=?", id); }
    private Payment paymentQuery(String where, Object value) {
        return jdbc.query("SELECT p.id,p.student_id,p.demand_id,p.receipt_no,p.reference_no,p.amount,p.payment_method,p.payment_date,p.status,r.issued_at " +
                        "FROM payments p LEFT JOIN receipts r ON r.payment_id=p.id WHERE " + where,
                (r, n) -> paymentRow(r), value).stream().findFirst().orElse(null);
    }

    private StudentScope studentScope(SecurityPrincipal actor, String studentAlias) {
        if (actor == null) return new StudentScope("1=1", List.of());
        List<String> predicates = new java.util.ArrayList<>();
        List<Object> args = new java.util.ArrayList<>();
        if (actor.roles().contains("STUDENT")) {
            predicates.add(studentAlias + ".user_id = ?");
            args.add(actor.userId());
        }
        if (actor.roles().contains("PARENT_GUARDIAN")) {
            predicates.add("EXISTS (SELECT 1 FROM student_guardians sg JOIN guardians g ON g.id=sg.guardian_id WHERE sg.student_id=" + studentAlias + ".id AND g.user_id=?)");
            args.add(actor.userId());
        }
        return predicates.isEmpty() ? new StudentScope("1=1", List.of())
                : new StudentScope("(" + String.join(" OR ", predicates) + ")", args);
    }

    private static List<Object> concat(List<?> first, List<Object> second) {
        List<Object> result = new java.util.ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    private record StudentScope(String predicate, List<Object> args) { }

    private Payment paymentRow(java.sql.ResultSet r) throws java.sql.SQLException {
        return new Payment(r.getLong("id"), r.getLong("student_id"), r.getLong("demand_id"), r.getString("receipt_no"),
                r.getString("reference_no"), r.getBigDecimal("amount"), r.getString("payment_method"),
                r.getObject("payment_date", LocalDate.class), r.getString("status"), r.getObject("issued_at", LocalDateTime.class));
    }

    private DemandBalance lockDemand(long id) {
        return jdbc.query("SELECT student_id,session_id,amount_due,amount_paid,discount_amount FROM fee_demands WHERE id=? FOR UPDATE",
                (r, n) -> new DemandBalance(r.getLong("student_id"), r.getLong("session_id"), r.getBigDecimal("amount_due"),
                        r.getBigDecimal("amount_paid"), r.getBigDecimal("discount_amount")), id).stream().findFirst().orElse(null);
    }
    private record DemandBalance(long studentId, long sessionId, BigDecimal amountDue, BigDecimal amountPaid, BigDecimal discountAmount) {}
    private static FeeDemand mapDemand(long id, long studentId, String name, long structureId, long sessionId, LocalDate period,
                                      BigDecimal due, BigDecimal paid, BigDecimal discount, LocalDate dueDate, String status) {
        return new FeeDemand(id, studentId, name == null ? "" : name.trim(), structureId, sessionId, period,
                due, paid, discount, due.subtract(paid).subtract(discount), dueDate, status);
    }
    private BigDecimal money(String sql, Object... args) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
        return value == null ? BigDecimal.ZERO : value;
    }
    private long insert(String sql, Object... args) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        Map.Entry<String, Object> generated = key.getKeys().entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("id"))
                .findFirst().orElseGet(() -> key.getKeys().entrySet().iterator().next());
        Number value = (Number) generated.getValue();
        if (value == null) throw new IllegalStateException("Database did not return a generated key");
        return value.longValue();
    }
    private void requireExists(String table, long id, String label) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE id=?", Integer.class, id);
        if (count == null || count == 0) throw notFound(label);
    }
    private static String csv(String value) { return CsvSafety.cell(value); }
        private static LocalDate normalizePeriod(LocalDate value, String frequency) {
                return switch (frequency) {
                        case "MONTHLY" -> value.withDayOfMonth(1);
                        case "QUARTERLY" -> value.withMonth(((value.getMonthValue() - 1) / 3) * 3 + 1).withDayOfMonth(1);
                        case "ANNUALLY" -> value.withDayOfYear(1);
                        case "ONE_TIME" -> value;
                        default -> value;
                };
        }
        private static void validateRange(LocalDate from, LocalDate to) {
                if (from == null || to == null || from.isAfter(to)) throw invalid("dateRange", "from must be on or before to");
        }
    private static ResponseStatusException invalid(String field, String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, field + ": " + reason); }
    private static ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT, reason); }
    private static ResponseStatusException notFound(String label) { return new ResponseStatusException(HttpStatus.NOT_FOUND, label + " not found"); }
}
