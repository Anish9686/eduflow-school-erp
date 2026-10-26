package com.erp.portal.administration;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class SettingsDtos {
    private SettingsDtos() {}

    public record SettingDto(
            String key,
            String value,
            String category,
            String description,
            Long updatedBy,
            Instant updatedAt,
            int version
    ) {}

    public record UpdateSettingRequest(
            @NotNull(message = "Setting value is required")
            String value
    ) {}
}
