import type { ApiErrorResponse } from '../types/api';

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '';
const TOKEN_KEY = 'erp-portal-access-token';

export class ApiRequestError extends Error {
  readonly fieldErrors: ApiErrorResponse['fieldErrors'];
  readonly traceId?: string;

  constructor(public readonly status: number, public readonly body: ApiErrorResponse | null) {
    super(messageFor(status, body));
    this.name = 'ApiRequestError';
    this.fieldErrors = body?.fieldErrors ?? [];
    this.traceId = body?.traceId;
  }
}

function messageFor(status: number, body: ApiErrorResponse | null): string {
  const baseMessage = status === 0
    ? 'Could not reach the server. Check your connection and try again.'
    : status === 401
      ? 'Your session has expired. Sign in again.'
      : status === 403
        ? 'You do not have permission to perform this action.'
        : status === 404
          ? 'The requested record or resource could not be found.'
          : status === 409
            ? body?.message || 'This change conflicts with the current record state.'
            : status === 400
              ? body?.message || 'Some submitted values are invalid.'
              : status === 422
                ? body?.message || 'The server could not accept one or more submitted values.'
              : status >= 500
                ? body?.message || 'The server could not complete the request. Try again shortly.'
                : body?.message || `The request failed (${status}).`;
  const fieldErrors = body?.fieldErrors ?? [];
  if (!fieldErrors.length) return baseMessage;
  return `${baseMessage} ${fieldErrors.map(({ field, message }) => `${field}: ${message}`).join('; ')}`;
}

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');
  if (!headers.has('Authorization')) {
    try {
      const token = window.sessionStorage.getItem(TOKEN_KEY);
      if (token && !path.endsWith('/auth/login')) headers.set('Authorization', `Bearer ${token}`);
    } catch { /* Authentication is optional for public endpoints. */ }
  }
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');

  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}${path}`, { ...init, headers });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
    throw new ApiRequestError(0, null);
  }

  if (!response.ok) {
    let body: ApiErrorResponse | null = null;
    try {
      body = (await response.json()) as ApiErrorResponse;
    } catch {
      body = null;
    }
    if (response.status === 401 && !path.endsWith('/auth/login')) {
      try { window.sessionStorage.removeItem(TOKEN_KEY); } catch { /* Storage may be unavailable. */ }
      window.dispatchEvent(new Event('erp:unauthorized'));
    }
    throw new ApiRequestError(response.status, body);
  }

  return response;
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await apiFetch(path, init);
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export function buildQueryString(values?: Record<string, unknown>): string {
  if (!values) return '';
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, String(value));
    }
  }
  const str = params.toString();
  return str ? `?${str}` : '';
}

export async function apiGet<T>(
  path: string,
  paramsOrSignal?: Record<string, unknown> | AbortSignal,
  maybeSignal?: AbortSignal
): Promise<T> {
  const signal = paramsOrSignal instanceof AbortSignal ? paramsOrSignal : maybeSignal;
  const params = paramsOrSignal instanceof AbortSignal ? undefined : paramsOrSignal;
  const query = buildQueryString(params);
  const fullPath = query ? (path.includes('?') ? `${path}&${query.slice(1)}` : `${path}${query}`) : path;
  return apiRequest<T>(fullPath, { method: 'GET', signal });
}

export async function apiPost<T>(path: string, body?: unknown, headers?: HeadersInit): Promise<T> {
  const isFormData = body instanceof FormData;
  return apiRequest<T>(path, {
    method: 'POST',
    body: body === undefined ? undefined : isFormData ? body : JSON.stringify(body),
    headers,
  });
}

export async function apiPut<T>(path: string, body?: unknown, headers?: HeadersInit): Promise<T> {
  const isFormData = body instanceof FormData;
  return apiRequest<T>(path, {
    method: 'PUT',
    body: body === undefined ? undefined : isFormData ? body : JSON.stringify(body),
    headers,
  });
}

export async function apiPatch<T>(path: string, body?: unknown, headers?: HeadersInit): Promise<T> {
  const isFormData = body instanceof FormData;
  return apiRequest<T>(path, {
    method: 'PATCH',
    body: body === undefined ? undefined : isFormData ? body : JSON.stringify(body),
    headers,
  });
}

export async function apiDelete<T = void>(path: string, body?: unknown, headers?: HeadersInit): Promise<T> {
  const isFormData = body instanceof FormData;
  return apiRequest<T>(path, {
    method: 'DELETE',
    body: body === undefined ? undefined : isFormData ? body : JSON.stringify(body),
    headers,
  });
}

export function extractFieldErrors(error: unknown): Record<string, string> {
  if (error instanceof ApiRequestError && error.fieldErrors?.length) {
    const map: Record<string, string> = {};
    for (const fe of error.fieldErrors) {
      if (fe.field && fe.message) {
        map[fe.field] = fe.message;
      }
    }
    return map;
  }
  return {};
}

export function extractErrorMessage(error: unknown, fallback = 'An unexpected error occurred'): string {
  if (typeof error === 'string') {
    return error;
  }
  if (error instanceof ApiRequestError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return fallback;
}

export const erpQueryKeys = {
  auth: ['auth'] as const,
  dashboard: ['dashboard'] as const,
  students: {
    all: ['students'] as const,
    list: (filters: Record<string, unknown>) => ['students', filters] as const,
    detail: (id: number) => ['students', id] as const,
    documents: (id: number) => ['students', id, 'documents'] as const,
    history: (id: number) => ['students', id, 'history'] as const,
  },
  admissions: {
    all: ['admissions'] as const,
    list: (filters: Record<string, unknown>) => ['admissions', filters] as const,
    detail: (id: number) => ['admissions', id] as const,
  },
  academics: {
    sessions: ['academic-sessions'] as const,
    classes: ['academic-classes'] as const,
    sections: (classId?: number) => ['academic-sections', classId] as const,
    subjects: ['academic-subjects'] as const,
    classSubjects: ['academic-class-subjects'] as const,
    teachers: ['academic-teachers'] as const,
    teacherAssignments: ['academic-teacher-assignments'] as const,
    timetable: (filters: Record<string, unknown>) => ['academic-timetable', filters] as const,
    homework: (filters: Record<string, unknown>) => ['academic-homework', filters] as const,
    syllabus: (filters: Record<string, unknown>) => ['academic-syllabus', filters] as const,
  },
  attendance: {
    roster: (params: Record<string, unknown>) => ['attendance', 'roster', params] as const,
    studentRecords: (params: Record<string, unknown>) => ['attendance', 'students', params] as const,
    summary: (params: Record<string, unknown>) => ['attendance', 'summary', params] as const,
    staffRecords: (params: Record<string, unknown>) => ['attendance', 'staff', params] as const,
    locks: (params?: Record<string, unknown>) => ['attendance', 'locks', params] as const,
  },
  finance: {
    all: ['finance'] as const,
    options: ['finance', 'options'] as const,
    structures: ['finance', 'structures'] as const,
    demands: ['finance', 'demands'] as const,
    payments: (dates: { from: string; to: string }) => ['finance', 'payments', dates] as const,
    receipt: (id: string | number) => ['finance', 'receipt', String(id)] as const,
    discounts: ['finance', 'discounts'] as const,
    expenses: ['finance', 'expenses'] as const,
    summary: (dates: { from: string; to: string }) => ['finance', 'summary', dates] as const,
    reconciliation: ['finance', 'reconciliation'] as const,
  },
  operations: {
    all: ['operations'] as const,
    inventory: ['operations', 'inventory'] as const,
    library: ['operations', 'library'] as const,
    hostel: ['operations', 'hostel'] as const,
    support: ['operations', 'support'] as const,
  },
  transport: {
    all: ['transport'] as const,
    overview: ['transport', 'overview'] as const,
    buses: (params?: Record<string, unknown>) => ['transport', 'buses', params] as const,
    bus: (id: number) => ['transport', 'buses', id] as const,
    routes: (params?: Record<string, unknown>) => ['transport', 'routes', params] as const,
    route: (id: number) => ['transport', 'routes', id] as const,
    stops: (routeId: number) => ['transport', 'routes', routeId, 'stops'] as const,
    assignments: (params?: Record<string, unknown>) => ['transport', 'assignments', params] as const,
    history: (params?: Record<string, unknown>) => ['transport', 'history', params] as const,
    staff: (role?: string) => ['transport', 'staff', role] as const,
    studentOptions: (search?: string) => ['transport', 'student-options', search] as const,
    myAssignment: ['transport', 'my-assignment'] as const,
    myChildren: ['transport', 'my-children'] as const,
  },
  exams: {
    all: ['exams'] as const,
    list: (params?: Record<string, unknown>) => ['exams', 'list', params] as const,
    detail: (id: number) => ['exams', id] as const,
    schedules: (examId: number) => ['exams', examId, 'schedules'] as const,
    marks: (examId: number, sectionId: number, subjectId: number) => ['exams', examId, 'marks', sectionId, subjectId] as const,
    submissions: (params?: Record<string, unknown>) => ['exams', 'submissions', params] as const,
    results: (examId: number, params?: Record<string, unknown>) => ['exams', examId, 'results', params] as const,
    reportCard: (examId: number, studentId: number) => ['exams', examId, 'report-card', studentId] as const,
    analytics: (examId: number) => ['exams', examId, 'analytics'] as const,
    dashboard: ['exams', 'dashboard'] as const,
    gradingSchemes: ['exams', 'grading-schemes'] as const,
    corrections: (examId: number) => ['exams', examId, 'corrections'] as const,
  },
  administration: {
    users: (params?: Record<string, unknown>) => ['admin-users', params] as const,
    roles: ['admin-roles'] as const,
    permissions: ['admin-permissions'] as const,
    auditLogs: (params?: Record<string, unknown>) => ['admin-audit-logs', params] as const,
    settings: ['system-settings'] as const,
  },
  reports: {
    catalog: ['reports', 'catalog'] as const,
    result: (code: string, params: Record<string, unknown>) => ['reports', 'result', code, params] as const,
  },
};

