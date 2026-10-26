package com.erp.portal.administration;

import java.util.List;

public final class ImportExportDtos {
    private ImportExportDtos() {}

    public record RowFailure(
            int rowNumber,
            String identifier,
            String error
    ) {}

    public record ImportResult(
            int totalRows,
            int successCount,
            int failureCount,
            List<RowFailure> errors
    ) {}
}
