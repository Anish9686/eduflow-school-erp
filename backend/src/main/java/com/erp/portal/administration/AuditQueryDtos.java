package com.erp.portal.administration;

import java.time.Instant;
import java.util.List;

public final class AuditQueryDtos {
    private AuditQueryDtos() {}

    public record AuditLogEntry(
            long id,
            Long actorUserId,
            String actorUsername,
            String action,
            String entityType,
            String entityId,
            String oldValue,
            String newValue,
            String ipAddress,
            String traceId,
            Instant createdAt
    ) {}

    public record AuditLogPageResponse(
            List<AuditLogEntry> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {}
}
