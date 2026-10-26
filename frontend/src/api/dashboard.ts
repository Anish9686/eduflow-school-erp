import { apiGet } from './client';

export type DashboardMetric = { key: string; label: string; value: string; detail: string };
export type DashboardActivity = { action: string; entityType: string; actor: string; createdAt: string };
export type DashboardResponse = { metrics: DashboardMetric[]; recentActivity: DashboardActivity[] };

export async function getDashboard(signal?: AbortSignal): Promise<DashboardResponse> {
  return apiGet<DashboardResponse>('/api/v1/dashboard', signal);
}
