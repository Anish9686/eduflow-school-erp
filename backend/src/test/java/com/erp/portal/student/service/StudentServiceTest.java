package com.erp.portal.student.service;

import com.erp.portal.administration.AuditService;
import com.erp.portal.security.SecurityPrincipal;
import com.erp.portal.student.StudentModuleException;
import com.erp.portal.student.api.StudentDtos;
import com.erp.portal.student.persistence.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {
    @Mock StudentRepository repository;
    @Mock AuditService audit;

    private StudentService service;

    @BeforeEach
    void setUp() {
        service = new StudentService(repository, audit, "target/test-student-documents");
    }

    @Test
    void existingAdmissionNumberReturnsFieldConflictWithoutCreatingDraft() {
        var request = createRequest("4321", "1234");
        when(repository.admissionApplicationNumberExists("4321")).thenReturn(false);
        when(repository.studentAdmissionNumberExists("1234")).thenReturn(true);

        Throwable thrown = catchThrowable(() -> service.createAdmission(request, actor(), null));

        assertThat(thrown).isInstanceOf(StudentModuleException.class);
        var conflict = (StudentModuleException) thrown;
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.field()).isEqualTo("admissionNo");
        assertThat(conflict.getMessage()).contains("already assigned").contains("unique admission number");
        verify(repository, never()).createAdmission(any(), anyLong());
    }

    @Test
    void uniqueIdentifiersCreateAdmissionDraft() {
        var request = createRequest("APP-NEW", "ST-NEW");
        when(repository.admissionApplicationNumberExists("APP-NEW")).thenReturn(false);
        when(repository.studentAdmissionNumberExists("ST-NEW")).thenReturn(false);
        when(repository.createAdmission(request, 7L)).thenReturn(42L);
        var expected = new StudentDtos.AdmissionResponse(42L, "APP-NEW", 11L, "Anish Mishra",
                LocalDate.now(), "DRAFT", LocalDateTime.now());
        when(repository.findAdmission(42L, actor())).thenReturn(java.util.Optional.of(expected));

        var actual = service.createAdmission(request, actor(), null);

        assertThat(actual).isEqualTo(expected);
        verify(repository).addHistory(11L, "ADMISSION_DRAFT_CREATED", null, "DRAFT", "APP-NEW", 7L);
        verify(audit).record("ADMISSION_DRAFT_CREATED", "ADMISSION", "42", actor(), null, null,
                java.util.Map.of("status", "DRAFT"), null);
    }

    private static StudentDtos.CreateAdmissionRequest createRequest(String applicationNo, String admissionNo) {
        var student = new StudentDtos.CreateStudentRequest(admissionNo, "Anish", "Mishra",
                null, null, null, null, null, "08603318171", "anish123@example.com", null, null);
        return new StudentDtos.CreateAdmissionRequest(applicationNo, student);
    }

    private static SecurityPrincipal actor() {
        return new SecurityPrincipal(7L, "admissions", "Admissions User", List.of("ADMIN"),
                Set.of("STUDENT:CREATE"), "session-hash");
    }
}
