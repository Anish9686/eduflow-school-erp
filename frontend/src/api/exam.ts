import { apiGet, apiPost, apiPut, apiDelete } from './client';

export interface ExamSummary {
  id: number;
  name: string;
  examType: string;
  sessionId: number;
  sessionCode: string;
  classId: number;
  className: string;
  sectionId: number | null;
  sectionName: string | null;
  startDate: string;
  endDate: string;
  status: 'PLANNED' | 'DRAFT' | 'SCHEDULED' | 'ONGOING' | 'COMPLETED' | 'PUBLISHED' | 'CANCELLED';
  gradingSchemeId: number | null;
  publishedAt: string | null;
  publishedBy: number | null;
  createdAt: string;
}

export interface Schedule {
  id: number;
  examId: number;
  classId: number;
  className: string;
  sectionId: number;
  sectionName: string;
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  examDate: string;
  startTime: string;
  endTime: string;
  maxMarks: number;
  passMarks: number;
  roomNo: string | null;
}

export interface SubmissionSummary {
  id: number;
  examId: number;
  examName: string;
  sectionId: number;
  sectionName: string;
  classId: number;
  className: string;
  subjectId: number;
  subjectName: string;
  teacherId: number;
  teacherName: string;
  status: 'DRAFT' | 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED';
  studentCount: number;
  submittedAt: string | null;
  reviewedAt: string | null;
  reviewedByName: string | null;
  reviewComments: string | null;
}

export interface ExamDetail extends ExamSummary {
  gradingSchemeName: string | null;
  instructions: string | null;
  publishedByName: string | null;
  createdByName: string;
  schedules: Schedule[];
  submissions: SubmissionSummary[];
}

export interface CreateExamRequest {
  name: string;
  examType: string;
  sessionId: number;
  classId: number;
  sectionId?: number | null;
  startDate: string;
  endDate: string;
  gradingSchemeId?: number | null;
  instructions?: string;
}

export interface UpdateExamRequest extends CreateExamRequest {
  status: string;
}

export interface CreateScheduleRequest {
  classId: number;
  sectionId: number;
  subjectId: number;
  examDate: string;
  startTime: string;
  endTime: string;
  maxMarks: number;
  passMarks: number;
  roomNo?: string;
}

export interface UpdateScheduleRequest {
  examDate: string;
  startTime: string;
  endTime: string;
  maxMarks: number;
  passMarks: number;
  roomNo?: string;
}

export interface StudentMarkInput {
  studentId: number;
  marksObtained: number | null;
  status: 'PRESENT' | 'ABSENT' | 'EXEMPT';
  teacherRemarks?: string;
}

export interface MarkRow {
  id: number | null;
  studentId: number;
  admissionNo: string;
  rollNo: string | null;
  studentName: string;
  marksObtained: number | null;
  maxMarks: number;
  passMarks: number;
  percentage: number | null;
  grade: string | null;
  status: 'PRESENT' | 'ABSENT' | 'EXEMPT';
  teacherRemarks: string | null;
}

export interface SubjectMarksEntryResponse {
  examId: number;
  examName: string;
  sectionId: number;
  sectionName: string;
  classId: number;
  className: string;
  subjectId: number;
  subjectName: string;
  maxMarks: number;
  passMarks: number;
  submissionStatus: string;
  reviewComments: string | null;
  marks: MarkRow[];
}

export interface CorrectionRequest {
  studentId: number;
  subjectId: number;
  marksObtained: number | null;
  status: string;
  reason: string;
}

export interface Correction {
  id: number;
  examId: number;
  studentId: number;
  studentName: string;
  subjectId: number;
  subjectName: string;
  previousMarks: number | null;
  newMarks: number | null;
  previousStatus: string;
  newStatus: string;
  reason: string;
  correctedByName: string;
  correctedAt: string;
}

export interface StudentSubjectResult {
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  marksObtained: number | null;
  maxMarks: number;
  passMarks: number;
  percentage: number | null;
  grade: string | null;
  status: string;
  isPass: boolean;
  teacherRemarks: string | null;
}

export interface StudentResult {
  examId: number;
  examName: string;
  examType: string;
  sessionCode: string;
  studentId: number;
  studentName: string;
  admissionNo: string;
  rollNo: string | null;
  className: string;
  sectionName: string;
  totalMaxMarks: number;
  totalMarksObtained: number;
  percentage: number;
  overallGrade: string;
  gpa: number;
  resultStatus: 'PASS' | 'FAIL';
  rankInSection: number | null;
  rankInClass: number | null;
  isPublished: boolean;
  publishedAt: string | null;
  subjects: StudentSubjectResult[];
}

export interface ReportCard {
  schoolName: string;
  schoolAddress: string;
  academicYear: string;
  examinationName: string;
  studentId: number;
  studentName: string;
  admissionNo: string;
  rollNo: string | null;
  className: string;
  sectionName: string;
  subjects: StudentSubjectResult[];
  totalMaxMarks: number;
  totalMarksObtained: number;
  percentage: number;
  overallGrade: string;
  gpa: number;
  resultStatus: 'PASS' | 'FAIL';
  rankInSection: number | null;
  rankInClass: number | null;
  attendancePercentage: number | null;
  publishedAt: string | null;
  generatedAt: string;
}

export interface GradeDistribution {
  grade: string;
  count: number;
  percentage: number;
}

export interface SubjectPerformance {
  subjectId: number;
  subjectName: string;
  averageMarks: number;
  highestMarks: number;
  lowestMarks: number;
  passPercentage: number;
}

