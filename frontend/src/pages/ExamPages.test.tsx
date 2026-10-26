import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppProviders } from '../app/AppProviders';
import { ThemeProvider } from '../theme/ThemeProvider';
import { AppRoutes } from '../routes';

function response(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

function renderApp() {
  return render(
    <ThemeProvider>
      <AppProviders>
        <AppRoutes />
      </AppProviders>
    </ThemeProvider>
  );
}

const adminUser = {
  id: 1,
  username: 'admin',
  fullName: 'Admin User',
  roles: ['ADMIN'],
  permissions: [
    'EXAM:READ', 'EXAM:CREATE', 'EXAM:UPDATE', 'EXAM:DELETE', 'EXAM:SCHEDULE',
    'RESULT:READ', 'RESULT:CREATE', 'RESULT:UPDATE', 'RESULT:SUBMIT',
    'RESULT:REVIEW', 'RESULT:APPROVE', 'RESULT:PUBLISH', 'RESULT:UNPUBLISH',
    'RESULT:REPORT:READ', 'RESULT:REPORT:EXPORT', 'ACADEMIC:READ'
  ],
};

const teacherUser = {
  id: 2,
  username: 'teacher',
  fullName: 'Teacher User',
  roles: ['TEACHER'],
  permissions: ['EXAM:READ', 'RESULT:READ', 'RESULT:CREATE', 'RESULT:UPDATE', 'RESULT:SUBMIT', 'ACADEMIC:READ'],
};

const studentUser = {
  id: 3,
  username: 'student',
  fullName: 'Alice Student',
  roles: ['STUDENT'],
  permissions: ['RESULT:READ', 'RESULT:REPORT:READ'],
};

const parentUser = {
  id: 4,
  username: 'parent',
  fullName: 'Parent User',
  roles: ['PARENT_GUARDIAN'],
  permissions: ['RESULT:READ', 'RESULT:REPORT:READ'],
};

const mockExams = [
  {
    id: 1,
    name: 'Mid-Term Exam 2026',
    examType: 'TERMINAL',
    sessionId: 1,
    sessionCode: '2026-2027',
    classId: 1,
    className: 'Class 10',
    sectionId: 1,
    sectionName: 'Section A',
    startDate: '2026-10-01',
    endDate: '2026-10-10',
    status: 'PUBLISHED',
    gradingSchemeId: 1,
    publishedAt: '2026-10-11T10:00:00',
    publishedBy: 1,
    createdAt: '2026-09-15T09:00:00'
  }
];

const mockDashboard = {
  totalExams: 1,
  upcomingExams: 0,
  ongoingExams: 0,
  pendingSubmissionsCount: 0,
  awaitingReviewCount: 1,
  publishedExamsCount: 1,
  recentExams: mockExams,
  actionRequiredSubmissions: []
};

const mockResults = [
  {
    examId: 1,
    examName: 'Mid-Term Exam 2026',
    examType: 'TERMINAL',
    sessionCode: '2026-2027',
    studentId: 101,
    studentName: 'Alice Student',
    admissionNo: 'ADM001',
    rollNo: '10A01',
    className: 'Class 10',
    sectionName: 'Section A',
    totalMaxMarks: 100,
    totalMarksObtained: 92,
    percentage: 92.0,
    overallGrade: 'A+',
    gpa: 10.0,
    resultStatus: 'PASS',
    rankInSection: 1,
    rankInClass: 1,
    isPublished: true,
    publishedAt: '2026-10-11T10:00:00',
    subjects: [
      {
        subjectId: 1,
        subjectCode: 'MATH101',
        subjectName: 'Mathematics',
        marksObtained: 92,
        maxMarks: 100,
        passMarks: 35,
        percentage: 92.0,
        grade: 'A+',
        status: 'PRESENT',
        isPass: true,
        teacherRemarks: 'Excellent'
      }
    ]
  }
];

describe('Results & Examinations Frontend Workflows', () => {
  beforeEach(() => {
    window.sessionStorage.clear();
    window.localStorage.clear();
    window.sessionStorage.setItem('erp-portal-access-token', 'mock-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));

    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);

      if (url.endsWith('/api/v1/me')) {
        return response(adminUser);
      }
      if (url.includes('/api/v1/exams/dashboard')) {
        return response(mockDashboard);
      }
      if (url.includes('/api/v1/exams/submissions')) {
        return response([]);
      }
      if (url.includes('/api/v1/exams/1/results')) {
        return response(mockResults);
      }
      if (url.includes('/api/v1/exams/1/report-card')) {
        return response({
          schoolName: 'EduFlow Model School',
          schoolAddress: '123 Academic Way',
          academicYear: '2026-2027',
          examinationName: 'Mid-Term Exam 2026',
          studentId: 101,
          studentName: 'Alice Student',
          admissionNo: 'ADM001',
          rollNo: '10A01',
          className: 'Class 10',
          sectionName: 'Section A',
          subjects: mockResults[0]?.subjects ?? [],
          totalMaxMarks: 100,
          totalMarksObtained: 92,
          percentage: 92.0,
          overallGrade: 'A+',
          gpa: 10.0,
          resultStatus: 'PASS',
          rankInSection: 1,
          rankInClass: 1,
          attendancePercentage: 98.5,
          publishedAt: '2026-10-11T10:00:00',
          generatedAt: '2026-10-12T10:00:00'
        });
      }
      if (url.includes('/api/v1/exams')) {
        return response(mockExams);
      }
      if (url.includes('/api/v1/academics/sessions')) {
        return response([{ id: 1, code: '2026-2027', name: 'Academic Year 2026-2027', active: true }]);
      }
      if (url.includes('/api/v1/academics/classes')) {
        return response([{ id: 1, name: 'Class 10', orderSeq: 1 }]);
      }
      if (url.includes('/api/v1/academics/sections')) {
        return response([{ id: 1, classId: 1, name: 'Section A' }]);
      }
      if (url.includes('/api/v1/academics/subjects')) {
        return response([{ id: 1, code: 'MATH101', name: 'Mathematics' }]);
      }
      if (url.includes('/api/v1/administration/settings')) {
        return response([]);
      }
      if (url.includes('/api/v1/students')) {
        return response([{ id: 101, firstName: 'Alice', lastName: 'Student', admissionNo: 'ADM001' }]);
      }

      return response({});
    }));
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('renders Examination Dashboard for administrative users', async () => {
    window.history.pushState({}, '', '/app/academics/examinations');
    renderApp();

    expect(await screen.findByText('Results & Examination Management')).toBeInTheDocument();
    expect(await screen.findByText('Total Examinations')).toBeInTheDocument();
    expect(screen.getAllByText('Published Results')[0]).toBeInTheDocument();
  });

  it('renders student scoped view for student role', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(studentUser);
      if (url.includes('/results')) return response(mockResults);
      if (url.includes('/api/v1/exams')) return response(mockExams);
      if (url.includes('/api/v1/administration/settings')) return response([]);
      return response({});
    }));

    window.history.pushState({}, '', '/app/academics/examinations');
    renderApp();

    expect(await screen.findByText('My Examination Results')).toBeInTheDocument();
    expect(await screen.findByText('Alice Student')).toBeInTheDocument();
    expect(await screen.findByText('92%')).toBeInTheDocument();
  });

  it('renders parent scoped view for parent/guardian role', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(parentUser);
      if (url.includes('/api/v1/students')) return response({ content: [{ id: 101, firstName: 'Alice', lastName: 'Student', admissionNo: 'ADM001' }] });
      if (url.includes('/results')) return response(mockResults);
      if (url.includes('/api/v1/exams')) return response(mockExams);
      if (url.includes('/api/v1/administration/settings')) return response([]);
      return response({});
    }));

    window.history.pushState({}, '', '/app/academics/examinations');
    renderApp();

    expect(await screen.findByText("Children's Examination Results")).toBeInTheDocument();
    expect(await screen.findByText('1. Select Child')).toBeInTheDocument();
  });

  it('allows navigation between tabs for administrator', async () => {
    window.history.pushState({}, '', '/app/academics/examinations');
    renderApp();

    expect(await screen.findByText('Examinations')).toBeInTheDocument();
    const examsTab = screen.getByRole('tab', { name: 'Examinations' });
    await userEvent.click(examsTab);

    expect(await screen.findByPlaceholderText('Search examinations...')).toBeInTheDocument();
  });

  it('renders teacher-specific tabs for teacher role', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(teacherUser);
      if (url.includes('/api/v1/exams')) return response(mockExams);
      if (url.includes('/api/v1/administration/settings')) return response([]);
      return response({});
    }));

    window.history.pushState({}, '', '/app/academics/examinations');
    renderApp();

    expect(await screen.findByRole('tab', { name: 'Marks Entry' })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Marks Review' })).not.toBeInTheDocument();
  });
});
