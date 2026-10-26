import { apiGet, apiPost, apiDelete } from './client';

export type AttendanceSessionLookup = { id: number; name: string };
export type AttendanceClassLookup = { id: number; name: string };
export type AttendanceSectionLookup = { id: number; classId: number; name: string };
export type AttendanceStaffLookup = { id: number; name: string };

export type StudentAttendance = {
  id: number;
  studentId: number;
  admissionNo: string;
  studentName: string;
  sessionId: number;
  classId: number;
  className: string;
  sectionId: number;
  sectionName: string;
  attendanceDate: string;
  status: 'PRESENT' | 'ABSENT' | 'LATE' | 'HALF_DAY' | 'HOLIDAY' | 'EXCUSED';
  markedBy: number;
  markerName: string;
  markedAt: string;
  remarks: string | null;
  corrected: boolean;
};

export type RosterStudent = {
  studentId: number;
  admissionNo: string;
  studentName: string;
  status: string | null;
  remarks: string | null;
  attendanceId: number | null;
  corrected: boolean;
};

export type AttendanceSummary = {
  total: number;
  present: number;
  absent: number;
  late: number;
  halfDay: number;
  excused: number;
  holiday: number;
};

export type StudentMarkEntry = {
  studentId: number;
  status: 'PRESENT' | 'ABSENT' | 'LATE' | 'HALF_DAY' | 'HOLIDAY' | 'EXCUSED';
  remarks?: string;
};

export type StudentMarkBatchRequest = {
  sessionId: number;
  classId: number;
  sectionId: number;
  attendanceDate: string;
  entries: StudentMarkEntry[];
};

export type MarkBatchResult = {
  created: number;
  alreadyMarked: number;
  records: StudentAttendance[];
};

export type AttendanceCorrection = {
  id: number;
  attendanceId: number;
  oldStatus: string;
  newStatus: string;
  reason: string;
  correctedBy: number;
  correctedByName: string;
  correctedAt: string;
};

export type StaffAttendance = {
  id: number;
  staffId: number;
  employeeNo: string;
  staffName: string;
  attendanceDate: string;
  status: 'PRESENT' | 'ABSENT' | 'LATE' | 'HALF_DAY' | 'ON_LEAVE';
  markedBy: number | null;
  markerName: string;
  markedAt: string;
  remarks: string | null;
  corrected: boolean;
};

export type StaffMarkEntry = {
  staffId: number;
  status: 'PRESENT' | 'ABSENT' | 'LATE' | 'HALF_DAY' | 'ON_LEAVE';
  remarks?: string;
};

export type StaffMarkBatchRequest = {
  attendanceDate: string;
  entries: StaffMarkEntry[];
};

export type StaffMarkBatchResult = {
  created: number;
  alreadyMarked: number;
  records: StaffAttendance[];
};

export type StaffAttendanceCorrection = {
  id: number;
  attendanceId: number;
  oldStatus: string;
  newStatus: string;
  reason: string;
  correctedBy: number;
  correctedByName: string;
  correctedAt: string;
};

export type DateLock = {
  id: number;
  scope: 'STUDENT' | 'STAFF';
  sessionId: number | null;
  attendanceDate: string;
  reason: string;
  lockedBy: number;
  lockedByName: string;
  lockedAt: string;
};

export type DateLockRequest = {
  scope: 'STUDENT' | 'STAFF';
  sessionId?: number | null;
  attendanceDate: string;
  reason: string;
};

export type AttendanceError = Error & { status?: number; fieldErrors?: { field: string; message: string }[] };

export const attendanceApi = {
  sessionOptions: () => apiGet<AttendanceSessionLookup[]>('/api/v1/attendance/options/sessions'),
  classOptions: () => apiGet<AttendanceClassLookup[]>('/api/v1/attendance/options/classes'),
  sectionOptions: (classId?: number) => apiGet<AttendanceSectionLookup[]>('/api/v1/attendance/options/sections', { classId }),
  staffOptions: () => apiGet<AttendanceStaffLookup[]>('/api/v1/attendance/options/staff'),

  roster: (params: { sessionId: number; classId: number; sectionId: number; attendanceDate: string }) =>
    apiGet<RosterStudent[]>('/api/v1/attendance/roster', params),

  markStudents: (payload: StudentMarkBatchRequest, idempotencyKey?: string) =>
    apiPost<MarkBatchResult>(
      '/api/v1/attendance/students/mark',
      payload,
      idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
    ),

  studentRecords: (params: {
    from?: string;
    to?: string;
    sessionId?: number;
    classId?: number;
    sectionId?: number;
    studentId?: number;
    attendanceDate?: string;
    status?: string;
  }) => apiGet<StudentAttendance[]>('/api/v1/attendance/students', params),

  summary: (params: { attendanceDate: string; sessionId?: number; classId?: number; sectionId?: number }) =>
    apiGet<AttendanceSummary>('/api/v1/attendance/summary', params),

  correctStudent: (id: number, payload: { status: string; reason: string }) =>
    apiPost<StudentAttendance>(`/api/v1/attendance/students/${id}/corrections`, payload),

  studentCorrectionHistory: (id: number) =>
    apiGet<AttendanceCorrection[]>(`/api/v1/attendance/students/${id}/corrections`),

  markStaff: (payload: StaffMarkBatchRequest, idempotencyKey?: string) =>
    apiPost<StaffMarkBatchResult>(
      '/api/v1/attendance/staff/mark',
      payload,
      idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
    ),

  staffRecords: (params: { from?: string; to?: string; staffId?: number; status?: string }) =>
    apiGet<StaffAttendance[]>('/api/v1/attendance/staff', params),

  correctStaff: (id: number, payload: { status: string; reason: string }) =>
    apiPost<StaffAttendance>(`/api/v1/attendance/staff/${id}/corrections`, payload),

  staffCorrectionHistory: (id: number) =>
    apiGet<StaffAttendanceCorrection[]>(`/api/v1/attendance/staff/${id}/corrections`),

  locks: (params?: { from?: string; to?: string }) =>
    apiGet<DateLock[]>('/api/v1/attendance/locks', params),

  createLock: (payload: DateLockRequest) =>
    apiPost<DateLock>('/api/v1/attendance/locks', payload),

  unlock: (id: number) =>
    apiDelete<void>(`/api/v1/attendance/locks/${id}`),
};
