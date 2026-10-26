package com.erp.portal.administration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/export")
public class ExportController {
    private final ImportExportService importExportService;

    public ExportController(ImportExportService importExportService) {
        this.importExportService = importExportService;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('USER:EXPORT')")
    public ResponseEntity<String> exportUsers() {
        String csv = importExportService.exportUsersCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"users_export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('AUDIT:EXPORT')")
    public ResponseEntity<String> exportAuditLogs(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        String csv = importExportService.exportAuditLogsCsv(actor, entityType, action, dateFrom, dateTo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit_logs_export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }
}
