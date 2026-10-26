import { useState } from 'react';
import { PageHeader, Tabs } from '../components';
import { useAuth } from '../auth/AuthProvider';
import {
  ExamDashboardView,
  ExamListView,
  ExamScheduleView,
  MarksEntryView,
  MarksReviewView,
  PublishedResultsView,
  ReportCardView,
  ResultAnalyticsView,
  StudentExamViewComponent,
  ParentExamViewComponent
} from './ExamComponents';
import type { ExamSummary } from '../api/exam';
import './exam-pages.css';

export type ExamSubTab =
  | 'dashboard'
  | 'examinations'
  | 'schedules'
  | 'marks-entry'
  | 'marks-review'
  | 'published-results'
  | 'report-cards'
  | 'analytics';

export function ExamManagementPage() {
  const { user, hasPermission } = useAuth();
  const roles = user?.roles ?? [];

  const isStudentOnly = roles.includes('STUDENT') && !roles.some(r =>
    ['SUPER_ADMIN', 'ADMIN', 'MANAGEMENT', 'TEACHER', 'STAFF_ADMIN', 'SUPPORT_AGENT'].includes(r)
  );

  const isParentOnly = roles.includes('PARENT_GUARDIAN') && !roles.some(r =>
    ['SUPER_ADMIN', 'ADMIN', 'MANAGEMENT', 'TEACHER', 'STAFF_ADMIN', 'SUPPORT_AGENT'].includes(r)
  );

  const [activeTab, setActiveTab] = useState<ExamSubTab>('dashboard');
  const [selectedExamForSchedule, setSelectedExamForSchedule] = useState<number | undefined>(undefined);
  const [selectedStudentForReportCard, setSelectedStudentForReportCard] = useState<{ examId: number; studentId: number } | undefined>(undefined);

  // If Student only, render Student Portal Results View
  if (isStudentOnly) {
    return (
      <section className="workspace-page academic-page">
        <PageHeader
          eyebrow="ACADEMICS & EXAMINATIONS"
          title="My Examination Results"
          description="View your published term results, subject grades, and official progress report cards."
        />
        <StudentExamViewComponent />
      </section>
    );
  }

  // If Parent only, render Parent Portal Results View
  if (isParentOnly) {
    return (
      <section className="workspace-page academic-page">
        <PageHeader
          eyebrow="PARENT PORTAL"
          title="Children's Examination Results"
          description="View official published term examination results, grades, and report cards for your linked children."
        />
        <ParentExamViewComponent />
      </section>
    );
  }

  // Determine allowed tabs based on permissions
  const canReview = hasPermission('RESULT:REVIEW');
  const canEnterMarks = hasPermission('RESULT:CREATE') || hasPermission('RESULT:UPDATE') || roles.includes('TEACHER');

  const tabs = [
    { id: 'dashboard' as const, label: <><span className="ds-tab-icon" aria-hidden="true">◫</span>Dashboard</> },
    { id: 'examinations' as const, label: <><span className="ds-tab-icon" aria-hidden="true">📋</span>Examinations</> },
    { id: 'schedules' as const, label: <><span className="ds-tab-icon" aria-hidden="true">📅</span>Schedules</> },
    ...(canEnterMarks ? [{ id: 'marks-entry' as const, label: <><span className="ds-tab-icon" aria-hidden="true">✍</span>Marks Entry</> }] : []),
    ...(canReview ? [{ id: 'marks-review' as const, label: <><span className="ds-tab-icon" aria-hidden="true">✓</span>Marks Review</> }] : []),
    { id: 'published-results' as const, label: <><span className="ds-tab-icon" aria-hidden="true">🏆</span>Published Results</> },
    { id: 'report-cards' as const, label: <><span className="ds-tab-icon" aria-hidden="true">📜</span>Report Cards</> },
    { id: 'analytics' as const, label: <><span className="ds-tab-icon" aria-hidden="true">📊</span>Result Analytics</> },
  ];

  const handleSelectExamForSchedule = (exam: ExamSummary) => {
    setSelectedExamForSchedule(exam.id);
    setActiveTab('schedules');
  };

  const handleSelectStudentReportCard = (examId: number, studentId: number) => {
    setSelectedStudentForReportCard({ examId, studentId });
    setActiveTab('report-cards');
  };

  return (
    <section className="workspace-page academic-page">
      <PageHeader
        eyebrow="ACADEMICS & EVALUATION"
        title="Results & Examination Management"
        description="Comprehensive evaluation system: schedule exams, enter marks, review submissions, publish results, and generate transcripts."
      />

      <div style={{ marginBottom: '1.25rem' }}>
        <Tabs
          tabs={tabs}
          value={activeTab}
          onChange={id => setActiveTab(id as ExamSubTab)}
        />
      </div>

      {activeTab === 'dashboard' && (
        <ExamDashboardView onNavigateTab={tab => setActiveTab(tab as ExamSubTab)} />
      )}

      {activeTab === 'examinations' && (
        <ExamListView onSelectExamForSchedule={handleSelectExamForSchedule} />
      )}

      {activeTab === 'schedules' && (
        <ExamScheduleView initialExamId={selectedExamForSchedule} />
      )}

      {activeTab === 'marks-entry' && canEnterMarks && (
        <MarksEntryView />
      )}

      {activeTab === 'marks-review' && canReview && (
        <MarksReviewView />
      )}

      {activeTab === 'published-results' && (
        <PublishedResultsView onSelectStudentReportCard={handleSelectStudentReportCard} />
      )}

      {activeTab === 'report-cards' && (
        <ReportCardView
          initialExamId={selectedStudentForReportCard?.examId}
          initialStudentId={selectedStudentForReportCard?.studentId}
        />
      )}

      {activeTab === 'analytics' && (
        <ResultAnalyticsView />
      )}
    </section>
  );
}
