package com.erp.portal.student.service;

import com.erp.portal.administration.AuditService;
import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.student.StudentModuleException;
import com.erp.portal.student.api.StudentDtos;
import com.erp.portal.student.persistence.StudentRepository;
import com.erp.portal.student.persistence.StudentRepository.DocumentRecord;
import com.erp.portal.student.persistence.StudentRepository.StudentRecord;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StudentService {
    private static final long MAX_DOCUMENT_BYTES = 10L * 1024 * 1024;
    private static final Set<String> DOCUMENT_TYPES = Set.of(
            "BIRTH_CERT", "TRANSFER_CERT", "MARKSHEET", "PHOTO", "OTHER");
    private static final Map<String, String> EXTENSION_MIME = Map.of(
            "pdf", "application/pdf", "jpg", "image/jpeg", "jpeg", "image/jpeg", "png", "image/png");
    private static final Set<String> ADMISSION_STATUSES = Set.of("DRAFT", "PENDING", "UNDER_REVIEW", "APPROVED", "REJECTED", "WAITLISTED");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "DRAFT", Set.of("PENDING", "UNDER_REVIEW", "WAITLISTED", "APPROVED", "REJECTED"),
            "PENDING", Set.of("UNDER_REVIEW", "APPROVED", "REJECTED", "WAITLISTED"),
            "UNDER_REVIEW", Set.of("APPROVED", "REJECTED", "WAITLISTED"),
            "WAITLISTED", Set.of("PENDING", "APPROVED", "REJECTED"),
            "APPROVED", Set.of(),
            "REJECTED", Set.of());

    private final StudentRepository repository;
    private final AuditService audit;
    private final Path storageRoot;

    public StudentService(StudentRepository repository, AuditService audit,
                          @Value("${student.documents.storage-dir:./storage/student-documents}") String storageDirectory) {
        this.repository = repository;
        this.audit = audit;
        this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    @Transactional(readOnly = true)
    public StudentDtos.PageResponse<StudentDtos.StudentSummary> list(String search, String status, String sortBy,
            String direction, int page, int size, SecurityPrincipal actor) {
        if (page < 0) throw StudentModuleException.invalid("page", "must be zero or greater");
        if (size < 1 || size > 100) throw StudentModuleException.invalid("size", "must be between 1 and 100");
        if (status != null && !Set.of("APPLIED", "ACTIVE", "TRANSFERRED", "EXITED").contains(status))
            throw StudentModuleException.invalid("status", "is not a supported student status");
        if (!Set.of("admissionNo", "firstName", "lastName", "status", "admissionDate", "createdAt").contains(sortBy))
            throw StudentModuleException.invalid("sortBy", "is not an allowed sort field");
        if (!Set.of("ASC", "DESC").contains(direction)) throw StudentModuleException.invalid("direction", "must be ASC or DESC");
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) throw StudentModuleException.invalid("page", "is too large");
        List<StudentDtos.StudentSummary> content = repository.list(search, status, sortBy, direction, size, (int) offset, actor);
        long total;
        if (page == 0 && content.size() < size) {
            total = content.size();
        } else {
            total = repository.count(search, status, actor);
        }
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new StudentDtos.PageResponse<>(content, page, size, total, totalPages, sortBy, direction);
    }

    @Transactional
    public StudentDtos.StudentResponse create(StudentDtos.CreateStudentRequest request, SecurityPrincipal actor,
                                               HttpServletRequest servletRequest) {
        long id = repository.createStudent(request, "ACTIVE", actor.userId(), LocalDate.now());
        repository.addHistory(id, "STUDENT_CREATED", null, "ACTIVE", "Student profile created", actor.userId());
        audit.record("STUDENT_CREATED", "STUDENT", Long.toString(id), actor, null,
                null, Map.of("status", "ACTIVE"), servletRequest);
        return detail(id, actor);
    }

    @Transactional(readOnly = true)
    public StudentDtos.StudentResponse detail(long id, SecurityPrincipal actor) {
        StudentRecord row = requireStudent(id, actor);
        return new StudentDtos.StudentResponse(row.id(), row.admissionNo(), row.firstName(), row.lastName(),
                row.dateOfBirth(), row.gender(), row.bloodGroup(), row.religion(), row.category(), row.phone(),
                row.email(), row.address(), row.photoUrl(), row.status(), row.admissionDate(), row.exitDate(),
                row.exitReason(), row.createdAt(), row.updatedAt(), repository.guardians(id));
    }

    @Transactional
    public StudentDtos.StudentResponse update(long id, StudentDtos.UpdateStudentRequest request,
                                               SecurityPrincipal actor, HttpServletRequest servletRequest) {
        StudentRecord before = requireStudent(id, actor);
        if (Set.of("TRANSFERRED", "EXITED").contains(before.status()))
            throw StudentModuleException.conflict("status", "A transferred or exited student profile cannot be edited");
        repository.updateStudent(id, request, actor.userId());
        repository.addHistory(id, "PROFILE_UPDATED", null, null, "Student profile updated", actor.userId());
        audit.record("STUDENT_UPDATED", "STUDENT", Long.toString(id), actor, null, null, null, servletRequest);
        return detail(id, actor);
    }

    @Transactional
    public List<StudentDtos.GuardianResponse> addGuardian(long id, StudentDtos.GuardianRequest request,
            SecurityPrincipal actor, HttpServletRequest servletRequest) {
        requireMutableStudent(id, actor);
        long guardianId = repository.addGuardian(id, request);
        repository.addHistory(id, "GUARDIAN_LINKED", null, Long.toString(guardianId), request.relation(), actor.userId());
        audit.record("STUDENT_GUARDIAN_LINKED", "STUDENT", Long.toString(id), actor, null, null,
                Map.of("guardianId", guardianId, "relation", request.relation()), servletRequest);
        return repository.guardians(id);
    }

    @Transactional
    public List<StudentDtos.GuardianResponse> updateGuardian(long id, long guardianId, StudentDtos.GuardianRequest request,
            SecurityPrincipal actor, HttpServletRequest servletRequest) {
        requireMutableStudent(id, actor);
        requireGuardianRelationship(id, guardianId);
        repository.updateGuardianRelationship(id, guardianId, request);
        repository.addHistory(id, "GUARDIAN_UPDATED", null, Long.toString(guardianId), request.relation(), actor.userId());
        audit.record("STUDENT_GUARDIAN_UPDATED", "STUDENT", Long.toString(id), actor, null, null,
                Map.of("guardianId", guardianId), servletRequest);
        return repository.guardians(id);
    }

    @Transactional
    public void removeGuardian(long id, long guardianId, SecurityPrincipal actor, HttpServletRequest request) {
        requireMutableStudent(id, actor);
        requireGuardianRelationship(id, guardianId);
        repository.unlinkGuardian(id, guardianId);
        repository.addHistory(id, "GUARDIAN_UNLINKED", Long.toString(guardianId), null, "Guardian relationship removed", actor.userId());
        audit.record("STUDENT_GUARDIAN_UNLINKED", "STUDENT", Long.toString(id), actor, null,
                Map.of("guardianId", guardianId), null, request);
    }

    @Transactional
    public StudentDtos.AdmissionResponse createAdmission(StudentDtos.CreateAdmissionRequest request,
            SecurityPrincipal actor, HttpServletRequest servletRequest) {
        if (request.student() == null) throw StudentModuleException.invalid("student", "is required");
        if (repository.admissionApplicationNumberExists(request.applicationNo().trim()))
            throw StudentModuleException.conflict("applicationNo", "already exists; use a unique application number");
        if (repository.studentAdmissionNumberExists(request.student().admissionNo().trim()))
            throw StudentModuleException.conflict("admissionNo",
                    "is already assigned to a student; use a unique admission number");
        long id = repository.createAdmission(request, actor.userId());
        StudentDtos.AdmissionResponse admission = repository.findAdmission(id, actor).orElseThrow(StudentModuleException::admissionNotFound);
        repository.addHistory(admission.studentId(), "ADMISSION_DRAFT_CREATED", null, "DRAFT", request.applicationNo(), actor.userId());
        audit.record("ADMISSION_DRAFT_CREATED", "ADMISSION", Long.toString(id), actor, null,
                null, Map.of("status", "DRAFT"), servletRequest);
        return admission;
    }

    @Transactional(readOnly = true)
    public StudentDtos.PageResponse<StudentDtos.AdmissionResponse> admissions(String status, String search,
            int page, int size, SecurityPrincipal actor) {
        if (page < 0) throw StudentModuleException.invalid("page", "must be zero or greater");
        if (size < 1 || size > 100) throw StudentModuleException.invalid("size", "must be between 1 and 100");
        if (status != null && !ADMISSION_STATUSES.contains(status)) throw StudentModuleException.invalid("status", "is not a supported admission status");
        int offset = Math.toIntExact(Math.min((long) page * size, Integer.MAX_VALUE));
        List<StudentDtos.AdmissionResponse> content = repository.admissions(status, search, size, offset, actor);
        long total = repository.admissionCount(status, search, actor);
        int pages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new StudentDtos.PageResponse<>(content, page, size, total, pages, "appliedOn", "DESC");
    }

    @Transactional
    public StudentDtos.AdmissionResponse submitAdmission(long id, SecurityPrincipal actor, HttpServletRequest request) {
        StudentDtos.AdmissionResponse admission = requireAdmission(id, actor);
        String current = admission.status();
        validateTransition(current, "PENDING");
        if (!repository.hasRequiredDocument(admission.studentId()))
            throw StudentModuleException.invalid("documents", "A supporting document is required before submitting the application");
        repository.changeAdmission(id, "PENDING", null, actor.userId());
        repository.addHistory(admission.studentId(), "ADMISSION_SUBMITTED", current, "PENDING", null, actor.userId());
        audit.record("ADMISSION_SUBMITTED", "ADMISSION", Long.toString(id), actor, null,
                Map.of("status", current), Map.of("status", "PENDING"), request);
        return requireAdmission(id, actor);
    }

    @Transactional
    public StudentDtos.AdmissionResponse decideAdmission(long id, StudentDtos.AdmissionDecisionRequest decision,
            SecurityPrincipal actor, HttpServletRequest request) {
        StudentDtos.AdmissionResponse before = requireAdmission(id, actor);
        validateTransition(before.status(), decision.status());
        if (("PENDING".equals(decision.status()) && "DRAFT".equals(before.status()) || "APPROVED".equals(decision.status())) &&
                !repository.hasRequiredDocument(before.studentId()))
            throw StudentModuleException.invalid("documents", "A supporting document is required before submitting or approving the application");
        if ("APPROVED".equals(decision.status())) repository.activateStudent(before.studentId());
        repository.changeAdmission(id, decision.status(), decision.note(), actor.userId());
        repository.addHistory(before.studentId(), "ADMISSION_STATUS_CHANGED", before.status(), decision.status(), decision.note(), actor.userId());
        audit.record("ADMISSION_STATUS_CHANGED", "ADMISSION", Long.toString(id), actor, null,
                Map.of("status", before.status()), Map.of("status", decision.status()), request);
        return requireAdmission(id, actor);
    }

    @Transactional(readOnly = true)
    public StudentDtos.AdmissionResponse admission(long id, SecurityPrincipal actor) { return requireAdmission(id, actor); }

    @Transactional
    public StudentDtos.StudentResponse transfer(long id, StudentDtos.TransferRequest transfer,
            SecurityPrincipal actor, HttpServletRequest request) {
        StudentRecord before = requireMutableStudent(id, actor);
        if (!"ACTIVE".equals(before.status())) throw StudentModuleException.conflict("status", "Only an active student can be transferred");
        LocalDate date = transfer.effectiveDate() == null ? LocalDate.now() : transfer.effectiveDate();
        String note = "To: " + transfer.destinationSchool() + "; effective: " + date + "; reason: " + transfer.reason();
        repository.changeStudentStatus(id, before.status(), "TRANSFERRED", date, note);
        repository.addHistory(id, "TRANSFERRED", before.status(), "TRANSFERRED", note, actor.userId());
        audit.record("STUDENT_TRANSFERRED", "STUDENT", Long.toString(id), actor, null,
                Map.of("status", before.status()), Map.of("status", "TRANSFERRED"), request);
        return detail(id, actor);
    }

    @Transactional
    public StudentDtos.StudentResponse exit(long id, StudentDtos.ExitRequest exit,
            SecurityPrincipal actor, HttpServletRequest request) {
        StudentRecord before = requireMutableStudent(id, actor);
        if (!"ACTIVE".equals(before.status())) throw StudentModuleException.conflict("status", "Only an active student can exit");
        LocalDate date = exit.effectiveDate() == null ? LocalDate.now() : exit.effectiveDate();
        repository.changeStudentStatus(id, before.status(), "EXITED", date, exit.reason());
        repository.addHistory(id, "EXITED", before.status(), "EXITED", exit.reason(), actor.userId());
        audit.record("STUDENT_EXITED", "STUDENT", Long.toString(id), actor, null,
                Map.of("status", before.status()), Map.of("status", "EXITED"), request);
        return detail(id, actor);
    }

    @Transactional(readOnly = true)
    public List<StudentDtos.StudentHistoryResponse> history(long id, SecurityPrincipal actor) {
        requireStudent(id, actor);
        return repository.history(id);
    }

    @Transactional(readOnly = true)
    public List<StudentDtos.StudentDocumentResponse> documents(long id, SecurityPrincipal actor) {
        requireStudent(id, actor);
        return repository.documents(id);
    }

    @Transactional
    public StudentDtos.StudentDocumentResponse uploadDocument(long id, String docType, MultipartFile file,
            SecurityPrincipal actor, HttpServletRequest request) {
        requireMutableStudent(id, actor);
        if (docType == null || !DOCUMENT_TYPES.contains(docType))
            throw StudentModuleException.invalid("docType", "is not a supported document type");
        if (file == null || file.isEmpty()) throw StudentModuleException.invalid("file", "is required");
        if (file.getSize() > MAX_DOCUMENT_BYTES) throw StudentModuleException.invalid("file", "must not exceed 10 MB");
        String suppliedName = safeFileName(file.getOriginalFilename());
        String extension = suppliedName.contains(".") ? suppliedName.substring(suppliedName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        String expectedMime = EXTENSION_MIME.get(extension);
        if (expectedMime == null || !expectedMime.equalsIgnoreCase(file.getContentType()))
            throw StudentModuleException.invalid("file", "must be a PDF, JPEG or PNG with a matching content type");
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (IOException ex) { throw StudentModuleException.invalid("file", "could not be read"); }
        if (!hasValidSignature(bytes, expectedMime)) throw StudentModuleException.invalid("file", "file content does not match its declared type");

        String storedName = UUID.randomUUID() + "." + extension;
        Path destination = storageRoot.resolve(storedName).normalize();
        if (!destination.startsWith(storageRoot)) throw StudentModuleException.invalid("file", "invalid file name");
        try {
            Files.createDirectories(storageRoot);
            Files.write(destination, bytes);
        } catch (IOException ex) {
            throw StudentModuleException.invalid("file", "could not be stored");
        }
        registerRollbackCleanup(destination);
        try {
            long documentId = repository.createDocument(id, docType, suppliedName, destination.toString(), bytes.length,
                    expectedMime, actor.userId());
            repository.addHistory(id, "DOCUMENT_UPLOADED", null, docType, suppliedName, actor.userId());
            audit.record("STUDENT_DOCUMENT_UPLOADED", "STUDENT", Long.toString(id), actor, null,
                    null, Map.of("documentId", documentId, "docType", docType), request);
            return repository.documents(id).stream().filter(item -> item.id() == documentId).findFirst()
                    .orElseThrow(StudentModuleException::documentNotFound);
        } catch (RuntimeException ex) {
            deleteQuietly(destination);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public DocumentDownload downloadDocument(long id, long documentId, SecurityPrincipal actor) {
        requireStudent(id, actor);
        DocumentRecord record = repository.findDocument(id, documentId).orElseThrow(StudentModuleException::documentNotFound);
        Path path = Path.of(record.filePath()).toAbsolutePath().normalize();
        if (!path.startsWith(storageRoot) || !Files.isRegularFile(path)) throw StudentModuleException.documentNotFound();
        return new DocumentDownload(new FileSystemResource(path), record.fileName(), record.mimeType());
    }

    private StudentRecord requireStudent(long id, SecurityPrincipal actor) {
        return repository.findStudent(id, actor).orElseThrow(StudentModuleException::notFound);
    }
    private StudentRecord requireMutableStudent(long id, SecurityPrincipal actor) {
        StudentRecord student = requireStudent(id, actor);
        if (Set.of("TRANSFERRED", "EXITED").contains(student.status()))
            throw StudentModuleException.conflict("status", "This student record is no longer active");
        return student;
    }
    private void requireGuardianRelationship(long id, long guardianId) {
        if (!repository.guardianRelationshipExists(id, guardianId)) throw StudentModuleException.notFound();
    }
    private StudentDtos.AdmissionResponse requireAdmission(long id, SecurityPrincipal actor) {
        return repository.findAdmission(id, actor).orElseThrow(StudentModuleException::admissionNotFound);
    }
    private static void validateTransition(String from, String to) {
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to))
            throw StudentModuleException.conflict("status", "Admission cannot transition from " + from + " to " + to);
    }
    private static String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) throw StudentModuleException.invalid("fileName", "is required");
        String normalized = fileName.replace('\\', '/');
        String base = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_").trim();
        if (base.isBlank() || base.length() > 255) throw StudentModuleException.invalid("fileName", "must be at most 255 safe characters");
        return base;
    }
    private static boolean hasValidSignature(byte[] b, String mime) {
        if ("application/pdf".equals(mime)) return b.length >= 5 && b[0]=='%' && b[1]=='P' && b[2]=='D' && b[3]=='F' && b[4]=='-';
        if ("image/jpeg".equals(mime)) return b.length >= 3 && (b[0]&0xff)==0xff && (b[1]&0xff)==0xd8 && (b[2]&0xff)==0xff;
        return "image/png".equals(mime) && b.length >= 8 && (b[0]&0xff)==0x89 && b[1]=='P' && b[2]=='N' && b[3]=='G' && (b[4]&0xff)==0x0d && (b[5]&0xff)==0x0a && (b[6]&0xff)==0x1a && (b[7]&0xff)==0x0a;
    }
    private static void registerRollbackCleanup(Path path) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) deleteQuietly(path); }
            });
        }
    }
    private static void deleteQuietly(Path path) { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }

    public record DocumentDownload(Resource resource, String fileName, String mimeType) { }
}