export interface AttentionStudent {
  studentId: number;
  studentName: string;
  admissionNo: string;
  percentage: number;
  resultStatus: string;
  failedSubjectCount: number;
}

export interface ExamAnalyticsSummary {
  examId: number;
  examName: string;
  totalStudents: number;
  appearedStudents: number;
  passedStudents: number;
  failedStudents: number;
  passPercentage: number;
  averagePercentage: number;
  highestPercentage: number;
  lowestPercentage: number;
  gradeDistribution: GradeDistribution[];
  subjectPerformances: SubjectPerformance[];
  studentsNeedingAttention: AttentionStudent[];
}

export interface ExamDashboardOverview {
  totalExams: number;
  upcomingExams: number;
  ongoingExams: number;
  pendingSubmissionsCount: number;
  awaitingReviewCount: number;
  publishedExamsCount: number;
  recentExams: ExamSummary[];
  actionRequiredSubmissions: SubmissionSummary[];
}

export interface GradingScaleRule {
  id?: number;
  grade: string;
  minPercentage: number;
  maxPercentage: number;
  gradePoint: number;
  isPass: boolean;
  remarks?: string;
  orderSeq: number;
}

export interface GradingScheme {
  id: number;
  name: string;
  description: string | null;
  isDefault: boolean;
  rules: GradingScaleRule[];
}

export const examApi = {
  // Examinations
  listExams: (params?: { sessionId?: number; classId?: number; examType?: string; status?: string; search?: string }) =>
    apiGet<ExamSummary[]>('/api/v1/exams', params),

  getExam: (id: number) =>
    apiGet<ExamDetail>(`/api/v1/exams/${id}`),

  createExam: (body: CreateExamRequest) =>
    apiPost<ExamSummary>('/api/v1/exams', body),

  updateExam: (id: number, body: UpdateExamRequest) =>
    apiPut<ExamSummary>(`/api/v1/exams/${id}`, body),

  deleteExam: (id: number) =>
    apiDelete<void>(`/api/v1/exams/${id}`),

  // Schedules
  listSchedules: (examId: number) =>
    apiGet<Schedule[]>(`/api/v1/exams/${examId}/schedules`),

  createSchedule: (examId: number, body: CreateScheduleRequest) =>
    apiPost<Schedule>(`/api/v1/exams/${examId}/schedules`, body),

  updateSchedule: (scheduleId: number, body: UpdateScheduleRequest) =>
    apiPut<Schedule>(`/api/v1/exams/schedules/${scheduleId}`, body),

  deleteSchedule: (scheduleId: number) =>
    apiDelete<void>(`/api/v1/exams/schedules/${scheduleId}`),

  // Marks Entry & Review Workflow
  getSubjectMarks: (examId: number, sectionId: number, subjectId: number) =>
    apiGet<SubjectMarksEntryResponse>(`/api/v1/exams/${examId}/marks`, { sectionId, subjectId }),

  saveDraftMarks: (examId: number, sectionId: number, subjectId: number, marks: StudentMarkInput[]) =>
    apiPost<SubjectMarksEntryResponse>(`/api/v1/exams/${examId}/marks/draft`, { sectionId, subjectId, marks }),

  submitMarks: (examId: number, sectionId: number, subjectId: number) =>
    apiPost<SubmissionSummary>(`/api/v1/exams/${examId}/marks/submit`, { sectionId, subjectId }),

  listSubmissions: (params?: { examId?: number; status?: string }) =>
    apiGet<SubmissionSummary[]>('/api/v1/exams/submissions', params),

  reviewSubmission: (submissionId: number, action: 'APPROVE' | 'REJECT', comments?: string) =>
    apiPost<SubmissionSummary>(`/api/v1/exams/submissions/${submissionId}/review?action=${action}`, { comments }),

  // Results & Publication
  calculateResults: (examId: number) =>
    apiPost<StudentResult[]>(`/api/v1/exams/${examId}/calculate`),

  publishResults: (examId: number) =>
    apiPost<ExamSummary>(`/api/v1/exams/${examId}/publish`),

  unpublishResults: (examId: number) =>
    apiPost<ExamSummary>(`/api/v1/exams/${examId}/unpublish`),

  correctResult: (examId: number, body: CorrectionRequest) =>
    apiPost<Correction>(`/api/v1/exams/${examId}/corrections`, body),

  listCorrections: (examId: number) =>
    apiGet<Correction[]>(`/api/v1/exams/${examId}/corrections`),

  // Student / Parent Results & Report Cards
  getResults: (examId: number, params?: { sectionId?: number; studentId?: number }) =>
    apiGet<StudentResult[]>(`/api/v1/exams/${examId}/results`, params),

  getReportCard: (examId: number, studentId: number) =>
    apiGet<ReportCard>(`/api/v1/exams/${examId}/report-card`, { studentId }),

  // Analytics & Dashboard
  getAnalytics: (examId: number) =>
    apiGet<ExamAnalyticsSummary>(`/api/v1/exams/${examId}/analytics`),

  getDashboard: () =>
    apiGet<ExamDashboardOverview>('/api/v1/exams/dashboard'),

  // Grading Schemes
  listGradingSchemes: () =>
    apiGet<GradingScheme[]>('/api/v1/exams/grading-schemes'),

  createGradingScheme: (body: { name: string; description?: string; rules: GradingScaleRule[] }) =>
    apiPost<GradingScheme>('/api/v1/exams/grading-schemes', body),
};
