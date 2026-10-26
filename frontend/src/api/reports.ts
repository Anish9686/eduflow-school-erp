import { apiFetch, apiGet, buildQueryString } from './client';

export type ReportFilter = { key: string; label: string; type: string; options: string[] };
export type ReportColumn = { key: string; label: string };
export type ReportDefinition = { code: string; title: string; description: string; filters: ReportFilter[]; columns: ReportColumn[]; sortableFields: string[]; formats: string[] };
export type ReportPageData = { code: string; columns: ReportColumn[]; content: Record<string, unknown>[]; page: number; size: number; totalElements: number; totalPages: number; sortBy: string; direction: string };
export type ExportJob = { jobId: string; reportCode: string; status: string; downloadUrl: string | null };

export const reportsApi = {
  catalog: () => apiGet<ReportDefinition[]>('/api/v1/reports'),
  read: (code: string, values: Record<string, string | number | boolean | undefined>, signal?: AbortSignal) =>
    apiGet<ReportPageData>(`/api/v1/reports/${encodeURIComponent(code)}`, values, signal),
  export: async (code: string, values: Record<string, string | number | boolean | undefined>, format: string, asyncExport = true): Promise<{ job: ExportJob } | { blob: Blob; filename: string }> => {
    const query = buildQueryString({ ...values, format, async: asyncExport });
    const response = await apiFetch(`/api/v1/reports/${encodeURIComponent(code)}/export${query}`);
    if (response.status === 202) return { job: await response.json() as ExportJob };
    return { blob: await response.blob(), filename: response.headers.get('Content-Disposition')?.match(/filename="?([^";]+)"?/)?.[1] ?? `${code}.${format.toLowerCase()}` };
  },
  job: (jobId: string) => apiGet<ExportJob>(`/api/v1/reports/exports/${encodeURIComponent(jobId)}`),
  download: async (jobId: string) => {
    const response = await apiFetch(`/api/v1/reports/exports/${encodeURIComponent(jobId)}/download`);
    return { blob: await response.blob(), filename: response.headers.get('Content-Disposition')?.match(/filename="?([^";]+)"?/)?.[1] ?? 'report.csv' };
  },
};

export function saveReport(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a'); link.href = url; link.download = filename; link.click(); URL.revokeObjectURL(url);
}
