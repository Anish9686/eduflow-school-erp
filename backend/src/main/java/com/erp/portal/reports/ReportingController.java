package com.erp.portal.reports;

import com.erp.portal.reports.ReportingService.ExportFile;
import com.erp.portal.reports.ReportingService.ExportResponse;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static com.erp.portal.reports.ReportDtos.*;

@RestController
@Validated
@RequestMapping("/api/v1/reports")
public class ReportingController {
    private final ReportingService reports;

    public ReportingController(ReportingService reports) { this.reports = reports; }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public java.util.List<ReportDefinition> catalog(@AuthenticationPrincipal SecurityPrincipal actor) {
        return reports.catalog(actor);
    }

    @GetMapping("/{code}")
    @PreAuthorize("isAuthenticated()")
    public ReportPage report(@PathVariable @Pattern(regexp = "[a-z0-9-]{2,60}") String code,
                             @RequestParam Map<String, String> params,
                             @AuthenticationPrincipal SecurityPrincipal actor) {
        return reports.report(code, params, actor);
    }

    @GetMapping("/{code}/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> export(@PathVariable @Pattern(regexp = "[a-z0-9-]{2,60}") String code,
                                    @RequestParam Map<String, String> params,
                                    @AuthenticationPrincipal SecurityPrincipal actor) {
        ExportResponse response = reports.export(code, params, params.get("format"),
                Boolean.parseBoolean(params.getOrDefault("async", "false")), actor);
        if (response.job() != null) return ResponseEntity.accepted().body(response.job());
        return file(response.file());
    }

    @GetMapping("/exports/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ExportJob exportJob(@PathVariable String jobId, @AuthenticationPrincipal SecurityPrincipal actor) {
        return reports.job(jobId, actor);
    }

    @GetMapping("/exports/{jobId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadJob(@PathVariable String jobId,
                                              @AuthenticationPrincipal SecurityPrincipal actor) {
        return file(reports.downloadJob(jobId, actor));
    }

    private static ResponseEntity<byte[]> file(ExportFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.body());
    }
}
