package com.erp.portal.administration;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class UserDtos {
    private UserDtos() {}

    public record UserDto(
            long id,
            String username,
            String email,
            String fullName,
            String phone,
            String status,
            int failedAttempts,
            Instant lastLoginAt,
            Instant createdAt,
            Instant updatedAt,
            List<String> roles
    ) {}

    public record CreateUserRequest(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
            String username,

            @NotBlank(message = "Email is required")
            @Email(message = "Email must be valid")
            @Size(max = 254, message = "Email cannot exceed 254 characters")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
            String password,

            @Size(max = 200, message = "Full name cannot exceed 200 characters")
            String fullName,

            @Size(max = 30, message = "Phone cannot exceed 30 characters")
            String phone,

            List<String> roleCodes
    ) {}

    public record UpdateUserRequest(
            @Email(message = "Email must be valid")
            @Size(max = 254, message = "Email cannot exceed 254 characters")
            String email,

            @Size(max = 200, message = "Full name cannot exceed 200 characters")
            String fullName,

            @Size(max = 30, message = "Phone cannot exceed 30 characters")
            String phone,

            @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
            String password
    ) {}

    public record UserStatusRequest(
            @NotBlank(message = "Status is required")
            String status
    ) {}

    public record UserPageResponse(
            List<UserDto> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
