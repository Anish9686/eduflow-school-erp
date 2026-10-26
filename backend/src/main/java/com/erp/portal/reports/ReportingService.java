package com.erp.portal.reports;

import com.erp.portal.attendance.AttendanceDtos.StudentAttendance;
import com.erp.portal.common.export.CsvSafety;
import com.erp.portal.attendance.AttendanceService;
import com.erp.portal.finance.FinanceDtos.Payment;
import com.erp.portal.finance.FinanceService;
import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.student.api.StudentDtos.PageResponse;
import com.erp.portal.student.api.StudentDtos.StudentSummary;
import com.erp.portal.student.service.StudentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.erp.portal.reports.ReportDtos.*;

@Service
public class ReportingService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SYNC_EXPORT_ROWS = 5_000;
    private final StudentService students;
    private final AttendanceService attendance;
    private final FinanceService finance;
    private final ExecutorService exportExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "report-export");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    private static final List<ReportDefinition> DEFINITIONS = List.of(
            new ReportDefinition("student-directory", "Student directory", "Scoped student directory with search, status, sorting, and pagination.",
                    List.of(new FilterDefinition("search", "Search", "text", List.of()),
                            new FilterDefinition("status", "Status", "select", List.of("APPLIED", "ACTIVE", "TRANSFERRED", "EXITED"))),
                    List.of(new ColumnDefinition("admissionNo", "Admission no."), new ColumnDefinition("firstName", "First name"),
                            new ColumnDefinition("lastName", "Last name"), new ColumnDefinition("status", "Status"),
                            new ColumnDefinition("admissionDate", "Admission date"), new ColumnDefinition("createdAt", "Created")),
                    List.of("admissionNo", "firstName", "lastName", "status", "admissionDate", "createdAt"), List.of("CSV", "XLSX")),
            new ReportDefinition("attendance-records", "Student attendance", "Student attendance records filtered by date and class scope.",
                    List.of(new FilterDefinition("dateFrom", "From date", "date", List.of()),
                            new FilterDefinition("dateTo", "To date", "date", List.of()),
                            new FilterDefinition("sessionId", "Session ID", "number", List.of()),
                            new FilterDefinition("classId", "Class ID", "number", List.of()),
                            new FilterDefinition("sectionId", "Section ID", "number", List.of()),
                            new FilterDefinition("status", "Status", "select", List.of("PRESENT", "ABSENT", "LATE", "HALF_DAY", "EXCUSED", "HOLIDAY"))),
                    List.of(new ColumnDefinition("attendanceDate", "Date"), new ColumnDefinition("admissionNo", "Admission no."),
                            new ColumnDefinition("studentName", "Student"), new ColumnDefinition("className", "Class"),
                            new ColumnDefinition("sectionName", "Section"), new ColumnDefinition("status", "Status"),
                            new ColumnDefinition("markedAt", "Marked at")),
                    List.of("attendanceDate", "admissionNo", "studentName", "className", "sectionName", "status", "markedAt"), List.of("CSV", "XLSX")),
            new ReportDefinition("finance-payments", "Finance payments", "Posted payments for a date range using the existing finance read query.",
                    List.of(new FilterDefinition("dateFrom", "From date", "date", List.of()),
                            new FilterDefinition("dateTo", "To date", "date", List.of())),
                    List.of(new ColumnDefinition("paymentDate", "Payment date"), new ColumnDefinition("receiptNo", "Receipt no."),
                            new ColumnDefinition("studentId", "Student ID"), new ColumnDefinition("demandId", "Demand ID"),
                            new ColumnDefinition("amount", "Amount"), new ColumnDefinition("paymentMethod", "Method"),
                            new ColumnDefinition("status", "Status"), new ColumnDefinition("issuedAt", "Issued at")),
                    List.of("paymentDate", "receiptNo", "studentId", "demandId", "amount", "status"), List.of("CSV", "XLSX"))
    );

    public ReportingService(StudentService students, AttendanceService attendance, FinanceService finance) {
        this.students = students;
        this.attendance = attendance;
        this.finance = finance;
    }

    public List<ReportDefinition> catalog(SecurityPrincipal actor) {
        return DEFINITIONS.stream().filter(definition -> canRead(definition.code(), actor))
                .map(definition -> canExport(definition.code(), actor) ? definition
                        : new ReportDefinition(definition.code(), definition.title(), definition.description(),
                        definition.filters(), definition.columns(), definition.sortableFields(), List.of()))
                .toList();
    }

    public ReportPage report(String code, Map<String, String> params, SecurityPrincipal actor) {
        requireRead(code, actor);
        int page = integerParam(params, "page", 0);
        int size = integerParam(params, "size", 25);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be non-negative and size must be between 1 and 100");
        }
        String sortBy = params.getOrDefault("sortBy", defaultSort(code));
        String direction = params.getOrDefault("direction", "ASC").toUpperCase(Locale.ROOT);
        ReportDefinition definition = definition(code);
        validateSort(definition, sortBy, direction);
        if (code.equals("student-directory")) {
            PageResponse<StudentSummary> pageResult = students.list(params.get("search"), params.get("status"),
                    sortBy, direction, page, size, actor);
            List<Map<String, Object>> rows = pageResult.content().stream().map(ReportingService::studentRow).toList();
            return new ReportPage(code, definition.columns(), rows, page, size, pageResult.totalElements(),
                    pageResult.totalPages(), sortBy, direction);
        }
        List<Map<String, Object>> rows = dataRows(code, params, actor);
        sort(rows, sortBy, direction);
        return page(code, definition, rows, page, size, sortBy, direction);
    }

    public ExportResponse export(String code, Map<String, String> params, String format,
                                 boolean asynchronous, SecurityPrincipal actor) {
        requireExport(code, actor);
        validateSort(definition(code), params.getOrDefault("sortBy", defaultSort(code)),
                params.getOrDefault("direction", "ASC").toUpperCase(Locale.ROOT));
        String normalizedFormat = format == null ? "CSV" : format.toUpperCase(Locale.ROOT);
        if (!List.of("CSV", "XLSX").contains(normalizedFormat)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format must be CSV or XLSX");
        }
        if (asynchronous) {
            String id = UUID.randomUUID().toString();
            CompletableFuture<ExportFile> future = CompletableFuture.supplyAsync(
                    () -> createExport(code, params, normalizedFormat, actor), exportExecutor);
            jobs.put(id, new Job(actor.userId(), code, future));
            return ExportResponse.job(new ExportJob(id, code, "QUEUED", "/api/v1/reports/exports/" + id));
        }
        ExportFile file = createExport(code, params, normalizedFormat, actor);
        if (file.rowCount() > MAX_SYNC_EXPORT_ROWS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "This report exceeds the synchronous export limit; retry with async=true");
        }
        return ExportResponse.file(file);
    }

    public ExportJob job(String jobId, SecurityPrincipal actor) {
        Job job = requireJob(jobId, actor);
        requireExport(job.reportCode(), actor);
        if (job.future().isCompletedExceptionally()) return new ExportJob(jobId, job.reportCode(), "FAILED", null);
        if (!job.future().isDone()) return new ExportJob(jobId, job.reportCode(), "RUNNING", null);
        return new ExportJob(jobId, job.reportCode(), "COMPLETED", "/api/v1/reports/exports/" + jobId + "/download");
    }

    public ExportFile downloadJob(String jobId, SecurityPrincipal actor) {
        Job job = requireJob(jobId, actor);
        requireExport(job.reportCode(), actor);
        if (!job.future().isDone()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Export is not ready");
        try {
            return job.future().join();
        } catch (RuntimeException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Report export failed", cause);
        }
    }

    private Job requireJob(String id, SecurityPrincipal actor) {
        Job job = jobs.get(id);
        if (job == null || job.userId() != actor.userId()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Export job not found");
        return job;
    }

    private ExportFile createExport(String code, Map<String, String> params, String format, SecurityPrincipal actor) {
        ReportDefinition definition = definition(code);
        List<Map<String, Object>> rows = dataRows(code, params, actor);
        sort(rows, params.getOrDefault("sortBy", defaultSort(code)), params.getOrDefault("direction", "ASC").toUpperCase(Locale.ROOT));
        byte[] body = format.equals("XLSX") ? xlsx(definition, rows) : csv(definition, rows);
        String extension = format.toLowerCase(Locale.ROOT);
        String mime = format.equals("XLSX")
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "text/csv;charset=UTF-8";
        return new ExportFile(definition.code() + "." + extension, mime, body, rows.size());
    }

    private List<Map<String, Object>> dataRows(String code, Map<String, String> params, SecurityPrincipal actor) {
        return switch (code) {
            case "student-directory" -> studentRows(params, actor);
            case "attendance-records" -> attendanceRows(params, actor);
            case "finance-payments" -> financeRows(params, actor);
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found");
        };
    }

    private List<Map<String, Object>> studentRows(Map<String, String> params, SecurityPrincipal actor) {
        List<Map<String, Object>> result = new ArrayList<>();
        int page = 0;
        PageResponse<StudentSummary> batch;
        do {
            batch = students.list(params.get("search"), params.get("status"),
                    params.getOrDefault("sortBy", "createdAt"), params.getOrDefault("direction", "ASC").toUpperCase(Locale.ROOT),
                    page++, MAX_PAGE_SIZE, actor);
            batch.content().stream().map(ReportingService::studentRow).forEach(result::add);
        } while (page < batch.totalPages());
        return result;
    }

    private List<Map<String, Object>> attendanceRows(Map<String, String> params, SecurityPrincipal actor) {
        LocalDate from = dateParam(params, "dateFrom");
        LocalDate to = dateParam(params, "dateTo");
        if ((from == null) != (to == null)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom and dateTo must be provided together");
        Long sessionId = longParam(params, "sessionId");
        Long classId = longParam(params, "classId");
        Long sectionId = longParam(params, "sectionId");
        Long studentId = longParam(params, "studentId");
        List<StudentAttendance> records = attendance.studentRecords(from, to, sessionId, classId, sectionId,
                studentId, null, params.get("status"), actor);
        return records.stream().map(ReportingService::attendanceRow).toList();
    }

    private List<Map<String, Object>> financeRows(Map<String, String> params, SecurityPrincipal actor) {
        LocalDate from = dateParam(params, "dateFrom");
        LocalDate to = dateParam(params, "dateTo");
        if (from == null || to == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom and dateTo are required");
        if (from.isAfter(to)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "dateFrom must be on or before dateTo");
        return finance.payments(from, to, actor).stream().map(ReportingService::paymentRow).toList();
    }

    private static ReportPage page(String code, ReportDefinition definition, List<Map<String, Object>> rows,
                                   int page, int size, String sortBy, String direction) {
        long offsetLong = (long) page * size;
        int start = (int) Math.min(rows.size(), offsetLong);
        int end = Math.min(rows.size(), start + size);
        int pages = rows.isEmpty() ? 0 : (int) Math.ceil((double) rows.size() / size);
        return new ReportPage(code, definition.columns(), rows.subList(start, end), page, size, rows.size(), pages, sortBy, direction);
    }

    private static void sort(List<Map<String, Object>> rows, String key, String direction) {
        Comparator<Map<String, Object>> comparator = Comparator.comparing(
                row -> row.get(key), Comparator.nullsLast(ReportingService::compareValues));
        if (direction.equals("DESC")) comparator = comparator.reversed();
        rows.sort(comparator);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compareValues(Object left, Object right) {
        if (left instanceof Number a && right instanceof Number b) {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
        }
        if (left instanceof Comparable comparable && left.getClass().isInstance(right)) return comparable.compareTo(right);
        return left.toString().compareToIgnoreCase(right.toString());
    }

    private boolean canRead(String code, SecurityPrincipal actor) {
        return switch (code) {
            case "student-directory" -> actor.permissions().contains("STUDENT:READ");
            case "attendance-records" -> hasAny(actor, "ATTENDANCE:READ", "ATTENDANCE:MARK", "ATTENDANCE:MANAGE");
            case "finance-payments" -> actor.permissions().contains("FINANCE_REPORT:READ");
            default -> false;
        };
    }

    private void requireRead(String code, SecurityPrincipal actor) {
        definition(code);
        if (!canRead(code, actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Report read permission is required");
    }

    private void requireExport(String code, SecurityPrincipal actor) {
        requireRead(code, actor);
        if (!canExport(code, actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Report export permission is required");
    }

    private boolean canExport(String code, SecurityPrincipal actor) {
        return switch (code) {
            case "student-directory", "attendance-records" -> actor.permissions().contains("REPORT:EXPORT");
            case "finance-payments" -> actor.permissions().contains("FINANCE_REPORT:EXPORT");
            default -> false;
        };
    }

    private static void validateSort(ReportDefinition definition, String sortBy, String direction) {
        if (!definition.sortableFields().contains(sortBy) || !List.of("ASC", "DESC").contains(direction)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported report sort field or direction");
        }
    }

    private static boolean hasAny(SecurityPrincipal actor, String... permissions) {
        for (String permission : permissions) if (actor.permissions().contains(permission)) return true;
        return false;
    }

    private static ReportDefinition definition(String code) {
        return DEFINITIONS.stream().filter(item -> item.code().equals(code)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    private static String defaultSort(String code) {
        return code.equals("student-directory") ? "createdAt" : code.equals("attendance-records") ? "attendanceDate" : "paymentDate";
    }

    private static int integerParam(Map<String, String> params, String key, int fallback) {
        String value = params.get(key);
        if (value == null || value.isBlank()) return fallback;
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be an integer"); }
    }

    private static Long longParam(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null || value.isBlank()) return null;
        try { long parsed = Long.parseLong(value); if (parsed < 1) throw new NumberFormatException(); return parsed; }
        catch (NumberFormatException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must be a positive integer"); }
    }

    private static LocalDate dateParam(Map<String, String> params, String key) {
        String value = params.get(key);
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value); }
        catch (RuntimeException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " must use YYYY-MM-DD"); }
    }

    private static Map<String, Object> studentRow(StudentSummary row) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("admissionNo", row.admissionNo()); values.put("firstName", row.firstName());
        values.put("lastName", row.lastName()); values.put("status", row.status());
        values.put("admissionDate", row.admissionDate()); values.put("createdAt", row.createdAt());
        return values;
    }

    private static Map<String, Object> attendanceRow(StudentAttendance row) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("attendanceDate", row.attendanceDate()); values.put("admissionNo", row.admissionNo());
        values.put("studentName", row.studentName()); values.put("className", row.className());
        values.put("sectionName", row.sectionName()); values.put("status", row.status());
        values.put("markedAt", row.markedAt());
        return values;
    }

    private static Map<String, Object> paymentRow(Payment row) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("paymentDate", row.paymentDate()); values.put("receiptNo", row.receiptNo());
        values.put("studentId", row.studentId()); values.put("demandId", row.demandId());
        values.put("amount", row.amount()); values.put("paymentMethod", row.paymentMethod());
        values.put("status", row.status()); values.put("issuedAt", row.issuedAt());
        return values;
    }

    private static byte[] csv(ReportDefinition definition, List<Map<String, Object>> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", definition.columns().stream().map(column -> csvCell(column.label())).toList())).append("\r\n");
        for (Map<String, Object> row : rows) {
            csv.append(String.join(",", definition.columns().stream().map(column -> csvCell(row.get(column.key()))).toList())).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvCell(Object raw) {
        return CsvSafety.cell(raw);
    }

    private static byte[] xlsx(ReportDefinition definition, List<Map<String, Object>> rows) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
                entry(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
                entry(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
                entry(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Report\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
                entry(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
                StringBuilder sheet = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
                int rowNumber = 1;
                sheet.append("<row r=\"1\">");
                for (int col = 0; col < definition.columns().size(); col++) cell(sheet, col, rowNumber, definition.columns().get(col).label());
                sheet.append("</row>");
                for (Map<String, Object> row : rows) {
                    rowNumber++;
                    sheet.append("<row r=\"").append(rowNumber).append("\">");
                    for (int col = 0; col < definition.columns().size(); col++) {
                        Object value = row.get(definition.columns().get(col).key());
                        cell(sheet, col, rowNumber, value == null ? "" : value.toString());
                    }
                    sheet.append("</row>");
                }
                sheet.append("</sheetData></worksheet>");
                entry(zip, "xl/worksheets/sheet1.xml", sheet.toString());
            }
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create XLSX report", exception);
        }
    }

    private static void cell(StringBuilder sheet, int column, int row, String value) {
        sheet.append("<c r=\"").append(columnName(column)).append(row).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                .append(xml(value)).append("</t></is></c>");
    }

    private static String columnName(int index) {
        StringBuilder name = new StringBuilder();
        int value = index + 1;
        while (value > 0) { int rem = (value - 1) % 26; name.insert(0, (char) ('A' + rem)); value = (value - 1) / 26; }
        return name.toString();
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void entry(ZipOutputStream zip, String name, String value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    public record ExportFile(String fileName, String contentType, byte[] body, int rowCount) {}
    public record ExportResponse(ExportFile file, ExportJob job) {
        static ExportResponse file(ExportFile file) { return new ExportResponse(file, null); }
        static ExportResponse job(ExportJob job) { return new ExportResponse(null, job); }
    }
    private record Job(long userId, String reportCode, CompletableFuture<ExportFile> future) {}
}
