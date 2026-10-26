package com.erp.portal.reports;

import java.util.List;
import java.util.Map;

public final class ReportDtos {
    private ReportDtos() {}

    public record FilterDefinition(String key, String label, String type, List<String> options) {}
    public record ColumnDefinition(String key, String label) {}
    public record ReportDefinition(String code, String title, String description,
                                   List<FilterDefinition> filters, List<ColumnDefinition> columns,
                                   List<String> sortableFields, List<String> formats) {}
    public record ReportPage(String code, List<ColumnDefinition> columns, List<Map<String, Object>> content,
                             int page, int size, long totalElements, int totalPages,
                             String sortBy, String direction) {}
    public record ExportJob(String jobId, String reportCode, String status, String downloadUrl) {}
}
