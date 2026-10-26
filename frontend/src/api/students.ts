import { apiFetch, apiGet, apiPost, apiPut, apiPatch, apiDelete } from './client';

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number; sortBy: string; direction: string };
export type StudentSummary = { id: number; admissionNo: string; firstName: string; lastName: string | null; status: string; admissionDate: string | null; createdAt: string };
export type Guardian = { id: number; name: string; phone: string | null; email: string | null; address: string | null; occupation: string | null; relation: string; primary: boolean };
export type Student = { id: number; admissionNo: string; firstName: string; lastName: string | null; dateOfBirth: string | null; gender: string | null; bloodGroup: string | null; religion: string | null; category: string | null; phone: string | null; email: string | null; address: string | null; photoUrl: string | null; status: string; admissionDate: string | null; exitDate: string | null; exitReason: string | null; createdAt: string; updatedAt: string; guardians: Guardian[] };
export type StudentHistory = { id: number; eventType: string; previousValue: string | null; newValue: string | null; note: string | null; changedBy: number | null; changedAt: string };
export type StudentDocument = { id: number; docType: string; fileName: string; fileSizeBytes: number; mimeType: string; uploadedAt: string; uploadedBy: number | null };
export type Admission = { id: number; applicationNo: string; studentId: number; applicantName: string; appliedOn: string; status: string; createdAt: string };
export type ApiFailure = Error & { status?: number; fieldErrors?: { field: string; message: string }[]; traceId?: string };

export const studentApi = {
  list: (filters: { search?: string; status?: string; sortBy?: string; direction?: string; page?: number; size?: number }, signal?: AbortSignal) =>
    apiGet<Page<StudentSummary>>('/api/v1/students', filters, signal),
  get: (id: number, signal?: AbortSignal) =>
    apiGet<Student>(`/api/v1/students/${id}`, signal),
  create: (body: Record<string, unknown>) =>
    apiPost<Student>('/api/v1/students', body),
  update: (id: number, body: Record<string, unknown>) =>
    apiPut<Student>(`/api/v1/students/${id}`, body),
  addGuardian: (id: number, body: Record<string, unknown>) =>
    apiPost<Guardian[]>(`/api/v1/students/${id}/guardians`, body),
  updateGuardian: (id: number, guardianId: number, body: Record<string, unknown>) =>
    apiPut<Guardian[]>(`/api/v1/students/${id}/guardians/${guardianId}`, body),
  removeGuardian: (id: number, guardianId: number) =>
    apiDelete<void>(`/api/v1/students/${id}/guardians/${guardianId}`),
  documents: (id: number, signal?: AbortSignal) =>
    apiGet<StudentDocument[]>(`/api/v1/students/${id}/documents`, signal),
  uploadDocument: (id: number, docType: string, file: File) => {
    const form = new FormData();
    form.set('docType', docType);
    form.set('file', file);
    return apiPost<StudentDocument>(`/api/v1/students/${id}/documents`, form);
  },
  downloadDocument: (id: number, documentId: number) =>
    apiFetch(`/api/v1/students/${id}/documents/${documentId}/content`),
  history: (id: number, signal?: AbortSignal) =>
    apiGet<StudentHistory[]>(`/api/v1/students/${id}/history`, signal),
  transfer: (id: number, body: Record<string, unknown>) =>
    apiPost<Student>(`/api/v1/students/${id}/transfer`, body),
  exit: (id: number, body: Record<string, unknown>) =>
    apiPost<Student>(`/api/v1/students/${id}/exit`, body),
  admissions: (filters: { status?: string; search?: string; page?: number; size?: number }, signal?: AbortSignal) =>
    apiGet<Page<Admission>>('/api/v1/admissions', filters, signal),
  admission: (id: number, signal?: AbortSignal) =>
    apiGet<Admission>(`/api/v1/admissions/${id}`, signal),
  createAdmission: (body: Record<string, unknown>) =>
    apiPost<Admission>('/api/v1/admissions', body),
  submitAdmission: (id: number) =>
    apiPost<Admission>(`/api/v1/admissions/${id}/submit`),
  decideAdmission: (id: number, status: string, note: string) =>
    apiPatch<Admission>(`/api/v1/admissions/${id}/status`, { status, note }),
};
