import { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  DataTable,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  Modal,
  Select,
  StatusBadge,
  Textarea
} from '../components';
import { useAuth } from '../auth/AuthProvider';
import {
  examApi,
  type ExamSummary,
  type ExamDetail,
  type Schedule,
  type SubmissionSummary,
  type StudentResult,
  type ReportCard
} from '../api/exam';
import { academicApi } from '../api/academics';
import { studentApi } from '../api/students';

// Helper hook for academic references
export function useExamAcademicRefs() {
  const sessions = useQuery({ queryKey: ['academic-sessions'], queryFn: academicApi.sessions });
  const classes = useQuery({ queryKey: ['academic-classes'], queryFn: academicApi.classes });
  const sections = useQuery({ queryKey: ['academic-sections'], queryFn: () => academicApi.sections() });
  const subjects = useQuery({ queryKey: ['academic-subjects'], queryFn: academicApi.subjects });
  return {
    sessions: sessions.data ?? [],
    classes: classes.data ?? [],
    sections: sections.data ?? [],
    subjects: subjects.data ?? [],
    isLoading: sessions.isPending || classes.isPending || sections.isPending || subjects.isPending,
  };
}

// =============================================================================
// 1. DASHBOARD VIEW
// =============================================================================

export function ExamDashboardView({ onNavigateTab }: { onNavigateTab?: (tab: string) => void }) {
  const { user } = useAuth();
  const query = useQuery({ queryKey: ['exam-dashboard'], queryFn: examApi.getDashboard });
  const data = query.data;

  const roles = user?.roles ?? [];
  const isTeacher = roles.includes('TEACHER') && !roles.some(r => ['SUPER_ADMIN', 'ADMIN'].includes(r));

  if (query.isPending) {
    return <Card className="exam-loading">Loading examination dashboard overview…</Card>;
  }

  if (query.isError) {
    return <Alert tone="danger">Failed to load examination dashboard. Please retry.</Alert>;
  }

  return (
    <div className="exam-page-container">
      <div className="exam-kpi-grid">
        <div className="exam-kpi-card">
          <div className="exam-kpi-header">
            <span className="exam-kpi-title">Total Examinations</span>
            <span className="exam-kpi-icon">📝</span>
          </div>
          <div className="exam-kpi-value">{data?.totalExams ?? 0}</div>
          <span className="exam-kpi-subtext">Configured academic terms</span>
        </div>

        <div className="exam-kpi-card">
          <div className="exam-kpi-header">
            <span className="exam-kpi-title">Upcoming Exams</span>
            <span className="exam-kpi-icon">📅</span>
          </div>
          <div className="exam-kpi-value">{data?.upcomingExams ?? 0}</div>
          <span className="exam-kpi-subtext">Scheduled soon</span>
        </div>

        <div className="exam-kpi-card">
          <div className="exam-kpi-header">
            <span className="exam-kpi-title">{isTeacher ? 'My Pending Submissions' : 'Submissions In Progress'}</span>
            <span className="exam-kpi-icon">⏳</span>
          </div>
          <div className="exam-kpi-value">{data?.pendingSubmissionsCount ?? 0}</div>
          <span className="exam-kpi-subtext">Drafts / In-entry</span>
        </div>

        <div className="exam-kpi-card">
          <div className="exam-kpi-header">
            <span className="exam-kpi-title">Awaiting Admin Review</span>
            <span className="exam-kpi-icon">🔍</span>
          </div>
          <div className="exam-kpi-value">{data?.awaitingReviewCount ?? 0}</div>
          <span className="exam-kpi-subtext">Marks submitted by teachers</span>
        </div>

        <div className="exam-kpi-card">
          <div className="exam-kpi-header">
            <span className="exam-kpi-title">Published Results</span>
            <span className="exam-kpi-icon">🎓</span>
          </div>
          <div className="exam-kpi-value">{data?.publishedExamsCount ?? 0}</div>
          <span className="exam-kpi-subtext">Visible to students & parents</span>
        </div>
      </div>

      {/* Action Required Submissions */}
      {data?.actionRequiredSubmissions && data.actionRequiredSubmissions.length > 0 && (
        <Card title={isTeacher ? 'Submissions Requiring Attention' : 'Submissions Awaiting Administrative Review'}>
          <div className="exam-table-wrap">
            <table className="marks-table">
              <thead>
                <tr>
                  <th>Examination</th>
                  <th>Class & Section</th>
                  <th>Subject</th>
                  <th>Teacher</th>
                  <th>Status</th>
                  <th>Submitted At</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {data.actionRequiredSubmissions.map(sub => (
                  <tr key={sub.id}>
                    <td><strong>{sub.examName}</strong></td>
                    <td>{sub.className} ({sub.sectionName})</td>
                    <td>{sub.subjectName}</td>
                    <td>{sub.teacherName}</td>
                    <td><StatusBadge status={sub.status} /></td>
                    <td>{sub.submittedAt ? new Date(sub.submittedAt).toLocaleDateString() : '—'}</td>
                    <td>
                      <Button
                        size="sm"
                        variant={isTeacher ? 'secondary' : 'primary'}
                        onClick={() => onNavigateTab?.(isTeacher ? 'marks-entry' : 'marks-review')}
                      >
                        {isTeacher ? 'Edit & Resubmit' : 'Review Marks'}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}

      {/* Recent Examinations */}
      <Card title="Recent Examinations">
        {data?.recentExams && data.recentExams.length > 0 ? (
          <div className="exam-table-wrap">
            <table className="marks-table">
              <thead>
                <tr>
                  <th>Examination Name</th>
                  <th>Type</th>
                  <th>Class</th>
                  <th>Academic Session</th>
                  <th>Period</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {data.recentExams.map(ex => (
                  <tr key={ex.id}>
                    <td><strong>{ex.name}</strong></td>
                    <td>{ex.examType}</td>
                    <td>{ex.className} {ex.sectionName ? `(${ex.sectionName})` : ''}</td>
                    <td>{ex.sessionCode}</td>
                    <td>{ex.startDate} to {ex.endDate}</td>
                    <td><StatusBadge status={ex.status} /></td>
                    <td>
                      <Button size="sm" variant="ghost" onClick={() => onNavigateTab?.('examinations')}>
                        View Details →
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState title="No recent examinations" description="Create an examination to begin tracking student performance." />
        )}
      </Card>
    </div>
  );
}

// =============================================================================
// 2. EXAMINATIONS LIST & MANAGEMENT VIEW
// =============================================================================

export function ExamListView({ onSelectExamForSchedule }: { onSelectExamForSchedule?: (exam: ExamSummary) => void }) {
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const canManage = hasPermission('EXAM:CREATE') || hasPermission('EXAM:UPDATE');
  const canDelete = hasPermission('EXAM:DELETE');

  const refs = useExamAcademicRefs();

  const [search, setSearch] = useState('');
  const [sessionFilter, setSessionFilter] = useState('');
  const [classFilter, setClassFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');

  const [openCreate, setOpenCreate] = useState(false);
  const [editingExam, setEditingExam] = useState<ExamSummary | null>(null);
  const [detailModalExam, setDetailModalExam] = useState<ExamDetail | null>(null);
  const [errorMsg, setErrorMsg] = useState('');
  const [modalError, setModalError] = useState('');

  // Form state
  const [formData, setFormData] = useState({
    name: '',
    examType: 'TERMINAL',
    sessionId: '',
    classId: '',
    sectionId: '',
    startDate: '',
    endDate: '',
    instructions: '',
    status: 'PLANNED'
  });

  const examsQuery = useQuery({
    queryKey: ['exams-list', sessionFilter, classFilter, statusFilter, search],
    queryFn: () => examApi.listExams({
      sessionId: sessionFilter ? Number(sessionFilter) : undefined,
      classId: classFilter ? Number(classFilter) : undefined,
      status: statusFilter === 'DRAFT' || statusFilter === 'PLANNED' ? undefined : (statusFilter || undefined),
      search: search || undefined
    })
  });

  const displayedExams = (examsQuery.data ?? []).filter(x => {
    if (statusFilter === 'DRAFT' || statusFilter === 'PLANNED') {
      return x.status === 'DRAFT' || x.status === 'PLANNED';
    }
    return true;
  });

  const saveMutation = useMutation({
    mutationFn: () => {
      const trimmedName = formData.name.trim();
      if (!trimmedName) throw new Error('Examination name is required.');
      if (!formData.sessionId || Number(formData.sessionId) <= 0) throw new Error('Please select an Academic Year.');
      if (!formData.classId || Number(formData.classId) <= 0) throw new Error('Please select an Applicable Class.');
      if (!formData.startDate) throw new Error('Start date is required.');
      if (!formData.endDate) throw new Error('End date is required.');
      if (formData.startDate > formData.endDate) throw new Error('End date cannot be earlier than start date.');

      const payload = {
        name: trimmedName,
        examType: formData.examType,
        sessionId: Number(formData.sessionId),
        classId: Number(formData.classId),
        sectionId: formData.sectionId ? Number(formData.sectionId) : null,
        startDate: formData.startDate,
        endDate: formData.endDate,
        instructions: formData.instructions || undefined,
      };

      if (editingExam) {
        return examApi.updateExam(editingExam.id, { ...payload, status: formData.status });
      }
      return examApi.createExam(payload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['exams-list'] });
      queryClient.refetchQueries({ queryKey: ['exams-list'] });
      queryClient.invalidateQueries({ queryKey: ['exam-dashboard'] });
      setOpenCreate(false);
      setEditingExam(null);
      setModalError('');
      setErrorMsg('');
    },
    onError: (err: any) => {
      setModalError(err.message || 'Failed to save examination.');
    }
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => examApi.deleteExam(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['exams-list'] });
      queryClient.refetchQueries({ queryKey: ['exams-list'] });
      queryClient.invalidateQueries({ queryKey: ['exam-dashboard'] });
    },
    onError: (err: any) => {
      setErrorMsg(err.message || 'Cannot delete examination.');
    }
  });

  const openNewModal = () => {
    setEditingExam(null);
    setFormData({
      name: '',
      examType: 'TERMINAL',
      sessionId: refs.sessions[0]?.id ? String(refs.sessions[0].id) : '',
      classId: refs.classes[0]?.id ? String(refs.classes[0].id) : '',
      sectionId: '',
      startDate: new Date().toISOString().slice(0, 10),
      endDate: new Date(Date.now() + 7 * 86400000).toISOString().slice(0, 10),
      instructions: '',
      status: 'PLANNED'
    });
    setErrorMsg('');
    setModalError('');
    setOpenCreate(true);
  };

  const openEditModal = (exam: ExamSummary) => {
    setEditingExam(exam);
    setFormData({
      name: exam.name,
      examType: exam.examType,
      sessionId: String(exam.sessionId),
      classId: String(exam.classId),
      sectionId: exam.sectionId ? String(exam.sectionId) : '',
      startDate: exam.startDate,
      endDate: exam.endDate,
      instructions: '',
      status: exam.status
    });
    setErrorMsg('');
    setModalError('');
    setOpenCreate(true);
  };

  const viewDetails = async (exam: ExamSummary) => {
    try {
      const details = await examApi.getExam(exam.id);
      setDetailModalExam(details);
    } catch (e: any) {
      setErrorMsg(e.message || 'Failed to load details.');
    }
  };

  const columns = [
    { key: 'name', header: 'Exam Name', render: (x: ExamSummary) => <strong>{x.name}</strong> },
    { key: 'examType', header: 'Type', render: (x: ExamSummary) => x.examType },
    { key: 'className', header: 'Applicable Class', render: (x: ExamSummary) => `${x.className} ${x.sectionName ? `(${x.sectionName})` : ''}` },
    { key: 'sessionCode', header: 'Academic Year', render: (x: ExamSummary) => x.sessionCode },
    { key: 'dates', header: 'Period', render: (x: ExamSummary) => `${x.startDate} to ${x.endDate}` },
    { key: 'status', header: 'Status', render: (x: ExamSummary) => <StatusBadge status={x.status} /> },
    {
      key: 'actions',
      header: 'Actions',
      render: (x: ExamSummary) => (
        <div className="exam-row-actions">
          <Button size="sm" variant="ghost" onClick={() => viewDetails(x)}>Details</Button>
          {canManage && (
            <>
              <Button size="sm" variant="ghost" onClick={() => openEditModal(x)}>Edit</Button>
              <Button size="sm" variant="secondary" onClick={() => onSelectExamForSchedule?.(x)}>Schedules →</Button>
            </>
          )}
          {canDelete && (x.status === 'DRAFT' || x.status === 'PLANNED') && (
            <Button
              size="sm"
              variant="danger"
              disabled={deleteMutation.isPending}
              onClick={() => {
                if (window.confirm(`Delete examination "${x.name}"?`)) {
                  deleteMutation.mutate(x.id);
                }
              }}
            >
              Delete
            </Button>
          )}
        </div>
      )
    }
  ];

  return (
    <div className="exam-page-container">
      {errorMsg && <Alert tone="danger">{errorMsg}</Alert>}

      <div className="exam-filter-bar">
        <div className="exam-filter-group">
          <Input
            placeholder="Search examinations..."
            value={search}
            onChange={e => setSearch(e.target.value)}
            style={{ width: 220 }}
          />

          <Select value={sessionFilter} onChange={e => setSessionFilter(e.target.value)}>
            <option value="">All Academic Years</option>
            {refs.sessions.map(s => <option key={s.id} value={s.id}>{s.name} ({s.code})</option>)}
          </Select>

          <Select value={classFilter} onChange={e => setClassFilter(e.target.value)}>
            <option value="">All Classes</option>
            {refs.classes.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
          </Select>

          <Select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
            <option value="">All Statuses</option>
            <option value="PLANNED">PLANNED / DRAFT</option>
            <option value="SCHEDULED">SCHEDULED</option>
            <option value="ONGOING">ONGOING</option>
            <option value="COMPLETED">COMPLETED</option>
            <option value="PUBLISHED">PUBLISHED</option>
            <option value="CANCELLED">CANCELLED</option>
          </Select>
        </div>

        {canManage && (
          <Button variant="primary" onClick={openNewModal}>+ New Examination</Button>
        )}
      </div>

      <Card>
        {examsQuery.isPending ? (
          <div>Loading examinations…</div>
        ) : examsQuery.isError ? (
          <ErrorState
            title="Failed to load examinations"
            description={(examsQuery.error as any)?.message || 'Could not fetch examination records.'}
            onRetry={() => void examsQuery.refetch()}
          />
        ) : displayedExams.length > 0 ? (
          <DataTable columns={columns} rows={displayedExams} rowKey={x => String(x.id)} caption="Examinations catalog" />
        ) : (
          <EmptyState title="No examinations found" description="Adjust your filters or configure a new examination." />
        )}
      </Card>

      {/* Create / Edit Modal */}
      <Modal
        open={openCreate}
        onClose={() => setOpenCreate(false)}
        title={editingExam ? 'Edit Examination' : 'Create New Examination'}
      >
        <form onSubmit={e => { e.preventDefault(); setModalError(''); saveMutation.mutate(); }}>
          {modalError && (
            <div style={{ marginBottom: '1rem' }}>
              <Alert tone="danger">{modalError}</Alert>
            </div>
          )}

          <FormField label="Examination Name" required>
            <Input
              value={formData.name}
              onChange={e => setFormData(f => ({ ...f, name: e.target.value }))}
              placeholder="e.g. Mid-Term Examination 2026"
              required
            />
          </FormField>

          <FormField label="Examination Type" required>
            <Select
              value={formData.examType}
              onChange={e => setFormData(f => ({ ...f, examType: e.target.value }))}
            >
              <option value="UNIT_TEST">Unit Test</option>
              <option value="PERIODIC_ASSESSMENT">Periodic Assessment</option>
              <option value="TERMINAL">Terminal / Half-Yearly</option>
              <option value="ANNUAL">Annual Examination</option>
              <option value="PRACTICAL">Practical Assessment</option>
              <option value="FINAL">Final Board Examination</option>
            </Select>
          </FormField>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <FormField label="Academic Year" required>
              <Select
                value={formData.sessionId}
                onChange={e => setFormData(f => ({ ...f, sessionId: e.target.value }))}
                required
              >
                <option value="">Select session</option>
                {refs.sessions.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
              </Select>
            </FormField>

            <FormField label="Applicable Class" required>
              <Select
                value={formData.classId}
                onChange={e => setFormData(f => ({ ...f, classId: e.target.value, sectionId: '' }))}
                required
              >
                <option value="">Select class</option>
                {refs.classes.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}
              </Select>
            </FormField>
          </div>

          <FormField label="Applicable Section (Optional)">
            <Select
              value={formData.sectionId}
              onChange={e => setFormData(f => ({ ...f, sectionId: e.target.value }))}
            >
              <option value="">All Sections (Whole Class)</option>
              {refs.sections
                .filter(s => !formData.classId || String(s.classId) === String(formData.classId))
                .map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </Select>
          </FormField>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <FormField label="Start Date" required>
              <Input
                type="date"
                value={formData.startDate}
                onChange={e => setFormData(f => ({ ...f, startDate: e.target.value }))}
                required
              />
            </FormField>

            <FormField label="End Date" required>
              <Input
                type="date"
                value={formData.endDate}
                onChange={e => setFormData(f => ({ ...f, endDate: e.target.value }))}
                required
              />
            </FormField>
          </div>

          {editingExam && (
            <FormField label="Status">
              <Select
                value={formData.status}
                onChange={e => setFormData(f => ({ ...f, status: e.target.value }))}
              >
                <option value="PLANNED">PLANNED</option>
                <option value="DRAFT">DRAFT</option>
                <option value="SCHEDULED">SCHEDULED</option>
                <option value="ONGOING">ONGOING</option>
                <option value="COMPLETED">COMPLETED</option>
                <option value="PUBLISHED">PUBLISHED</option>
                <option value="CANCELLED">CANCELLED</option>
              </Select>
            </FormField>
          )}

          <FormField label="Instructions & General Rules">
            <Textarea
              value={formData.instructions}
              onChange={e => setFormData(f => ({ ...f, instructions: e.target.value }))}
              placeholder="e.g. Calculators strictly prohibited. Duration 3 hours."
            />
          </FormField>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.25rem' }}>
            <Button type="button" variant="secondary" onClick={() => setOpenCreate(false)}>Cancel</Button>
            <Button disabled={saveMutation.isPending}>{saveMutation.isPending ? 'Saving…' : 'Save Examination'}</Button>
          </div>
        </form>
      </Modal>

      {/* Details Modal */}
      {detailModalExam && (
        <Modal
          open={Boolean(detailModalExam)}
          onClose={() => setDetailModalExam(null)}
          title={`Examination Details: ${detailModalExam.name}`}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.75rem' }}>
              <div><strong>Type:</strong> {detailModalExam.examType}</div>
              <div><strong>Academic Session:</strong> {detailModalExam.sessionCode}</div>
              <div><strong>Class:</strong> {detailModalExam.className} {detailModalExam.sectionName ? `(${detailModalExam.sectionName})` : ''}</div>
              <div><strong>Status:</strong> <StatusBadge status={detailModalExam.status} /></div>
              <div><strong>Start Date:</strong> {detailModalExam.startDate}</div>
              <div><strong>End Date:</strong> {detailModalExam.endDate}</div>
              <div><strong>Grading Scheme:</strong> {detailModalExam.gradingSchemeName || 'Standard 10-Point Scale'}</div>
              <div><strong>Created By:</strong> {detailModalExam.createdByName}</div>
            </div>

            {detailModalExam.instructions && (
              <div style={{ background: 'var(--surface-muted)', padding: '0.75rem', borderRadius: '6px' }}>
                <strong>Instructions:</strong>
                <p style={{ margin: '0.25rem 0 0 0' }}>{detailModalExam.instructions}</p>
              </div>
            )}

            <Card title={`Scheduled Subjects (${detailModalExam.schedules.length})`}>
              {detailModalExam.schedules.length > 0 ? (
                <table className="marks-table">
                  <thead>
                    <tr>
                      <th>Subject</th>
                      <th>Date</th>
                      <th>Time</th>
                      <th>Max Marks</th>
                      <th>Pass Marks</th>
                      <th>Room</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detailModalExam.schedules.map(s => (
                      <tr key={s.id}>
                        <td><strong>{s.subjectName}</strong> ({s.subjectCode})</td>
                        <td>{s.examDate}</td>
                        <td>{s.startTime} - {s.endTime}</td>
                        <td>{s.maxMarks}</td>
                        <td>{s.passMarks}</td>
                        <td>{s.roomNo || '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <EmptyState title="No schedules configured" description="Add subject schedules to enable marks entry." />
              )}
            </Card>

            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button variant="secondary" onClick={() => setDetailModalExam(null)}>Close</Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}

// =============================================================================
// 3. EXAMINATION SCHEDULE VIEW
// =============================================================================

export function ExamScheduleView({ initialExamId }: { initialExamId?: number }) {
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const canSchedule = hasPermission('EXAM:SCHEDULE') || hasPermission('EXAM:CREATE');

  const refs = useExamAcademicRefs();
  const examsQuery = useQuery({ queryKey: ['exams-list'], queryFn: () => examApi.listExams() });

  const [selectedExamId, setSelectedExamId] = useState<string>(initialExamId ? String(initialExamId) : '');
  const [openModal, setOpenModal] = useState(false);
  const [editingSchedule, setEditingSchedule] = useState<Schedule | null>(null);
  const [errorMsg, setErrorMsg] = useState('');

  useEffect(() => {
    if (!selectedExamId && examsQuery.data?.[0]) {
      setSelectedExamId(String(examsQuery.data[0].id));
    }
  }, [examsQuery.data, selectedExamId]);

  const selectedExam = examsQuery.data?.find(e => String(e.id) === selectedExamId);

  const schedulesQuery = useQuery({
    queryKey: ['exam-schedules', selectedExamId],
    queryFn: () => selectedExamId ? examApi.listSchedules(Number(selectedExamId)) : Promise.resolve([]),
    enabled: Boolean(selectedExamId)
  });

  // Filter sections by selected exam's class
  const applicableSections = refs.sections.filter(s => selectedExam && s.classId === selectedExam.classId);

  const [form, setForm] = useState({
    sectionId: '',
    subjectId: '',
    examDate: '',
    startTime: '09:00',
    endTime: '12:00',
    maxMarks: '100',
    passMarks: '35',
    roomNo: ''
  });

  const saveMutation = useMutation({
    mutationFn: () => {
      const payload = {
        classId: selectedExam!.classId,
        sectionId: Number(form.sectionId),
        subjectId: Number(form.subjectId),
        examDate: form.examDate,
        startTime: form.startTime,
        endTime: form.endTime,
        maxMarks: Number(form.maxMarks),
        passMarks: Number(form.passMarks),
        roomNo: form.roomNo || undefined
      };

      if (editingSchedule) {
        return examApi.updateSchedule(editingSchedule.id, payload);
      }
      return examApi.createSchedule(Number(selectedExamId), payload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['exam-schedules', selectedExamId] });
      setOpenModal(false);
      setEditingSchedule(null);
    },
    onError: (err: any) => setErrorMsg(err.message || 'Failed to save schedule.')
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => examApi.deleteSchedule(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['exam-schedules', selectedExamId] }),
    onError: (err: any) => setErrorMsg(err.message || 'Failed to delete schedule.')
  });

  const openCreateModal = () => {
    setEditingSchedule(null);
    setForm({
      sectionId: applicableSections[0]?.id ? String(applicableSections[0].id) : '',
      subjectId: refs.subjects[0]?.id ? String(refs.subjects[0].id) : '',
      examDate: selectedExam?.startDate || '',
      startTime: '09:00',
      endTime: '12:00',
      maxMarks: '100',
      passMarks: '35',
      roomNo: 'Hall 1'
    });
    setErrorMsg('');
    setOpenModal(true);
  };

  const openEditModal = (sch: Schedule) => {
    setEditingSchedule(sch);
    setForm({
      sectionId: String(sch.sectionId),
      subjectId: String(sch.subjectId),
      examDate: sch.examDate,
      startTime: sch.startTime,
      endTime: sch.endTime,
      maxMarks: String(sch.maxMarks),
      passMarks: String(sch.passMarks),
      roomNo: sch.roomNo || ''
    });
    setErrorMsg('');
    setOpenModal(true);
  };

  return (
    <div className="exam-page-container">
      {errorMsg && <Alert tone="danger">{errorMsg}</Alert>}

      <div className="exam-filter-bar">
        <div style={{ minWidth: 320 }}>
          <FormField label="Choose Examination">
            <Select value={selectedExamId} onChange={e => setSelectedExamId(e.target.value)}>
              <option value="">Select examination</option>
              {examsQuery.data?.map(ex => (
                <option key={ex.id} value={ex.id}>
                  {ex.name} — {ex.className} ({ex.sessionCode}) [{ex.status}]
                </option>
              ))}
            </Select>
          </FormField>
        </div>

        {canSchedule && selectedExam && (
          <Button variant="primary" onClick={openCreateModal}>+ Schedule Subject</Button>
        )}
      </div>

      {selectedExam && (
        <Card title={`Timetable Schedule: ${selectedExam.name}`}>
          <div style={{ marginBottom: '1rem', color: 'var(--text-muted)', fontSize: '0.9rem' }}>
            <strong>Class:</strong> {selectedExam.className} | <strong>Period:</strong> {selectedExam.startDate} to {selectedExam.endDate} | <strong>Status:</strong> <StatusBadge status={selectedExam.status} />
          </div>

          {schedulesQuery.isPending ? (
            <div>Loading timetable schedule…</div>
          ) : schedulesQuery.data && schedulesQuery.data.length > 0 ? (
            <div className="exam-table-wrap">
              <table className="marks-table">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Time Slot</th>
                    <th>Section</th>
                    <th>Subject</th>
                    <th>Max Marks</th>
                    <th>Passing Marks</th>
                    <th>Venue / Room</th>
                    {canSchedule && <th>Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {schedulesQuery.data.map(sch => (
                    <tr key={sch.id}>
                      <td><strong>{sch.examDate}</strong></td>
                      <td>{sch.startTime} – {sch.endTime}</td>
                      <td>{sch.sectionName}</td>
                      <td><strong>{sch.subjectName}</strong> <span style={{ color: 'var(--text-muted)' }}>({sch.subjectCode})</span></td>
                      <td>{sch.maxMarks}</td>
                      <td>{sch.passMarks}</td>
                      <td>{sch.roomNo || '—'}</td>
                      {canSchedule && (
                        <td>
                          <div className="exam-row-actions">
                            <Button size="sm" variant="ghost" onClick={() => openEditModal(sch)}>Edit</Button>
                            <Button
                              size="sm"
                              variant="danger"
                              onClick={() => {
                                if (window.confirm(`Remove schedule for ${sch.subjectName}?`)) {
                                  deleteMutation.mutate(sch.id);
                                }
                              }}
                            >
                              Remove
                            </Button>
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState title="No schedules configured" description="Click '+ Schedule Subject' to configure subject dates and timings." />
          )}
        </Card>
      )}

      {/* Schedule Form Modal */}
      <Modal
        open={openModal}
        onClose={() => setOpenModal(false)}
        title={editingSchedule ? 'Edit Schedule Slot' : 'Add Subject to Schedule'}
      >
        <form onSubmit={e => { e.preventDefault(); setErrorMsg(''); saveMutation.mutate(); }}>
          {!editingSchedule && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
              <FormField label="Section" required>
                <Select
                  value={form.sectionId}
                  onChange={e => setForm(f => ({ ...f, sectionId: e.target.value }))}
                  required
                >
                  <option value="">Select section</option>
                  {applicableSections.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                </Select>
              </FormField>

              <FormField label="Subject" required>
                <Select
                  value={form.subjectId}
                  onChange={e => setForm(f => ({ ...f, subjectId: e.target.value }))}
                  required
                >
                  <option value="">Select subject</option>
                  {refs.subjects.map(s => <option key={s.id} value={s.id}>{s.name} ({s.code})</option>)}
                </Select>
              </FormField>
            </div>
          )}

          <FormField label="Examination Date" required>
            <Input
              type="date"
              value={form.examDate}
              min={selectedExam?.startDate}
              max={selectedExam?.endDate}
              onChange={e => setForm(f => ({ ...f, examDate: e.target.value }))}
              required
            />
          </FormField>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <FormField label="Start Time" required>
              <Input
                type="time"
                value={form.startTime}
                onChange={e => setForm(f => ({ ...f, startTime: e.target.value }))}
                required
              />
            </FormField>

            <FormField label="End Time" required>
              <Input
                type="time"
                value={form.endTime}
                onChange={e => setForm(f => ({ ...f, endTime: e.target.value }))}
                required
              />
            </FormField>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <FormField label="Maximum Marks" required>
              <Input
                type="number"
                min="1"
                step="0.5"
                value={form.maxMarks}
                onChange={e => setForm(f => ({ ...f, maxMarks: e.target.value }))}
                required
              />
            </FormField>

            <FormField label="Passing Marks" required>
              <Input
                type="number"
                min="0"
                step="0.5"
                value={form.passMarks}
                onChange={e => setForm(f => ({ ...f, passMarks: e.target.value }))}
                required
              />
            </FormField>
          </div>

          <FormField label="Room / Venue No.">
            <Input
              value={form.roomNo}
              onChange={e => setForm(f => ({ ...f, roomNo: e.target.value }))}
              placeholder="e.g. Hall A, Room 204"
            />
          </FormField>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.25rem' }}>
            <Button type="button" variant="secondary" onClick={() => setOpenModal(false)}>Cancel</Button>
            <Button disabled={saveMutation.isPending}>{saveMutation.isPending ? 'Saving…' : 'Save Schedule'}</Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

// =============================================================================
// 4. MARKS ENTRY VIEW (TEACHER & ADMIN WORKFLOW)
// =============================================================================

export function MarksEntryView() {
  const queryClient = useQueryClient();
  const examsQuery = useQuery({ queryKey: ['exams-list'], queryFn: () => examApi.listExams() });

  const [selectedExamId, setSelectedExamId] = useState<string>('');
  const [selectedSectionId, setSelectedSectionId] = useState<string>('');
  const [selectedSubjectId, setSelectedSubjectId] = useState<string>('');

  const [marksState, setMarksState] = useState<Record<number, { marks: string; status: 'PRESENT' | 'ABSENT' | 'EXEMPT'; remarks: string }>>({});
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'danger'; message: string } | null>(null);

  // Schedules query for the selected exam to know valid section/subject combinations
  const schedulesQuery = useQuery({
    queryKey: ['exam-schedules', selectedExamId],
    queryFn: () => selectedExamId ? examApi.listSchedules(Number(selectedExamId)) : Promise.resolve([]),
    enabled: Boolean(selectedExamId)
  });

  // Distinct sections and subjects scheduled
  const availableSections = Array.from(
    new Map(schedulesQuery.data?.map(s => [s.sectionId, { id: s.sectionId, name: s.sectionName }])).values()
  );

  const availableSubjects = schedulesQuery.data
    ?.filter(s => !selectedSectionId || s.sectionId === Number(selectedSectionId))
    .map(s => ({ id: s.subjectId, name: s.subjectName, code: s.subjectCode, maxMarks: s.maxMarks, passMarks: s.passMarks })) ?? [];

  // Marks data query
  const marksQuery = useQuery({
    queryKey: ['subject-marks', selectedExamId, selectedSectionId, selectedSubjectId],
    queryFn: () => (selectedExamId && selectedSectionId && selectedSubjectId)
      ? examApi.getSubjectMarks(Number(selectedExamId), Number(selectedSectionId), Number(selectedSubjectId))
      : Promise.resolve(null),
    enabled: Boolean(selectedExamId && selectedSectionId && selectedSubjectId)
  });

  // Populate local marks form when data loads
  useEffect(() => {
    if (marksQuery.data?.marks) {
      const map: Record<number, { marks: string; status: 'PRESENT' | 'ABSENT' | 'EXEMPT'; remarks: string }> = {};
      for (const m of marksQuery.data.marks) {
        map[m.studentId] = {
          marks: m.marksObtained !== null ? String(m.marksObtained) : '',
          status: m.status,
          remarks: m.teacherRemarks || ''
        };
      }
      setMarksState(map);
    }
  }, [marksQuery.data]);

  const saveDraftMutation = useMutation({
    mutationFn: () => {
      const marksPayload = Object.entries(marksState).map(([stdId, val]) => ({
        studentId: Number(stdId),
        marksObtained: (val.status === 'PRESENT' && val.marks !== '') ? Number(val.marks) : null,
        status: val.status,
        teacherRemarks: val.remarks || undefined
      }));
      return examApi.saveDraftMarks(Number(selectedExamId), Number(selectedSectionId), Number(selectedSubjectId), marksPayload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['subject-marks', selectedExamId, selectedSectionId, selectedSubjectId] });
      queryClient.invalidateQueries({ queryKey: ['exam-dashboard'] });
      setFeedback({ tone: 'success', message: 'Draft marks saved successfully.' });
    },
    onError: (err: any) => {
      setFeedback({ tone: 'danger', message: err.message || 'Failed to save marks.' });
    }
  });

  const submitMutation = useMutation({
    mutationFn: () => examApi.submitMarks(Number(selectedExamId), Number(selectedSectionId), Number(selectedSubjectId)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['subject-marks', selectedExamId, selectedSectionId, selectedSubjectId] });
      queryClient.invalidateQueries({ queryKey: ['exam-dashboard'] });
      setFeedback({ tone: 'success', message: 'Marks submitted for administrative review successfully.' });
    },
    onError: (err: any) => {
      setFeedback({ tone: 'danger', message: err.message || 'Cannot submit marks.' });
    }
  });

  const updateStudentMark = (studentId: number, field: 'marks' | 'status' | 'remarks', value: any) => {
    setMarksState(prev => {
      const current = prev[studentId] || { marks: '', status: 'PRESENT' as const, remarks: '' };
      return {
        ...prev,
        [studentId]: {
          marks: field === 'marks' ? String(value) : current.marks,
          status: field === 'status' ? (value as 'PRESENT' | 'ABSENT' | 'EXEMPT') : current.status,
          remarks: field === 'remarks' ? String(value) : current.remarks
        }
      };
    });
  };

  const marksData = marksQuery.data;
  const isLocked = marksData?.submissionStatus === 'SUBMITTED' || marksData?.submissionStatus === 'APPROVED';

  return (
    <div className="exam-page-container">
      {feedback && <Alert tone={feedback.tone}>{feedback.message}</Alert>}

      <Card>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '1rem' }}>
          <FormField label="1. Examination" required>
            <Select
              value={selectedExamId}
              onChange={e => {
                setSelectedExamId(e.target.value);
                setSelectedSectionId('');
                setSelectedSubjectId('');
                setFeedback(null);
              }}
            >
              <option value="">Select examination</option>
              {examsQuery.data?.map(ex => (
                <option key={ex.id} value={ex.id}>{ex.name} ({ex.className})</option>
              ))}
            </Select>
          </FormField>

          <FormField label="2. Section" required>
            <Select
              value={selectedSectionId}
              disabled={!selectedExamId}
              onChange={e => {
                setSelectedSectionId(e.target.value);
                setSelectedSubjectId('');
                setFeedback(null);
              }}
            >
              <option value="">Select section</option>
              {availableSections.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
            </Select>
          </FormField>

          <FormField label="3. Scheduled Subject" required>
            <Select
              value={selectedSubjectId}
              disabled={!selectedSectionId}
              onChange={e => {
                setSelectedSubjectId(e.target.value);
                setFeedback(null);
              }}
            >
              <option value="">Select subject</option>
              {availableSubjects.map(s => (
                <option key={s.id} value={s.id}>{s.name} (Max: {s.maxMarks})</option>
              ))}
            </Select>
          </FormField>
        </div>
      </Card>

      {marksData && (
        <Card title={`Marks Entry: ${marksData.subjectName} — ${marksData.className} (${marksData.sectionName})`}>
          {/* Submission status banner */}
          <div className="workflow-status-banner">
            <div>
              <strong>Submission Status: </strong>
              <span className={`workflow-status-badge workflow-${marksData.submissionStatus}`}>
                {marksData.submissionStatus}
              </span>
              {marksData.reviewComments && (
                <div style={{ marginTop: '0.4rem', color: '#b91c1c' }}>
                  <strong>Admin Feedback: </strong> {marksData.reviewComments}
                </div>
              )}
            </div>

            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <Button
                variant="secondary"
                disabled={isLocked || saveDraftMutation.isPending}
                onClick={() => saveDraftMutation.mutate()}
              >
                {saveDraftMutation.isPending ? 'Saving Draft…' : 'Save Draft'}
              </Button>

              <Button
                variant="primary"
                disabled={isLocked || submitMutation.isPending}
                onClick={() => {
                  if (window.confirm('Submit marks for administrative review? You will not be able to edit them while under review.')) {
                    submitMutation.mutate();
                  }
                }}
              >
                {submitMutation.isPending ? 'Submitting…' : 'Submit for Review'}
              </Button>
            </div>
          </div>

          {/* Student Table */}
          <div className="marks-entry-table-wrap">
            <table className="marks-table">
              <thead>
                <tr>
                  <th style={{ width: 100 }}>Roll No</th>
                  <th>Student Name</th>
                  <th>Admission No</th>
                  <th>Status</th>
                  <th>Marks Obtained (Max: {marksData.maxMarks})</th>
                  <th>Pass Marks</th>
                  <th>Teacher Remarks</th>
                </tr>
              </thead>
              <tbody>
                {marksData.marks.map(m => {
                  const curr = marksState[m.studentId] || { marks: '', status: 'PRESENT' as const, remarks: '' };
                  const isPresent = curr.status === 'PRESENT';
                  const numericMark = Number(curr.marks);
                  const isExceeded = isPresent && curr.marks !== '' && numericMark > marksData.maxMarks;
                  const isNegative = isPresent && curr.marks !== '' && numericMark < 0;

                  return (
                    <tr key={m.studentId} style={{ background: isExceeded || isNegative ? '#fee2e2' : undefined }}>
                      <td><strong>{m.rollNo || '—'}</strong></td>
                      <td><strong>{m.studentName}</strong></td>
                      <td>{m.admissionNo}</td>
                      <td>
                        <select
                          className="marks-status-select"
                          disabled={isLocked}
                          value={curr.status}
                          onChange={e => updateStudentMark(m.studentId, 'status', e.target.value)}
                        >
                          <option value="PRESENT">PRESENT</option>
                          <option value="ABSENT">ABSENT</option>
                          <option value="EXEMPT">EXEMPT</option>
                        </select>
                      </td>
                      <td>
                        <input
                          type="number"
                          step="0.5"
                          min="0"
                          max={marksData.maxMarks}
                          className="marks-input"
                          disabled={isLocked || !isPresent}
                          placeholder={isPresent ? '0.0' : '—'}
                          value={isPresent ? curr.marks : ''}
                          onChange={e => updateStudentMark(m.studentId, 'marks', e.target.value)}
                        />
                        {isExceeded && (
                          <div style={{ color: '#b91c1c', fontSize: '0.75rem', marginTop: '2px' }}>
                            Exceeds max ({marksData.maxMarks})
                          </div>
                        )}
                        {isNegative && (
                          <div style={{ color: '#b91c1c', fontSize: '0.75rem', marginTop: '2px' }}>
                            Cannot be negative
                          </div>
                        )}
                      </td>
                      <td>{marksData.passMarks}</td>
                      <td>
                        <input
                          type="text"
                          className="marks-input"
                          style={{ width: '100%', textAlign: 'left' }}
                          disabled={isLocked}
                          placeholder="Optional remarks"
                          value={curr.remarks}
                          onChange={e => updateStudentMark(m.studentId, 'remarks', e.target.value)}
                        />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}

// =============================================================================
// 5. MARKS REVIEW VIEW (ADMIN WORKFLOW)
// =============================================================================

export function MarksReviewView() {
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const canReview = hasPermission('RESULT:REVIEW');

  const [statusFilter, setStatusFilter] = useState('SUBMITTED');
  const [selectedSubmission, setSelectedSubmission] = useState<SubmissionSummary | null>(null);
  const [reviewAction, setReviewAction] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [reviewComments, setReviewComments] = useState('');
  const [errorMsg, setErrorMsg] = useState('');

  const submissionsQuery = useQuery({
    queryKey: ['submissions-list', statusFilter],
    queryFn: () => examApi.listSubmissions({ status: statusFilter || undefined })
  });

  const subjectMarksQuery = useQuery({
    queryKey: ['review-marks-detail', selectedSubmission?.examId, selectedSubmission?.sectionId, selectedSubmission?.subjectId],
    queryFn: () => selectedSubmission
      ? examApi.getSubjectMarks(selectedSubmission.examId, selectedSubmission.sectionId, selectedSubmission.subjectId)
      : Promise.resolve(null),
    enabled: Boolean(selectedSubmission)
  });

  const reviewMutation = useMutation({
    mutationFn: () => {
      if (reviewAction === 'REJECT' && !reviewComments.trim()) {
        throw new Error('Please provide comments explaining why marks are rejected or returned for correction.');
      }
      return examApi.reviewSubmission(selectedSubmission!.id, reviewAction, reviewComments.trim());
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['submissions-list'] });
      queryClient.invalidateQueries({ queryKey: ['exam-dashboard'] });
      setSelectedSubmission(null);
      setReviewComments('');
    },
    onError: (err: any) => setErrorMsg(err.message || 'Failed to review submission.')
  });

  const openReviewModal = (sub: SubmissionSummary) => {
    setSelectedSubmission(sub);
    setReviewAction('APPROVE');
    setReviewComments('');
    setErrorMsg('');
  };

  const columns = [
    { key: 'examName', header: 'Examination', render: (x: SubmissionSummary) => <strong>{x.examName}</strong> },
    { key: 'class', header: 'Class / Section', render: (x: SubmissionSummary) => `${x.className} (${x.sectionName})` },
    { key: 'subjectName', header: 'Subject', render: (x: SubmissionSummary) => x.subjectName },
    { key: 'teacherName', header: 'Submitted By (Teacher)', render: (x: SubmissionSummary) => x.teacherName },
    { key: 'studentCount', header: 'Students', render: (x: SubmissionSummary) => x.studentCount },
    { key: 'status', header: 'Status', render: (x: SubmissionSummary) => <StatusBadge status={x.status} /> },
    {
      key: 'actions',
      header: 'Actions',
      render: (x: SubmissionSummary) => (
        <Button size="sm" variant="primary" onClick={() => openReviewModal(x)}>
          Review Marks →
        </Button>
      )
    }
  ];

  return (
    <div className="exam-page-container">
      {errorMsg && <Alert tone="danger">{errorMsg}</Alert>}

      <div className="exam-filter-bar">
        <FormField label="Filter By Submission Status">
          <Select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
            <option value="">All Submissions</option>
            <option value="SUBMITTED">Awaiting Review (SUBMITTED)</option>
            <option value="APPROVED">APPROVED</option>
            <option value="REJECTED">REJECTED / Returned</option>
            <option value="DRAFT">DRAFT</option>
          </Select>
        </FormField>
      </div>

      <Card title="Teacher Marks Submissions Awaiting Review">
        {submissionsQuery.isPending ? (
          <div>Loading submissions…</div>
        ) : submissionsQuery.data && submissionsQuery.data.length > 0 ? (
          <DataTable columns={columns} rows={submissionsQuery.data} rowKey={x => String(x.id)} caption="Marks Submissions" />
        ) : (
          <EmptyState title="No submissions awaiting review" description="When teachers submit marks sheets, they will appear here for administrative approval." />
        )}
      </Card>

      {/* Review & Approval Modal */}
      {selectedSubmission && (
        <Modal
          open={Boolean(selectedSubmission)}
          onClose={() => setSelectedSubmission(null)}
          title={`Review Marks: ${selectedSubmission.subjectName} — ${selectedSubmission.className} (${selectedSubmission.sectionName})`}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.75rem', fontSize: '0.9rem' }}>
              <div><strong>Exam:</strong> {selectedSubmission.examName}</div>
              <div><strong>Teacher:</strong> {selectedSubmission.teacherName}</div>
              <div><strong>Current Status:</strong> <StatusBadge status={selectedSubmission.status} /></div>
              <div><strong>Submitted At:</strong> {selectedSubmission.submittedAt ? new Date(selectedSubmission.submittedAt).toLocaleString() : '—'}</div>
            </div>

            {/* Marks Sheet Table Preview */}
            <div style={{ maxHeight: '320px', overflowY: 'auto' }}>
              <table className="marks-table">
                <thead>
                  <tr>
                    <th>Roll No</th>
                    <th>Student Name</th>
                    <th>Status</th>
                    <th>Marks Obtained</th>
                    <th>Max</th>
                    <th>Pass</th>
                    <th>Remarks</th>
                  </tr>
                </thead>
                <tbody>
                  {subjectMarksQuery.data?.marks.map(m => (
                    <tr key={m.studentId}>
                      <td>{m.rollNo || '—'}</td>
                      <td><strong>{m.studentName}</strong></td>
                      <td><StatusBadge status={m.status} /></td>
                      <td><strong>{m.marksObtained !== null ? m.marksObtained : '—'}</strong></td>
                      <td>{m.maxMarks}</td>
                      <td>{m.passMarks}</td>
                      <td>{m.teacherRemarks || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {canReview && (
              <div style={{ background: 'var(--surface-muted)', padding: '1rem', borderRadius: '8px' }}>
                <FormField label="Review Decision" required>
                  <div style={{ display: 'flex', gap: '1.5rem', marginTop: '0.25rem' }}>
                    <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer' }}>
                      <input
                        type="radio"
                        name="reviewAction"
                        checked={reviewAction === 'APPROVE'}
                        onChange={() => setReviewAction('APPROVE')}
                      />
                      <strong style={{ color: '#15803d' }}>Approve Submission</strong>
                    </label>

                    <label style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', cursor: 'pointer' }}>
                      <input
                        type="radio"
                        name="reviewAction"
                        checked={reviewAction === 'REJECT'}
                        onChange={() => setReviewAction('REJECT')}
                      />
                      <strong style={{ color: '#b91c1c' }}>Reject & Return for Correction</strong>
                    </label>
                  </div>
                </FormField>

                <FormField label={reviewAction === 'REJECT' ? 'Reason for Rejection *' : 'Reviewer Comments (Optional)'}>
                  <Textarea
                    value={reviewComments}
                    onChange={e => setReviewComments(e.target.value)}
                    placeholder={reviewAction === 'REJECT' ? 'Specify required corrections for the teacher...' : 'Optional approval notes'}
                  />
                </FormField>

                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1rem' }}>
                  <Button variant="secondary" onClick={() => setSelectedSubmission(null)}>Cancel</Button>
                  <Button
                    variant={reviewAction === 'APPROVE' ? 'primary' : 'danger'}
                    disabled={reviewMutation.isPending}
                    onClick={() => reviewMutation.mutate()}
                  >
                    {reviewMutation.isPending ? 'Processing…' : `Confirm ${reviewAction === 'APPROVE' ? 'Approval' : 'Rejection'}`}
                  </Button>
                </div>
              </div>
            )}
          </div>
        </Modal>
      )}
    </div>
  );
}

// =============================================================================
// 6. PUBLISHED RESULTS & CONTROLLED CORRECTIONS VIEW
// =============================================================================

export function PublishedResultsView({ onSelectStudentReportCard }: { onSelectStudentReportCard?: (examId: number, studentId: number) => void }) {
  const queryClient = useQueryClient();
  const { hasPermission } = useAuth();
  const canPublish = hasPermission('RESULT:PUBLISH');
  const canApprove = hasPermission('RESULT:APPROVE');
  const canCorrect = hasPermission('RESULT:UPDATE') || hasPermission('RESULT:PUBLISH');

  const refs = useExamAcademicRefs();
  const examsQuery = useQuery({ queryKey: ['exams-list'], queryFn: () => examApi.listExams() });

  const [selectedExamId, setSelectedExamId] = useState<string>('');
  const [selectedSectionId, setSelectedSectionId] = useState<string>('');
  const [feedback, setFeedback] = useState<{ tone: 'success' | 'danger'; message: string } | null>(null);

  // Correction Modal
  const [correctionTarget, setCorrectionTarget] = useState<{ examId: number; studentId: number; studentName: string; subjectId: number; subjectName: string; maxMarks: number; currentMarks: number | null; currentStatus: string } | null>(null);
  const [newMarks, setNewMarks] = useState('');
  const [newStatus, setNewStatus] = useState('PRESENT');
  const [correctionReason, setCorrectionReason] = useState('');

  // Audit History Modal
  const [showHistory, setShowHistory] = useState(false);

  const selectedExam = examsQuery.data?.find(e => String(e.id) === selectedExamId);
  const applicableSections = refs.sections.filter(s => selectedExam && s.classId === selectedExam.classId);

  const resultsQuery = useQuery({
    queryKey: ['calculated-results', selectedExamId, selectedSectionId],
    queryFn: () => selectedExamId ? examApi.getResults(Number(selectedExamId), { sectionId: selectedSectionId ? Number(selectedSectionId) : undefined }) : Promise.resolve([]),
    enabled: Boolean(selectedExamId)
  });

  const correctionsQuery = useQuery({
    queryKey: ['audit-corrections', selectedExamId],
    queryFn: () => selectedExamId ? examApi.listCorrections(Number(selectedExamId)) : Promise.resolve([]),
    enabled: Boolean(selectedExamId && showHistory)
  });

  const calculateMutation = useMutation({
    mutationFn: () => examApi.calculateResults(Number(selectedExamId)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calculated-results', selectedExamId] });
      setFeedback({ tone: 'success', message: 'Examination results calculated successfully.' });
    },
    onError: (err: any) => setFeedback({ tone: 'danger', message: err.message || 'Calculation failed.' })
  });

  const publishMutation = useMutation({
    mutationFn: () => examApi.publishResults(Number(selectedExamId)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calculated-results', selectedExamId] });
      queryClient.invalidateQueries({ queryKey: ['exams-list'] });
      queryClient.invalidateQueries({ queryKey: ['exam-dashboard'] });
      setFeedback({ tone: 'success', message: 'Results published successfully! Now visible to students and parents.' });
    },
    onError: (err: any) => setFeedback({ tone: 'danger', message: err.message || 'Publication failed.' })
  });

  const unpublishMutation = useMutation({
    mutationFn: () => examApi.unpublishResults(Number(selectedExamId)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calculated-results', selectedExamId] });
      queryClient.invalidateQueries({ queryKey: ['exams-list'] });
      queryClient.invalidateQueries({ queryKey: ['exam-dashboard'] });
      setFeedback({ tone: 'success', message: 'Results unpublished. They are now hidden from students and parents.' });
    },
    onError: (err: any) => setFeedback({ tone: 'danger', message: err.message || 'Unpublication failed.' })
  });

  const correctMutation = useMutation({
    mutationFn: () => {
      if (!correctionReason.trim()) {
        throw new Error('An audit reason is required for correcting published academic marks.');
      }
      return examApi.correctResult(correctionTarget!.examId, {
        studentId: correctionTarget!.studentId,
        subjectId: correctionTarget!.subjectId,
        marksObtained: newStatus === 'PRESENT' ? Number(newMarks) : null,
        status: newStatus,
        reason: correctionReason.trim()
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['calculated-results', selectedExamId] });
      setCorrectionTarget(null);
      setFeedback({ tone: 'success', message: 'Mark corrected and audit history updated.' });
    },
    onError: (err: any) => setFeedback({ tone: 'danger', message: err.message || 'Correction failed.' })
  });

  const openCorrectionModal = (res: StudentResult, sub: any) => {
    setCorrectionTarget({
      examId: res.examId,
      studentId: res.studentId,
      studentName: res.studentName,
      subjectId: sub.subjectId,
      subjectName: sub.subjectName,
      maxMarks: sub.maxMarks,
      currentMarks: sub.marksObtained,
      currentStatus: sub.status
    });
    setNewMarks(sub.marksObtained !== null ? String(sub.marksObtained) : '');
    setNewStatus(sub.status || 'PRESENT');
    setCorrectionReason('');
  };

  return (
    <div className="exam-page-container">
      {feedback && <Alert tone={feedback.tone}>{feedback.message}</Alert>}

      <div className="exam-filter-bar">
        <div style={{ minWidth: 320 }}>
          <FormField label="Choose Examination">
            <Select value={selectedExamId} onChange={e => { setSelectedExamId(e.target.value); setSelectedSectionId(''); setFeedback(null); }}>
              <option value="">Select examination</option>
              {examsQuery.data?.map(ex => (
                <option key={ex.id} value={ex.id}>
                  {ex.name} — {ex.className} [{ex.status}]
                </option>
              ))}
            </Select>
          </FormField>
        </div>

        {selectedExam && (
          <div style={{ minWidth: 200 }}>
            <FormField label="Filter Section">
              <Select value={selectedSectionId} onChange={e => setSelectedSectionId(e.target.value)}>
                <option value="">All Sections</option>
                {applicableSections.map(s => (
                  <option key={s.id} value={s.id}>{s.name}</option>
                ))}
              </Select>
            </FormField>
          </div>
        )}

        {selectedExam && (
          <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'flex-end' }}>
            {canApprove && (
              <Button
                variant="secondary"
                disabled={calculateMutation.isPending}
                onClick={() => calculateMutation.mutate()}
              >
                {calculateMutation.isPending ? 'Calculating…' : 'Recalculate Results'}
              </Button>
            )}

            {canPublish && selectedExam.status !== 'PUBLISHED' && (
              <Button
                variant="primary"
                disabled={publishMutation.isPending}
                onClick={() => {
                  if (window.confirm('Are you sure you want to publish results? This will make report cards immediately visible to all authorized students and parents.')) {
                    publishMutation.mutate();
                  }
                }}
              >
                {publishMutation.isPending ? 'Publishing…' : 'Publish Results'}
              </Button>
            )}

            {canPublish && selectedExam.status === 'PUBLISHED' && (
              <Button
                variant="danger"
                disabled={unpublishMutation.isPending}
                onClick={() => {
                  if (window.confirm('Are you sure you want to unpublish results? Students and parents will lose access to report cards until republished.')) {
                    unpublishMutation.mutate();
                  }
                }}
              >
                {unpublishMutation.isPending ? 'Unpublishing…' : 'Unpublish Results'}
              </Button>
            )}

            {canCorrect && (
              <Button variant="ghost" onClick={() => setShowHistory(true)}>
                Audit History 📜
              </Button>
            )}
          </div>
        )}
      </div>

      {selectedExam && (
        <Card title={`Student Results: ${selectedExam.name}`}>
          <div style={{ marginBottom: '1rem', display: 'flex', gap: '1.5rem', alignItems: 'center' }}>
            <span><strong>Status:</strong> <StatusBadge status={selectedExam.status} /></span>
            <span><strong>Total Students Evaluated:</strong> {resultsQuery.data?.length ?? 0}</span>
          </div>

          {resultsQuery.isPending ? (
            <div>Loading calculated results…</div>
          ) : resultsQuery.data && resultsQuery.data.length > 0 ? (
            <div className="exam-table-wrap">
              <table className="marks-table">
                <thead>
                  <tr>
                    <th>Rank</th>
                    <th>Roll No</th>
                    <th>Student Name</th>
                    <th>Total Obtained</th>
                    <th>Percentage</th>
                    <th>Grade</th>
                    <th>Result</th>
                    <th>Subject Breakdown</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {resultsQuery.data.map(r => (
                    <tr key={r.studentId}>
                      <td><strong>#{r.rankInSection ?? r.rankInClass ?? '—'}</strong></td>
                      <td>{r.rollNo || '—'}</td>
                      <td><strong>{r.studentName}</strong> <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{r.admissionNo}</div></td>
                      <td>{r.totalMarksObtained} / {r.totalMaxMarks}</td>
                      <td><strong>{r.percentage}%</strong></td>
                      <td><span style={{ fontWeight: 750, color: r.overallGrade === 'A+' || r.overallGrade === 'A' ? '#16a34a' : r.overallGrade === 'F' ? '#dc2626' : '#2563eb' }}>{r.overallGrade}</span></td>
                      <td><StatusBadge status={r.resultStatus} /></td>
                      <td>
                        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.35rem' }}>
                          {r.subjects.map(s => (
                            <span
                              key={s.subjectId}
                              style={{
                                padding: '2px 6px',
                                borderRadius: '4px',
                                fontSize: '0.75rem',
                                background: s.isPass ? '#dcfce7' : '#fee2e2',
                                color: s.isPass ? '#15803d' : '#b91c1c',
                                cursor: canCorrect ? 'pointer' : 'default'
                              }}
                              title={canCorrect ? `Click to correct ${s.subjectName}` : undefined}
                              onClick={() => canCorrect && openCorrectionModal(r, s)}
                            >
                              {s.subjectCode}: {s.marksObtained !== null ? s.marksObtained : s.status} {canCorrect && '✎'}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td>
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={() => onSelectStudentReportCard?.(r.examId, r.studentId)}
                        >
                          Report Card →
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState title="No results available" description="Ensure teacher submissions are approved, then click 'Recalculate Results'." />
          )}
        </Card>
      )}

      {/* Controlled Correction Modal */}
      {correctionTarget && (
        <Modal
          open={Boolean(correctionTarget)}
          onClose={() => setCorrectionTarget(null)}
          title={`Audited Result Correction: ${correctionTarget.studentName} (${correctionTarget.subjectName})`}
        >
          <form onSubmit={e => { e.preventDefault(); correctMutation.mutate(); }}>
            <div style={{ marginBottom: '1rem', background: 'var(--surface-muted)', padding: '0.75rem', borderRadius: '6px' }}>
              <div><strong>Current Mark:</strong> {correctionTarget.currentMarks !== null ? correctionTarget.currentMarks : 'None'} (Status: {correctionTarget.currentStatus})</div>
              <div><strong>Max Marks:</strong> {correctionTarget.maxMarks}</div>
            </div>

            <FormField label="Status" required>
              <Select value={newStatus} onChange={e => setNewStatus(e.target.value)}>
                <option value="PRESENT">PRESENT</option>
                <option value="ABSENT">ABSENT</option>
                <option value="EXEMPT">EXEMPT</option>
              </Select>
            </FormField>

            {newStatus === 'PRESENT' && (
              <FormField label="New Marks" required>
                <Input
                  type="number"
                  step="0.5"
                  min="0"
                  max={correctionTarget.maxMarks}
                  value={newMarks}
                  onChange={e => setNewMarks(e.target.value)}
                  required
                />
              </FormField>
            )}

            <FormField label="Audit Reason / Justification *" required>
              <Textarea
                value={correctionReason}
                onChange={e => setCorrectionReason(e.target.value)}
                placeholder="e.g. Scrutiny committee re-evaluation found Question 3 was incorrectly totaled."
                required
              />
            </FormField>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1.25rem' }}>
              <Button type="button" variant="secondary" onClick={() => setCorrectionTarget(null)}>Cancel</Button>
              <Button variant="danger" disabled={correctMutation.isPending}>
                {correctMutation.isPending ? 'Saving Correction…' : 'Confirm Audited Correction'}
              </Button>
            </div>
          </form>
        </Modal>
      )}

      {/* Audit History Modal */}
      {showHistory && (
        <Modal open={showHistory} onClose={() => setShowHistory(false)} title="Audit Log: Result Corrections">
          <div style={{ maxHeight: '400px', overflowY: 'auto' }}>
            {correctionsQuery.data && correctionsQuery.data.length > 0 ? (
              <table className="marks-table">
                <thead>
                  <tr>
                    <th>Date & Time</th>
                    <th>Student</th>
                    <th>Subject</th>
                    <th>Previous</th>
                    <th>New</th>
                    <th>Reason</th>
                    <th>Corrected By</th>
                  </tr>
                </thead>
                <tbody>
                  {correctionsQuery.data.map(c => (
                    <tr key={c.id}>
                      <td>{new Date(c.correctedAt).toLocaleString()}</td>
                      <td><strong>{c.studentName}</strong></td>
                      <td>{c.subjectName}</td>
                      <td>{c.previousMarks ?? c.previousStatus}</td>
                      <td><strong>{c.newMarks ?? c.newStatus}</strong></td>
                      <td>{c.reason}</td>
                      <td>{c.correctedByName}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <EmptyState title="No corrections recorded" description="No academic result corrections have been made for this examination." />
            )}
          </div>
        </Modal>
      )}
    </div>
  );
}

// =============================================================================
// 7. REPORT CARD VIEW (PRINTABLE & EXPORTABLE)
// =============================================================================

export function ReportCardView({ initialExamId, initialStudentId }: { initialExamId?: number; initialStudentId?: number }) {
  const examsQuery = useQuery({ queryKey: ['exams-list'], queryFn: () => examApi.listExams() });

  const [examId, setExamId] = useState<string>(initialExamId ? String(initialExamId) : '');
  const [studentId, setStudentId] = useState<string>(initialStudentId ? String(initialStudentId) : '');

  // Students enrolled in the selected exam
  const resultsQuery = useQuery({
    queryKey: ['calculated-results', examId],
    queryFn: () => examId ? examApi.getResults(Number(examId)) : Promise.resolve([]),
    enabled: Boolean(examId)
  });

  // Set initial student when results load
  useEffect(() => {
    if (!studentId && resultsQuery.data?.[0]) {
      setStudentId(String(resultsQuery.data[0].studentId));
    }
  }, [resultsQuery.data, studentId]);

  const reportCardQuery = useQuery({
    queryKey: ['report-card', examId, studentId],
    queryFn: () => (examId && studentId) ? examApi.getReportCard(Number(examId), Number(studentId)) : Promise.resolve(null),
    enabled: Boolean(examId && studentId)
  });

  const card = reportCardQuery.data;

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="exam-page-container">
      <div className="exam-filter-bar report-card-actions">
        <div className="exam-filter-group">
          <FormField label="Examination">
            <Select value={examId} onChange={e => { setExamId(e.target.value); setStudentId(''); }}>
              <option value="">Select examination</option>
              {examsQuery.data?.map(ex => (
                <option key={ex.id} value={ex.id}>{ex.name} ({ex.className})</option>
              ))}
            </Select>
          </FormField>

          <FormField label="Student">
            <Select value={studentId} disabled={!examId} onChange={e => setStudentId(e.target.value)}>
              <option value="">Select student</option>
              {resultsQuery.data?.map(r => (
                <option key={r.studentId} value={r.studentId}>
                  {r.rollNo ? `[Roll ${r.rollNo}] ` : ''}{r.studentName} ({r.admissionNo})
                </option>
              ))}
            </Select>
          </FormField>
        </div>

        {card && (
          <Button variant="primary" onClick={handlePrint}>
            🖨 Print / Export PDF
          </Button>
        )}
      </div>

      {reportCardQuery.isPending ? (
        <Card>Loading report card…</Card>
      ) : card ? (
        <div className="report-card-container">
          <div className="report-card-header">
            <h1 className="report-card-school-name">{card.schoolName}</h1>
            <div className="report-card-school-address">{card.schoolAddress}</div>
            <div className="report-card-title">PROGRESS REPORT & ACADEMIC TRANSCRIPT</div>
            <div style={{ marginTop: '0.4rem', fontWeight: 600 }}>Academic Year: {card.academicYear}</div>
          </div>

          <div className="report-card-meta-grid">
            <div className="report-card-meta-item">
              <span className="report-card-meta-label">Student Name:</span>
              <span className="report-card-meta-value">{card.studentName}</span>
            </div>
            <div className="report-card-meta-item">
              <span className="report-card-meta-label">Admission No:</span>
              <span className="report-card-meta-value">{card.admissionNo}</span>
            </div>
            <div className="report-card-meta-item">
              <span className="report-card-meta-label">Class & Section:</span>
              <span className="report-card-meta-value">{card.className} {card.sectionName ? `(${card.sectionName})` : ''}</span>
            </div>
            <div className="report-card-meta-item">
              <span className="report-card-meta-label">Roll Number:</span>
              <span className="report-card-meta-value">{card.rollNo || '—'}</span>
            </div>
            <div className="report-card-meta-item">
              <span className="report-card-meta-label">Examination:</span>
              <span className="report-card-meta-value">{card.examinationName}</span>
            </div>
            <div className="report-card-meta-item">
              <span className="report-card-meta-label">Attendance:</span>
              <span className="report-card-meta-value">{card.attendancePercentage !== null ? `${card.attendancePercentage}%` : '100%'}</span>
            </div>
          </div>

          <table className="report-card-table">
            <thead>
              <tr>
                <th>Subject</th>
                <th>Maximum Marks</th>
                <th>Pass Marks</th>
                <th>Marks Obtained</th>
                <th>Percentage</th>
                <th>Grade</th>
                <th>Remarks</th>
              </tr>
            </thead>
            <tbody>
              {card.subjects.map(s => (
                <tr key={s.subjectId}>
                  <td><strong>{s.subjectName}</strong> <span style={{ color: '#64748b' }}>({s.subjectCode})</span></td>
                  <td>{s.maxMarks}</td>
                  <td>{s.passMarks}</td>
                  <td><strong>{s.marksObtained !== null ? s.marksObtained : s.status}</strong></td>
                  <td>{s.percentage !== null ? `${s.percentage}%` : '—'}</td>
                  <td><strong>{s.grade || '—'}</strong></td>
                  <td>{s.teacherRemarks || (s.isPass ? 'Good' : 'Needs improvement')}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="report-card-summary-box">
            <div>
              <div className="report-card-summary-metric">{card.totalMarksObtained} / {card.totalMaxMarks}</div>
              <div className="report-card-summary-label">Total Marks</div>
            </div>
            <div>
              <div className="report-card-summary-metric">{card.percentage}%</div>
              <div className="report-card-summary-label">Overall Percentage</div>
            </div>
            <div>
              <div className="report-card-summary-metric">{card.overallGrade}</div>
              <div className="report-card-summary-label">Overall Grade</div>
            </div>
            <div>
              <div className="report-card-summary-metric" style={{ color: card.resultStatus === 'PASS' ? '#16a34a' : '#dc2626' }}>
                {card.resultStatus}
              </div>
              <div className="report-card-summary-label">Final Result</div>
            </div>
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem', color: '#64748b' }}>
            <span>Section Rank: <strong>#{card.rankInSection ?? '—'}</strong></span>
            <span>Class Rank: <strong>#{card.rankInClass ?? '—'}</strong></span>
            <span>Published Date: <strong>{card.publishedAt ? new Date(card.publishedAt).toLocaleDateString() : '—'}</strong></span>
          </div>

          <div className="report-card-signatures">
            <div className="report-card-signature-line">Class Teacher</div>
            <div className="report-card-signature-line">Examination Controller</div>
            <div className="report-card-signature-line">Principal</div>
          </div>
        </div>
      ) : (
        <EmptyState title="No report card selected" description="Select an examination and student to generate an official report card transcript." />
      )}
    </div>
  );
}

// =============================================================================
// 8. RESULT ANALYTICS VIEW
// =============================================================================

export function ResultAnalyticsView() {
  const examsQuery = useQuery({ queryKey: ['exams-list'], queryFn: () => examApi.listExams() });
  const [selectedExamId, setSelectedExamId] = useState<string>('');

  useEffect(() => {
    if (!selectedExamId && examsQuery.data?.[0]) {
      setSelectedExamId(String(examsQuery.data[0].id));
    }
  }, [examsQuery.data, selectedExamId]);

  const analyticsQuery = useQuery({
    queryKey: ['exam-analytics', selectedExamId],
    queryFn: () => selectedExamId ? examApi.getAnalytics(Number(selectedExamId)) : Promise.resolve(null),
    enabled: Boolean(selectedExamId)
  });

  const a = analyticsQuery.data;

  return (
    <div className="exam-page-container">
      <div className="exam-filter-bar">
        <div style={{ minWidth: 320 }}>
          <FormField label="Choose Examination">
            <Select value={selectedExamId} onChange={e => setSelectedExamId(e.target.value)}>
              <option value="">Select examination</option>
              {examsQuery.data?.map(ex => (
                <option key={ex.id} value={ex.id}>{ex.name} ({ex.className})</option>
              ))}
            </Select>
          </FormField>
        </div>
      </div>

      {analyticsQuery.isPending ? (
        <Card>Loading examination analytics…</Card>
      ) : a ? (
        <>
          {/* KPI Cards */}
          <div className="exam-kpi-grid">
            <div className="exam-kpi-card">
              <span className="exam-kpi-title">Pass Percentage</span>
              <div className="exam-kpi-value" style={{ color: a.passPercentage >= 75 ? '#16a34a' : a.passPercentage >= 50 ? '#2563eb' : '#dc2626' }}>
                {a.passPercentage}%
              </div>
              <span className="exam-kpi-subtext">{a.passedStudents} passed out of {a.appearedStudents} appeared</span>
            </div>

            <div className="exam-kpi-card">
              <span className="exam-kpi-title">Average Percentage</span>
              <div className="exam-kpi-value">{a.averagePercentage}%</div>
              <span className="exam-kpi-subtext">Class cohort average</span>
            </div>

            <div className="exam-kpi-card">
              <span className="exam-kpi-title">Highest Score</span>
              <div className="exam-kpi-value">{a.highestPercentage}%</div>
              <span className="exam-kpi-subtext">Lowest: {a.lowestPercentage}%</span>
            </div>

            <div className="exam-kpi-card">
              <span className="exam-kpi-title">Students Evaluated</span>
              <div className="exam-kpi-value">{a.totalStudents}</div>
              <span className="exam-kpi-subtext">{a.totalStudents - a.appearedStudents} absent / incomplete</span>
            </div>
          </div>

          {/* Grade Distribution */}
          <Card title="Grade Distribution">
            <div className="grade-bar-container">
              {a.gradeDistribution.map(g => (
                <div
                  key={g.grade}
                  className={`grade-bar-segment grade-${g.grade.replace('+', '_plus')}`}
                  style={{ width: `${g.percentage}%` }}
                  title={`${g.grade}: ${g.count} students (${g.percentage}%)`}
                >
                  {g.percentage > 5 ? `${g.grade} (${g.percentage}%)` : ''}
                </div>
              ))}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(100px, 1fr))', gap: '0.75rem', marginTop: '1rem' }}>
              {a.gradeDistribution.map(g => (
                <div key={g.grade} style={{ textAlign: 'center', padding: '0.5rem', background: 'var(--surface-muted)', borderRadius: '6px' }}>
                  <div style={{ fontWeight: 750, fontSize: '1.1rem' }}>{g.grade}</div>
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{g.count} students ({g.percentage}%)</div>
                </div>
              ))}
            </div>
          </Card>

          {/* Subject Performance */}
          <Card title="Subject Performance Breakdown">
            <table className="marks-table">
              <thead>
                <tr>
                  <th>Subject</th>
                  <th>Average Marks</th>
                  <th>Highest</th>
                  <th>Lowest</th>
                  <th>Pass Percentage</th>
                </tr>
              </thead>
              <tbody>
                {a.subjectPerformances.map(s => (
                  <tr key={s.subjectId}>
                    <td><strong>{s.subjectName}</strong></td>
                    <td>{s.averageMarks}</td>
                    <td><span style={{ color: '#16a34a', fontWeight: 650 }}>{s.highestMarks}</span></td>
                    <td><span style={{ color: '#dc2626', fontWeight: 650 }}>{s.lowestMarks}</span></td>
                    <td><strong>{s.passPercentage}%</strong></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </Card>

          {/* Attention Required Students */}
          {a.studentsNeedingAttention.length > 0 && (
            <Card title="Students Requiring Academic Attention">
              <table className="marks-table">
                <thead>
                  <tr>
                    <th>Student Name</th>
                    <th>Admission No</th>
                    <th>Overall Percentage</th>
                    <th>Result Status</th>
                    <th>Failed Subjects</th>
                  </tr>
                </thead>
                <tbody>
                  {a.studentsNeedingAttention.map(s => (
                    <tr key={s.studentId}>
                      <td><strong>{s.studentName}</strong></td>
                      <td>{s.admissionNo}</td>
                      <td><span style={{ color: '#dc2626', fontWeight: 700 }}>{s.percentage}%</span></td>
                      <td><StatusBadge status={s.resultStatus} /></td>
                      <td><span style={{ color: '#b91c1c', fontWeight: 650 }}>{s.failedSubjectCount} subject(s) failed</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </Card>
          )}
        </>
      ) : (
        <EmptyState title="No analytics data available" description="Select an examination with published or calculated results." />
      )}
    </div>
  );
}

// =============================================================================
// 9. STUDENT PORTAL EXAM VIEW (SCOPED READ-ONLY)
// =============================================================================

export function StudentExamViewComponent() {
  const examsQuery = useQuery({ queryKey: ['exams-list'], queryFn: () => examApi.listExams({ status: 'PUBLISHED' }) });
  const [selectedExamId, setSelectedExamId] = useState<string>('');
  const [activeReportCard, setActiveReportCard] = useState<ReportCard | null>(null);
  const [reportCardError, setReportCardError] = useState('');

  useEffect(() => {
    if (!selectedExamId && examsQuery.data?.[0]) {
      setSelectedExamId(String(examsQuery.data[0].id));
    }
  }, [examsQuery.data, selectedExamId]);

  const resultsQuery = useQuery({
    queryKey: ['student-own-results', selectedExamId],
    queryFn: () => selectedExamId ? examApi.getResults(Number(selectedExamId)) : Promise.resolve([]),
    enabled: Boolean(selectedExamId)
  });

  const viewReportCard = async (examId: number, studentId: number) => {
    setReportCardError('');
    try {
      const card = await examApi.getReportCard(examId, studentId);
      setActiveReportCard(card);
    } catch (err: unknown) {
      setReportCardError(err instanceof Error ? err.message : 'Report card is not published yet.');
    }
  };

  return (
    <div className="exam-page-container">
      <Card title="Published Examination Results">
        {reportCardError && <div style={{ marginBottom: '1rem' }}><Alert tone="warning">{reportCardError}</Alert></div>}
        <div style={{ marginBottom: '1rem', maxWidth: 360 }}>
          <FormField label="Choose Examination">
            <Select value={selectedExamId} onChange={e => { setSelectedExamId(e.target.value); setActiveReportCard(null); }}>
              <option value="">Select examination</option>
              {examsQuery.data?.map(ex => (
                <option key={ex.id} value={ex.id}>{ex.name} ({ex.sessionCode})</option>
              ))}
            </Select>
          </FormField>
        </div>

        {resultsQuery.isPending ? (
          <div>Loading your results…</div>
        ) : resultsQuery.data && resultsQuery.data.length > 0 ? (
          <div>
            {resultsQuery.data.map(res => (
              <div key={res.examId} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem', background: 'var(--surface-muted)', padding: '1rem', borderRadius: '8px' }}>
                  <div>
                    <h3 style={{ margin: '0 0 0.25rem 0' }}>{res.studentName}</h3>
                    <span style={{ color: 'var(--text-muted)' }}>Roll: {res.rollNo || '—'} | Admission: {res.admissionNo} | Class: {res.className}</span>
                  </div>
                  <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                    <div style={{ textAlign: 'right' }}>
                      <div style={{ fontSize: '1.4rem', fontWeight: 800 }}>{res.percentage}%</div>
                      <StatusBadge status={res.resultStatus} />
                    </div>
                    <Button variant="primary" onClick={() => viewReportCard(res.examId, res.studentId)}>
                      View Official Report Card 🖨
                    </Button>
                  </div>
                </div>

                {/* Subject Breakdown */}
                <table className="marks-table">
                  <thead>
                    <tr>
                      <th>Subject</th>
                      <th>Marks Obtained</th>
                      <th>Max Marks</th>
                      <th>Passing Marks</th>
                      <th>Grade</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(res.subjects ?? []).map(s => (
                      <tr key={s.subjectId}>
                        <td><strong>{s.subjectName}</strong> ({s.subjectCode})</td>
                        <td><strong>{s.marksObtained !== null ? s.marksObtained : s.status}</strong></td>
                        <td>{s.maxMarks}</td>
                        <td>{s.passMarks}</td>
                        <td><strong>{s.grade || '—'}</strong></td>
                        <td><StatusBadge status={s.isPass ? 'PASS' : 'FAIL'} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}
          </div>
        ) : (
          <EmptyState title="No published examination results" description="Official academic results will appear here once finalized and published by school administration." />
        )}
      </Card>

      {/* Modal Report Card View */}
      {activeReportCard && (
        <Modal open={Boolean(activeReportCard)} onClose={() => setActiveReportCard(null)} title="Official Report Card">
          <div style={{ maxHeight: '80vh', overflowY: 'auto' }}>
            <div className="report-card-container">
              <div className="report-card-header">
                <h1 className="report-card-school-name">{activeReportCard.schoolName}</h1>
                <div className="report-card-school-address">{activeReportCard.schoolAddress}</div>
                <div className="report-card-title">PROGRESS REPORT</div>
                <div style={{ marginTop: '0.4rem', fontWeight: 600 }}>Academic Year: {activeReportCard.academicYear}</div>
              </div>

              <div className="report-card-meta-grid">
                <div className="report-card-meta-item">
                  <span className="report-card-meta-label">Student Name:</span>
                  <span className="report-card-meta-value">{activeReportCard.studentName}</span>
                </div>
                <div className="report-card-meta-item">
                  <span className="report-card-meta-label">Admission No:</span>
                  <span className="report-card-meta-value">{activeReportCard.admissionNo}</span>
                </div>
                <div className="report-card-meta-item">
                  <span className="report-card-meta-label">Class & Section:</span>
                  <span className="report-card-meta-value">{activeReportCard.className}</span>
                </div>
                <div className="report-card-meta-item">
                  <span className="report-card-meta-label">Roll Number:</span>
                  <span className="report-card-meta-value">{activeReportCard.rollNo || '—'}</span>
                </div>
              </div>

              <table className="report-card-table">
                <thead>
                  <tr>
                    <th>Subject</th>
                    <th>Max</th>
                    <th>Pass</th>
                    <th>Obtained</th>
                    <th>Grade</th>
                  </tr>
                </thead>
                <tbody>
                  {activeReportCard.subjects.map(s => (
                    <tr key={s.subjectId}>
                      <td><strong>{s.subjectName}</strong></td>
                      <td>{s.maxMarks}</td>
                      <td>{s.passMarks}</td>
                      <td>{s.marksObtained !== null ? s.marksObtained : s.status}</td>
                      <td><strong>{s.grade || '—'}</strong></td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <div className="report-card-summary-box">
                <div>
                  <div className="report-card-summary-metric">{activeReportCard.totalMarksObtained} / {activeReportCard.totalMaxMarks}</div>
                  <div className="report-card-summary-label">Total Marks</div>
                </div>
                <div>
                  <div className="report-card-summary-metric">{activeReportCard.percentage}%</div>
                  <div className="report-card-summary-label">Percentage</div>
                </div>
                <div>
                  <div className="report-card-summary-metric">{activeReportCard.overallGrade}</div>
                  <div className="report-card-summary-label">Overall Grade</div>
                </div>
                <div>
                  <div className="report-card-summary-metric" style={{ color: activeReportCard.resultStatus === 'PASS' ? '#16a34a' : '#dc2626' }}>
                    {activeReportCard.resultStatus}
                  </div>
                  <div className="report-card-summary-label">Final Result</div>
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1.5rem', gap: '0.75rem' }}>
                <Button variant="secondary" onClick={() => setActiveReportCard(null)}>Close</Button>
                <Button variant="primary" onClick={() => window.print()}>🖨 Print Report Card</Button>
              </div>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}

// =============================================================================
// 10. PARENT PORTAL EXAM VIEW (SCOPED TO LINKED CHILDREN)
// =============================================================================

export function ParentExamViewComponent() {
  const childrenQuery = useQuery({ queryKey: ['parent-linked-children'], queryFn: () => studentApi.list({ size: 50 }).then(res => (res as any)?.content ?? res ?? []) });
  const [selectedStudentId, setSelectedStudentId] = useState<string>('');

  useEffect(() => {
    if (!selectedStudentId && childrenQuery.data?.[0]) {
      setSelectedStudentId(String(childrenQuery.data[0].id));
    }
  }, [childrenQuery.data, selectedStudentId]);

  const examsQuery = useQuery({ queryKey: ['exams-list'], queryFn: () => examApi.listExams({ status: 'PUBLISHED' }) });
  const [selectedExamId, setSelectedExamId] = useState<string>('');
  const [activeReportCard, setActiveReportCard] = useState<ReportCard | null>(null);
  const [reportCardError, setReportCardError] = useState('');

  useEffect(() => {
    if (!selectedExamId && examsQuery.data?.[0]) {
      setSelectedExamId(String(examsQuery.data[0].id));
    }
  }, [examsQuery.data, selectedExamId]);

  const resultsQuery = useQuery({
    queryKey: ['parent-child-results', selectedExamId, selectedStudentId],
    queryFn: () => (selectedExamId && selectedStudentId)
      ? examApi.getResults(Number(selectedExamId), { studentId: Number(selectedStudentId) })
      : Promise.resolve([]),
    enabled: Boolean(selectedExamId && selectedStudentId)
  });

  const viewReportCard = async (examId: number, studentId: number) => {
    setReportCardError('');
    try {
      const card = await examApi.getReportCard(examId, studentId);
      setActiveReportCard(card);
    } catch (err: unknown) {
      setReportCardError(err instanceof Error ? err.message : 'Report card is not published yet.');
    }
  };

  return (
    <div className="exam-page-container">
      <Card title="Published Examination Results for Linked Children">
        {reportCardError && <div style={{ marginBottom: '1rem' }}><Alert tone="warning">{reportCardError}</Alert></div>}
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: '1rem', marginBottom: '1.25rem' }}>
          <FormField label="1. Select Child">
            <Select value={selectedStudentId} onChange={e => { setSelectedStudentId(e.target.value); setActiveReportCard(null); }}>
              {childrenQuery.data?.map((c: any) => (
                <option key={c.id} value={c.id}>{c.firstName} {c.lastName} ({c.admissionNo})</option>
              ))}
            </Select>
          </FormField>

          <FormField label="2. Examination">
            <Select value={selectedExamId} onChange={e => { setSelectedExamId(e.target.value); setActiveReportCard(null); }}>
              <option value="">Select examination</option>
              {examsQuery.data?.map(ex => (
                <option key={ex.id} value={ex.id}>{ex.name} ({ex.sessionCode})</option>
              ))}
            </Select>
          </FormField>
        </div>

        {resultsQuery.isPending ? (
          <div>Loading student results…</div>
        ) : resultsQuery.data && resultsQuery.data.length > 0 ? (
          <div>
            {resultsQuery.data.map(res => (
              <div key={res.examId} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '0.75rem', background: 'var(--surface-muted)', padding: '1rem', borderRadius: '8px' }}>
                  <div>
                    <h3 style={{ margin: '0 0 0.25rem 0' }}>{res.studentName}</h3>
                    <span style={{ color: 'var(--text-muted)' }}>Roll: {res.rollNo || '—'} | Admission: {res.admissionNo} | Class: {res.className}</span>
                  </div>
                  <div style={{ display: 'flex', gap: '1rem', alignItems: 'center' }}>
                    <div style={{ textAlign: 'right' }}>
                      <div style={{ fontSize: '1.4rem', fontWeight: 800 }}>{res.percentage}%</div>
                      <StatusBadge status={res.resultStatus} />
                    </div>
                    <Button variant="primary" onClick={() => viewReportCard(res.examId, res.studentId)}>
                      View Official Report Card 🖨
                    </Button>
                  </div>
                </div>

                <table className="marks-table">
                  <thead>
                    <tr>
                      <th>Subject</th>
                      <th>Marks Obtained</th>
                      <th>Max Marks</th>
                      <th>Passing Marks</th>
                      <th>Grade</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {(res.subjects ?? []).map(s => (
                      <tr key={s.subjectId}>
                        <td><strong>{s.subjectName}</strong> ({s.subjectCode})</td>
                        <td><strong>{s.marksObtained !== null ? s.marksObtained : s.status}</strong></td>
                        <td>{s.maxMarks}</td>
                        <td>{s.passMarks}</td>
                        <td><strong>{s.grade || '—'}</strong></td>
                        <td><StatusBadge status={s.isPass ? 'PASS' : 'FAIL'} /></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}
          </div>
        ) : (
          <EmptyState title="No published examination results" description="Official examination results for your child will appear here once published." />
        )}
      </Card>

      {/* Modal Report Card View */}
      {activeReportCard && (
        <Modal open={Boolean(activeReportCard)} onClose={() => setActiveReportCard(null)} title="Official Report Card">
          <div style={{ maxHeight: '80vh', overflowY: 'auto' }}>
            <div className="report-card-container">
              <div className="report-card-header">
                <h1 className="report-card-school-name">{activeReportCard.schoolName}</h1>
                <div className="report-card-school-address">{activeReportCard.schoolAddress}</div>
                <div className="report-card-title">PROGRESS REPORT</div>
                <div style={{ marginTop: '0.4rem', fontWeight: 600 }}>Academic Year: {activeReportCard.academicYear}</div>
              </div>

              <div className="report-card-meta-grid">
                <div className="report-card-meta-item">
                  <span className="report-card-meta-label">Student Name:</span>
                  <span className="report-card-meta-value">{activeReportCard.studentName}</span>
                </div>
                <div className="report-card-meta-item">
                  <span className="report-card-meta-label">Admission No:</span>
                  <span className="report-card-meta-value">{activeReportCard.admissionNo}</span>
                </div>
                <div className="report-card-meta-item">
                  <span className="report-card-meta-label">Class & Section:</span>
                  <span className="report-card-meta-value">{activeReportCard.className}</span>
                </div>
                <div className="report-card-meta-item">
                  <span className="report-card-meta-label">Roll Number:</span>
                  <span className="report-card-meta-value">{activeReportCard.rollNo || '—'}</span>
                </div>
              </div>

              <table className="report-card-table">
                <thead>
                  <tr>
                    <th>Subject</th>
                    <th>Max</th>
                    <th>Pass</th>
                    <th>Obtained</th>
                    <th>Grade</th>
                  </tr>
                </thead>
                <tbody>
                  {activeReportCard.subjects.map(s => (
                    <tr key={s.subjectId}>
                      <td><strong>{s.subjectName}</strong></td>
                      <td>{s.maxMarks}</td>
                      <td>{s.passMarks}</td>
                      <td>{s.marksObtained !== null ? s.marksObtained : s.status}</td>
                      <td><strong>{s.grade || '—'}</strong></td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <div className="report-card-summary-box">
                <div>
                  <div className="report-card-summary-metric">{activeReportCard.totalMarksObtained} / {activeReportCard.totalMaxMarks}</div>
                  <div className="report-card-summary-label">Total Marks</div>
                </div>
                <div>
                  <div className="report-card-summary-metric">{activeReportCard.percentage}%</div>
                  <div className="report-card-summary-label">Percentage</div>
                </div>
                <div>
                  <div className="report-card-summary-metric">{activeReportCard.overallGrade}</div>
                  <div className="report-card-summary-label">Overall Grade</div>
                </div>
                <div>
                  <div className="report-card-summary-metric" style={{ color: activeReportCard.resultStatus === 'PASS' ? '#16a34a' : '#dc2626' }}>
                    {activeReportCard.resultStatus}
                  </div>
                  <div className="report-card-summary-label">Final Result</div>
                </div>
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1.5rem', gap: '0.75rem' }}>
                <Button variant="secondary" onClick={() => setActiveReportCard(null)}>Close</Button>
                <Button variant="primary" onClick={() => window.print()}>🖨 Print Report Card</Button>
              </div>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}
