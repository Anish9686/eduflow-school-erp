package com.erp.portal.student.api;

import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.student.service.StudentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import static com.erp.portal.student.api.StudentDtos.*;

@RestController
@Validated
@RequestMapping("/api/v1/students")
public class StudentController {
    private final StudentService students;
    public StudentController(StudentService students) { this.students = students; }

    @GetMapping
    @PreAuthorize("hasAuthority('STUDENT:READ')")
    public PageResponse<StudentSummary> list(
            @RequestParam(required = false) @Size(max = 100) String search,
            @RequestParam(required = false) @Pattern(regexp = "APPLIED|ACTIVE|TRANSFERRED|EXITED") String status,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return students.list(search, status, sortBy, direction.toUpperCase(), page, size, actor);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STUDENT:CREATE')")
    public ResponseEntity<StudentResponse> create(@Valid @org.springframework.web.bind.annotation.RequestBody CreateStudentRequest body,
            @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) {
        StudentResponse result = students.create(body, actor, request);
        return ResponseEntity.created(URI.create("/api/v1/students/" + result.id())).body(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('STUDENT:READ','ADMISSION:READ')")
    public StudentResponse detail(@PathVariable @Min(1) long id, @AuthenticationPrincipal SecurityPrincipal actor) {
        return students.detail(id, actor);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STUDENT:UPDATE')")
    public StudentResponse update(@PathVariable @Min(1) long id,
            @Valid @org.springframework.web.bind.annotation.RequestBody UpdateStudentRequest body,
            @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) {
        return students.update(id, body, actor, request);
    }

    @GetMapping("/{id}/guardians")
    @PreAuthorize("hasAuthority('STUDENT:READ')")
    public java.util.List<GuardianResponse> guardians(@PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor) {
        return students.detail(id, actor).guardians();
    }

    @PostMapping("/{id}/guardians")
    @PreAuthorize("hasAuthority('STUDENT:UPDATE')")
    public java.util.List<GuardianResponse> addGuardian(@PathVariable @Min(1) long id,
            @Valid @org.springframework.web.bind.annotation.RequestBody GuardianRequest body,
            @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) {
        return students.addGuardian(id, body, actor, request);
    }

    @PutMapping("/{id}/guardians/{guardianId}")
    @PreAuthorize("hasAuthority('STUDENT:UPDATE')")
    public java.util.List<GuardianResponse> updateGuardian(@PathVariable @Min(1) long id,
            @PathVariable @Min(1) long guardianId,
            @Valid @org.springframework.web.bind.annotation.RequestBody GuardianRequest body,
            @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) {
        return students.updateGuardian(id, guardianId, body, actor, request);
    }

    @DeleteMapping("/{id}/guardians/{guardianId}")
    @PreAuthorize("hasAuthority('STUDENT:UPDATE')")
    public ResponseEntity<Void> removeGuardian(@PathVariable @Min(1) long id,
            @PathVariable @Min(1) long guardianId, @AuthenticationPrincipal SecurityPrincipal actor,
            HttpServletRequest request) {
        students.removeGuardian(id, guardianId, actor, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasAnyAuthority('STUDENT:READ','ADMISSION:READ')")
    public java.util.List<StudentDocumentResponse> documents(@PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor) { return students.documents(id, actor); }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('STUDENT:UPDATE','ADMISSION:UPDATE')")
    public ResponseEntity<StudentDocumentResponse> uploadDocument(@PathVariable @Min(1) long id,
            @RequestParam String docType, @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) {
        StudentDocumentResponse result = students.uploadDocument(id, docType, file, actor, request);
        return ResponseEntity.created(URI.create("/api/v1/students/" + id + "/documents/" + result.id())).body(result);
    }

    @GetMapping("/{id}/documents/{documentId}/content")
    @PreAuthorize("hasAnyAuthority('STUDENT:READ','ADMISSION:READ')")
    public ResponseEntity<Resource> downloadDocument(@PathVariable @Min(1) long id,
            @PathVariable @Min(1) long documentId, @AuthenticationPrincipal SecurityPrincipal actor) {
        var document = students.downloadDocument(id, documentId, actor);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(document.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(document.resource());
    }

    @PostMapping("/{id}/transfer")
    @PreAuthorize("hasAuthority('STUDENT:UPDATE')")
    public StudentResponse transfer(@PathVariable @Min(1) long id,
            @Valid @org.springframework.web.bind.annotation.RequestBody TransferRequest body,
            @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) {
        return students.transfer(id, body, actor, request);
    }

    @PostMapping("/{id}/exit")
    @PreAuthorize("hasAuthority('STUDENT:UPDATE')")
    public StudentResponse exit(@PathVariable @Min(1) long id,
            @Valid @org.springframework.web.bind.annotation.RequestBody ExitRequest body,
            @AuthenticationPrincipal SecurityPrincipal actor, HttpServletRequest request) {
        return students.exit(id, body, actor, request);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('STUDENT:READ')")
    public java.util.List<StudentHistoryResponse> history(@PathVariable @Min(1) long id,
            @AuthenticationPrincipal SecurityPrincipal actor) { return students.history(id, actor); }
}
