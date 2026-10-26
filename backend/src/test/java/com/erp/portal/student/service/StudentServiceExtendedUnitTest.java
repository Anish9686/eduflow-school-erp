package com.erp.portal.student.service;

import com.erp.portal.administration.AuditService;
import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.student.StudentModuleException;
import com.erp.portal.student.api.StudentDtos.*;
import com.erp.portal.student.persistence.StudentRepository;
import com.erp.portal.student.persistence.StudentRepository.StudentRecord;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceExtendedUnitTest {

    @Mock StudentRepository repository;
    @Mock AuditService audit;
    @Mock HttpServletRequest servletRequest;

    private StudentService service;
    private final SecurityPrincipal admin = new SecurityPrincipal(1L, "admin", "Admin", List.of("ADMIN"),
            Set.of("STUDENT:READ", "STUDENT:CREATE", "STUDENT:UPDATE", "STUDENT:DELETE", "ADMISSION:READ", "ADMISSION:DECIDE"), "hash");

    @BeforeEach
    void setUp() {
        service = new StudentService(repository, audit, "target/test-student-docs-" + System.nanoTime());
    }

    @Test
    void listRejectsNegativePage() {
        assertThatThrownBy(() -> service.list("search", "ACTIVE", "firstName", "ASC", -1, 20, admin))
                .isInstanceOf(StudentModuleException.class)
                .matches(e -> "page".equals(((StudentModuleException) e).field()))
                .hasMessageContaining("must be zero or greater");
    }

    @Test
    void listRejectsInvalidPageSize() {
        assertThatThrownBy(() -> service.list("search", "ACTIVE", "firstName", "ASC", 0, 0, admin))
                .isInstanceOf(StudentModuleException.class)
                .matches(e -> "size".equals(((StudentModuleException) e).field()))
                .hasMessageContaining("must be between 1 and 100");

        assertThatThrownBy(() -> service.list("search", "ACTIVE", "firstName", "ASC", 0, 101, admin))
                .isInstanceOf(StudentModuleException.class)
                .matches(e -> "size".equals(((StudentModuleException) e).field()))
                .hasMessageContaining("must be between 1 and 100");
    }

    @Test
    void listRejectsUnsupportedStatus() {
        assertThatThrownBy(() -> service.list("search", "INVALID_STATUS", "firstName", "ASC", 0, 20, admin))
                .isInstanceOf(StudentModuleException.class)
                .matches(e -> "status".equals(((StudentModuleException) e).field()));
    }

    @Test
    void listRejectsUnsupportedSortByOrDirection() {
        assertThatThrownBy(() -> service.list("search", "ACTIVE", "secretColumn", "ASC", 0, 20, admin))
                .isInstanceOf(StudentModuleException.class)
                .matches(e -> "sortBy".equals(((StudentModuleException) e).field()));

        assertThatThrownBy(() -> service.list("search", "ACTIVE", "firstName", "SIDEWAYS", 0, 20, admin))
                .isInstanceOf(StudentModuleException.class)
                .matches(e -> "direction".equals(((StudentModuleException) e).field()));
    }

    @Test
    void cannotUpdateTransferredOrExitedStudent() {
        StudentRecord exitedStudent = new StudentRecord(10L, "ADM-1", "Jane", "Doe", LocalDate.now(), "F", "O+", null, null, null, null, null, null, "EXITED", LocalDate.now(), LocalDate.now(), "Graduated", LocalDateTime.now(), LocalDateTime.now());
        when(repository.findStudent(10L, admin)).thenReturn(Optional.of(exitedStudent));

        UpdateStudentRequest updateReq = new UpdateStudentRequest("Jane", "Smith", null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.update(10L, updateReq, admin, servletRequest))
                .isInstanceOf(StudentModuleException.class)
                .matches(e -> ((StudentModuleException) e).status() == 409)
                .hasMessageContaining("transferred or exited");
    }

    @Test
    void transferRejectsNonActiveStudent() {
        StudentRecord inactiveStudent = new StudentRecord(11L, "ADM-2", "Bob", "Smith", LocalDate.now(), "M", "A+", null, null, null, null, null, null, "TRANSFERRED", LocalDate.now(), null, null, LocalDateTime.now(), LocalDateTime.now());
        when(repository.findStudent(11L, admin)).thenReturn(Optional.of(inactiveStudent));

        TransferRequest transferReq = new TransferRequest("New School", LocalDate.now(), "Relocation");

        assertThatThrownBy(() -> service.transfer(11L, transferReq, admin, servletRequest))
                .isInstanceOf(StudentModuleException.class)
                .matches(e -> ((StudentModuleException) e).status() == 409);
    }

    @Test
    void exitRejectsNonActiveStudent() {
        StudentRecord inactiveStudent = new StudentRecord(12L, "ADM-3", "Charlie", "Brown", LocalDate.now(), "M", "B+", null, null, null, null, null, null, "EXITED", LocalDate.now(), null, null, LocalDateTime.now(), LocalDateTime.now());
        when(repository.findStudent(12L, admin)).thenReturn(Optional.of(inactiveStudent));

        ExitRequest exitReq = new ExitRequest("Completed course", LocalDate.now());

        assertThatThrownBy(() -> service.exit(12L, exitReq, admin, servletRequest))
                .isInstanceOf(StudentModuleException.class)
                .matches(e -> ((StudentModuleException) e).status() == 409);
    }

    @Test
    void guardianOperationsThrowNotFoundWhenRelationshipDoesNotExist() {
        StudentRecord activeStudent = new StudentRecord(1L, "ADM-1", "Alice", "Wonder", LocalDate.now(), "F", "AB+", null, null, null, null, null, null, "ACTIVE", LocalDate.now(), null, null, LocalDateTime.now(), LocalDateTime.now());
        when(repository.findStudent(1L, admin)).thenReturn(Optional.of(activeStudent));
        when(repository.guardianRelationshipExists(1L, 999L)).thenReturn(false);

        GuardianRequest req = new GuardianRequest("John", "1234567890", "john@example.com", "Address", "Engineer", "Father", true);

        assertThatThrownBy(() -> service.updateGuardian(1L, 999L, req, admin, servletRequest))
                .isInstanceOf(StudentModuleException.class)
                .matches(e -> ((StudentModuleException) e).status() == 404);

        assertThatThrownBy(() -> service.removeGuardian(1L, 999L, admin, servletRequest))
                .isInstanceOf(StudentModuleException.class)
                .matches(e -> ((StudentModuleException) e).status() == 404);
    }

    @Test
    void submitAdmissionRejectsInvalidTransitionOrMissingDocument() {
        AdmissionResponse draftAdmission = new AdmissionResponse(1L, "APP-1", 100L, "Student Name", LocalDate.now(), "DRAFT", LocalDateTime.now());
        when(repository.findAdmission(1L, admin)).thenReturn(Optional.of(draftAdmission));
        when(repository.hasRequiredDocument(100L)).thenReturn(false);

        assertThatThrownBy(() -> service.submitAdmission(1L, admin, servletRequest))
                .isInstanceOf(StudentModuleException.class)
                .hasMessageContaining("supporting document is required");
    }

    @Test
    void decideAdmissionRejectsInvalidStatusTransition() {
        AdmissionResponse approvedAdmission = new AdmissionResponse(1L, "APP-1", 100L, "Student Name", LocalDate.now(), "APPROVED", LocalDateTime.now());
        when(repository.findAdmission(1L, admin)).thenReturn(Optional.of(approvedAdmission));

        AdmissionDecisionRequest decision = new AdmissionDecisionRequest("REJECTED", "Cannot reject approved");

        assertThatThrownBy(() -> service.decideAdmission(1L, decision, admin, servletRequest))
                .isInstanceOf(StudentModuleException.class)
                .matches(e -> ((StudentModuleException) e).status() == 409)
                .hasMessageContaining("cannot transition");
    }

    @Test
    void uploadDocumentValidatesTypeSizeAndMagicBytes() {
        StudentRecord activeStudent = new StudentRecord(1L, "ADM-1", "Alice", "Wonder", LocalDate.now(), "F", "AB+", null, null, null, null, null, null, "ACTIVE", LocalDate.now(), null, null, LocalDateTime.now(), LocalDateTime.now());
        when(repository.findStudent(1L, admin)).thenReturn(Optional.of(activeStudent));

        // Invalid document type
        MockMultipartFile validPdf = new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
        assertThatThrownBy(() -> service.uploadDocument(1L, "INVALID_TYPE", validPdf, admin, servletRequest))
                .isInstanceOf(StudentModuleException.class)
                .matches(e -> "docType".equals(((StudentModuleException) e).field()));

        // Empty file
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        assertThatThrownBy(() -> service.uploadDocument(1L, "BIRTH_CERT", emptyFile, admin, servletRequest))
                .isInstanceOf(StudentModuleException.class)
                .matches(e -> "file".equals(((StudentModuleException) e).field()));

        // Fake PDF with invalid magic bytes
        MockMultipartFile fakePdf = new MockMultipartFile("file", "fake.pdf", "application/pdf", "NOT A PDF".getBytes());
        assertThatThrownBy(() -> service.uploadDocument(1L, "BIRTH_CERT", fakePdf, admin, servletRequest))
                .isInstanceOf(StudentModuleException.class)
                .hasMessageContaining("file content does not match its declared type");
    }
}
