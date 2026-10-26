package com.erp.portal.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RolePermissionsRequest(
        @NotNull @Size(max = 256) Set<@NotBlank @Size(max = 100) String> permissionCodes) {
}
