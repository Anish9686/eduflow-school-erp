package com.erp.portal.dashboard;

import java.util.List;

public record DashboardResponse(List<Metric> metrics, List<Activity> recentActivity) {
    public record Metric(String key, String label, String value, String detail) { }
    public record Activity(String action, String entityType, String actor, String createdAt) { }
}
