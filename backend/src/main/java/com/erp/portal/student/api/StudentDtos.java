package com.erp.portal.student.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class StudentDtos {
    private StudentDtos() { }

    public record CreateStudentRequest(
            @NotBlank @Size(max = 50) String admissionNo,
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Past LocalDate dateOfBirth,
            @Pattern(regexp = "MALE|FEMALE|OTHER") String gender,
            @Size(max = 5) String bloodGroup,
            @Size(max = 50) String religion,
            @Size(max = 50) String category,
            @Size(max = 30) String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 4000) String address,
            @Size(max = 500) String photoUrl) { }

    public record UpdateStudentRequest(
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String lastName,
            @Past LocalDate dateOfBirth,
            @Pattern(regexp = "MALE|FEMALE|OTHER") String gender,
            @Size(max = 5) String bloodGroup,
            @Size(max = 50) String religion,
            @Size(max = 50) String category,
            @Size(max = 30) String phone,
            @Email @Size(max = 254) String email,
            @Size(max = 4000) String address,
            @Size(max = 500) String photoUrl) { }

    public record StudentSummary(long id, String admissionNo, String firstName, String lastName,
                                 String status, LocalDate admissionDate, LocalDateTime createdAt) { }

    public record StudentResponse(long id, String admissionNo, String firstName, String lastName,
                                  LocalDate dateOfBirth, String gender, String bloodGroup, String religion,
                                  String category, String phone, String email, String address, String photoUrl,
                                  String status, LocalDate admissionDate, LocalDate exitDate, String exitReason,
                                  LocalDateTime createdAt, LocalDateTime updatedAt,
                                  List<GuardianResponse> guardians) { }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                                  String sortBy, String direction) { }

    public record GuardianRequest(@NotBlank @Size(max = 150) String name,
                                  @Size(max = 30) String phone,
                                  @Email @Size(max = 254) String email,
                                  @Size(max = 2000) String address,
                                  @Size(max = 100) String occupation,
                                  @NotBlank @Size(max = 30) String relation,
                                  boolean primary) { }

    public record GuardianResponse(long id, String name, String phone, String email, String address,
                                   String occupation, String relation, boolean primary) { }

    public record CreateAdmissionRequest(
            @NotBlank @Size(max = 50) String applicationNo,
            @Valid CreateStudentRequest student) { }

    public record AdmissionResponse(long id, String applicationNo, long studentId, String applicantName,
                                    LocalDate appliedOn, String status, LocalDateTime createdAt) { }

    public record AdmissionDecisionRequest(
            @NotBlank @Pattern(regexp = "UNDER_REVIEW|APPROVED|REJECTED|WAITLISTED|PENDING") String status,
            @Size(max = 1000) String note) { }

    public record TransferRequest(@NotBlank @Size(max = 200) String destinationSchool,
                                  LocalDate effectiveDate,
                                  @NotBlank @Size(max = 700) String reason) { }

    public record ExitRequest(@NotBlank @Size(max = 500) String reason, LocalDate effectiveDate) { }

    public record StudentHistoryResponse(long id, String eventType, String previousValue, String newValue,
                                         String note, Long changedBy, LocalDateTime changedAt) { }

    public record StudentDocumentResponse(long id, String docType, String fileName, long fileSizeBytes,
                                          String mimeType, LocalDateTime uploadedAt, Long uploadedBy) { }
}
