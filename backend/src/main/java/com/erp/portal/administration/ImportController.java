package com.erp.portal.administration;

import com.erp.portal.administration.ImportExportDtos.ImportResult;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/admin/import")
public class ImportController {
    private final ImportExportService importExportService;

    public ImportController(ImportExportService importExportService) {
        this.importExportService = importExportService;
    }

    @PostMapping(value = "/users", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('USER:IMPORT')")
    public ResponseEntity<ImportResult> importUsersMultipart(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal SecurityPrincipal principal,
            HttpServletRequest httpRequest) throws IOException {
        ImportResult result = importExportService.importUsers(file.getInputStream(), principal, httpRequest);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/users", consumes = {"text/csv", "text/plain"})
    @PreAuthorize("hasAuthority('USER:IMPORT')")
    public ResponseEntity<ImportResult> importUsersCsvBody(
            @RequestBody String csvContent,
            @AuthenticationPrincipal SecurityPrincipal principal,
            HttpServletRequest httpRequest) {
        ByteArrayInputStream bais = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));
        ImportResult result = importExportService.importUsers(bais, principal, httpRequest);
        return ResponseEntity.ok(result);
    }
}
