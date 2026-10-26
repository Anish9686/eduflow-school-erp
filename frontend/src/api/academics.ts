import { apiGet, apiPost, apiPut, apiPatch, apiDelete } from './client';

export type AcademicSession = { id:number; code:string; name:string; startDate:string; endDate:string; active:boolean; createdAt:string };
export type AcademicClass = { id:number; name:string; orderSeq:number; createdAt:string };
export type AcademicSection = { id:number; classId:number; className:string; name:string; capacity:number|null; roomNo:string|null; createdAt:string };
export type AcademicSubject = { id:number; code:string; name:string; description:string|null; createdAt:string };
export type ClassSubject = { id:number; sessionId:number; classId:number; className:string; subjectId:number; subjectCode:string; subjectName:string; active:boolean; createdAt:string };
export type Teacher = { id:number; employeeNo:string; name:string; email:string|null };
export type TeacherAssignment = { id:number; sessionId:number; classId:number; className:string; sectionId:number; sectionName:string; subjectId:number; subjectName:string; staffId:number; teacherName:string; status:string; assignedAt:string };
export type TimetableEntry = { id:number; sessionId:number; classId:number; className:string; sectionId:number; sectionName:string; subjectId:number; subjectName:string; teacherId:number|null; teacherName:string|null; dayOfWeek:number; startTime:string; endTime:string; roomNo:string|null };
export type Homework = { id:number; sessionId:number; classId:number; className:string; sectionId:number; sectionName:string; subjectId:number; subjectName:string; assignedBy:number; teacherName:string; title:string; description:string|null; dueDate:string; createdAt:string };
export type SyllabusProgress = { id:number; sessionId:number; classId:number; className:string; sectionId:number; sectionName:string; subjectId:number; subjectName:string; topic:string; status:'PENDING'|'IN_PROGRESS'|'COMPLETED'; completedAt:string|null; recordedBy:number; actorName:string; createdAt:string; updatedAt:string };
export type AcademicError = Error & { status?:number; fieldErrors?:{field:string;message:string}[] };

export const academicApi = {
  sessions: () => apiGet<AcademicSession[]>('/api/v1/academics/sessions'),
  createSession: (x: unknown) => apiPost<AcademicSession>('/api/v1/academics/sessions', x),
  updateSession: (id: number, x: unknown) => apiPut<AcademicSession>(`/api/v1/academics/sessions/${id}`, x),
  setSessionStatus: (id: number, active: boolean) => apiPatch<AcademicSession>(`/api/v1/academics/sessions/${id}/status`, { active }),

  classes: () => apiGet<AcademicClass[]>('/api/v1/academics/classes'),
  createClass: (x: unknown) => apiPost<AcademicClass>('/api/v1/academics/classes', x),
  updateClass: (id: number, x: unknown) => apiPut<AcademicClass>(`/api/v1/academics/classes/${id}`, x),

  sections: (classId?: number) => apiGet<AcademicSection[]>('/api/v1/academics/sections', { classId }),
  createSection: (x: unknown) => apiPost<AcademicSection>('/api/v1/academics/sections', x),
  updateSection: (id: number, x: unknown) => apiPut<AcademicSection>(`/api/v1/academics/sections/${id}`, x),

  subjects: () => apiGet<AcademicSubject[]>('/api/v1/academics/subjects'),
  createSubject: (x: unknown) => apiPost<AcademicSubject>('/api/v1/academics/subjects', x),
  updateSubject: (id: number, x: unknown) => apiPut<AcademicSubject>(`/api/v1/academics/subjects/${id}`, x),

  classSubjects: (sessionId?: number, classId?: number) => apiGet<ClassSubject[]>('/api/v1/academics/class-subjects', { sessionId, classId }),
  assignSubject: (x: unknown) => apiPost<ClassSubject>('/api/v1/academics/class-subjects', x),
  deactivateClassSubject: (id: number) => apiDelete<void>(`/api/v1/academics/class-subjects/${id}`),

  teachers: () => apiGet<Teacher[]>('/api/v1/academics/teachers'),
  teacherAssignments: (sessionId?: number) => apiGet<TeacherAssignment[]>('/api/v1/academics/teacher-assignments', { sessionId }),
  assignTeacher: (x: unknown) => apiPost<TeacherAssignment>('/api/v1/academics/teacher-assignments', x),
  deactivateTeacher: (id: number) => apiDelete<void>(`/api/v1/academics/teacher-assignments/${id}`),

  timetable: (sessionId?: number, sectionId?: number) => apiGet<TimetableEntry[]>('/api/v1/academics/timetable', { sessionId, sectionId }),
  createTimetable: (x: unknown) => apiPost<TimetableEntry>('/api/v1/academics/timetable', x),
  updateTimetable: (id: number, x: unknown) => apiPut<TimetableEntry>(`/api/v1/academics/timetable/${id}`, x),
  deleteTimetable: (id: number) => apiDelete<void>(`/api/v1/academics/timetable/${id}`),

  homework: (sessionId?: number, sectionId?: number) => apiGet<Homework[]>('/api/v1/academics/homework', { sessionId, sectionId }),
  createHomework: (x: unknown) => apiPost<Homework>('/api/v1/academics/homework', x),
  deleteHomework: (id: number) => apiDelete<void>(`/api/v1/academics/homework/${id}`),

  syllabus: (sessionId?: number, sectionId?: number, subjectId?: number) => apiGet<SyllabusProgress[]>('/api/v1/academics/syllabus', { sessionId, sectionId, subjectId }),
  createSyllabus: (x: unknown) => apiPost<SyllabusProgress>('/api/v1/academics/syllabus', x),
  updateSyllabus: (id: number, x: unknown) => apiPut<SyllabusProgress>(`/api/v1/academics/syllabus/${id}`, x),
};
