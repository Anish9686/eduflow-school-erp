package com.erp.portal.student.api;

import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.student.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.erp.portal.student.api.StudentDtos.*;

@RestController
@Validated
@RequestMapping("/api/v1/admissions")
public class AdmissionController {
    private final StudentService students;
    public AdmissionController(StudentService students) { this.students = students; }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMISSION:READ')")
    public PageResponse<AdmissionResponse> list(
            @RequestParam(required = false) @Pattern(regexp = "DRAFT|PENDING|UNDER_REVIEW|APPROVED|REJECTED|WAITLISTED") String status,
            @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return students.admissions(status, search, page, size, actor);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMISSION:CREATE')")
    public ResponseEntity<AdmissionResponse> create(@Valid @RequestBody CreateAdmissionRequest body,
            @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) {
        AdmissionResponse result = students.createAdmission(body, actor, request);
        return ResponseEntity.created(java.net.URI.create("/api/v1/admissions/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMISSION:READ')")
    public AdmissionResponse detail(@PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor) { return students.admission(id, actor); }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('ADMISSION:CREATE')")
    public AdmissionResponse submit(@PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) {
        return students.submitAdmission(id, actor, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ADMISSION:UPDATE')")
    public AdmissionResponse decide(@PathVariable @Min(1) long id,
            @Valid @RequestBody AdmissionDecisionRequest body,
            @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) {
        return students.decideAdmission(id, body, actor, request);
    }
}
