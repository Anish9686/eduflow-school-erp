package com.erp.portal.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RoleAssignmentRequest(
        @NotNull @Size(max = 32) Set<@NotBlank @Size(max = 50) String> roleCodes) {
}
