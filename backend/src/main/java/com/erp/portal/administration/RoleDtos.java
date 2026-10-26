package com.erp.portal.administration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class RoleDtos {
    private RoleDtos() {}

    public record RoleDto(
            long id,
            String code,
            String name,
            String description,
            boolean isSystem,
            Instant createdAt,
            Instant updatedAt,
            List<String> permissions
    ) {}

    public record CreateRoleRequest(
            @NotBlank(message = "Role code is required")
            @Size(min = 2, max = 50, message = "Role code must be between 2 and 50 characters")
            @Pattern(regexp = "^[A-Z0-9_]+$", message = "Role code must contain only uppercase letters, numbers, and underscores")
            String code,

            @NotBlank(message = "Role name is required")
            @Size(max = 100, message = "Role name cannot exceed 100 characters")
            String name,

            @Size(max = 255, message = "Description cannot exceed 255 characters")
            String description,

            List<String> permissionCodes
    ) {}

    public record UpdateRoleRequest(
            @NotBlank(message = "Role name is required")
            @Size(max = 100, message = "Role name cannot exceed 100 characters")
            String name,

            @Size(max = 255, message = "Description cannot exceed 255 characters")
            String description
    ) {}

    public record PermissionDto(
            long id,
            String code,
            String description,
            String module
    ) {}
}
