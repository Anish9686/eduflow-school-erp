import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
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
  PageHeader,
  Select,
  StatusBadge,
  Textarea,
} from '../components';
import { useAuth } from '../auth/AuthProvider';
import {
  attendanceApi,
  type AttendanceSummary,
  type DateLock,
  type RosterStudent,
  type StaffAttendance,
  type StudentAttendance,
} from '../api/attendance';
import './attendance-pages.css';

type AttendanceTab = 'mark' | 'records' | 'summary' | 'staff' | 'locks';

function todayIso(): string {
  const d = new Date();
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function AttendancePage() {
  const { hasPermission } = useAuth();
  const canMarkStudent = hasPermission('ATTENDANCE:MARK') || hasPermission('ATTENDANCE:MANAGE');
  const canReadStudent = hasPermission('ATTENDANCE:READ') || canMarkStudent;
  const canManageLocks = hasPermission('ATTENDANCE:MANAGE') || hasPermission('STAFF_ATTENDANCE:MANAGE');
  const canAccessStaff =
    hasPermission('STAFF_ATTENDANCE:READ') ||
    hasPermission('STAFF_ATTENDANCE:MARK') ||
    hasPermission('STAFF_ATTENDANCE:MANAGE');

  const defaultTab: AttendanceTab = canMarkStudent
    ? 'mark'
    : canReadStudent
      ? 'records'
      : canAccessStaff
        ? 'staff'
        : 'summary';

  const [tab, setTab] = useState<AttendanceTab>(defaultTab);

  return (
    <section className="workspace-page attendance-page">
      <PageHeader
        eyebrow="ATTENDANCE MANAGEMENT"
        title="Attendance workspace"
        description="Mark and track student and staff daily attendance, handle audits, and manage corrections."
      />

      <nav className="attendance-tabs" aria-label="Attendance navigation">
        {canMarkStudent && (
          <button
            type="button"
            className={`attendance-tab-btn ${tab === 'mark' ? 'is-active' : ''}`}
            onClick={() => setTab('mark')}
          >
            <span>✎</span> Mark Attendance
          </button>
        )}
        {canReadStudent && (
          <button
            type="button"
            className={`attendance-tab-btn ${tab === 'records' ? 'is-active' : ''}`}
            onClick={() => setTab('records')}
          >
            <span>▤</span> Attendance Records
          </button>
        )}
        {canReadStudent && (
          <button
            type="button"
            className={`attendance-tab-btn ${tab === 'summary' ? 'is-active' : ''}`}
            onClick={() => setTab('summary')}
          >
            <span>▥</span> Attendance Summary
          </button>
        )}
        {canAccessStaff && (
          <button
            type="button"
            className={`attendance-tab-btn ${tab === 'staff' ? 'is-active' : ''}`}
            onClick={() => setTab('staff')}
          >
            <span>👥</span> Staff Attendance
          </button>
        )}
        {canManageLocks && (
          <button
            type="button"
            className={`attendance-tab-btn ${tab === 'locks' ? 'is-active' : ''}`}
            onClick={() => setTab('locks')}
          >
            <span>🔒</span> Date Locks
          </button>
        )}
      </nav>

      {tab === 'mark' && <MarkAttendanceView />}
      {tab === 'records' && <AttendanceRecordsView />}
      {tab === 'summary' && <AttendanceSummaryView />}
      {tab === 'staff' && <StaffAttendanceView />}
      {tab === 'locks' && <DateLocksView />}
    </section>
  );
}

// ========================================================
// 1. MARK ATTENDANCE VIEW (Efficient Marking UI)
// ========================================================
function MarkAttendanceView() {
  const queryClient = useQueryClient();
  const [sessionId, setSessionId] = useState('');
  const [classId, setClassId] = useState('');
  const [sectionId, setSectionId] = useState('');
  const [attendanceDate, setAttendanceDate] = useState(todayIso());
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);

  const sessionsQuery = useQuery({ queryKey: ['attendance-sessions'], queryFn: attendanceApi.sessionOptions });
  const classesQuery = useQuery({ queryKey: ['attendance-classes'], queryFn: attendanceApi.classOptions });
  const sectionsQuery = useQuery({
    queryKey: ['attendance-sections', classId],
    queryFn: () => attendanceApi.sectionOptions(classId ? Number(classId) : undefined),
    enabled: Boolean(classId),
  });

  // Set default session if available
  useEffect(() => {
    if (sessionsQuery.data && sessionsQuery.data.length > 0 && !sessionId) {
      setSessionId(String(sessionsQuery.data[0]!.id));
    }
  }, [sessionsQuery.data, sessionId]);

  const canQueryRoster = Boolean(sessionId && classId && sectionId && attendanceDate);

  const rosterQuery = useQuery({
    queryKey: ['attendance-roster', sessionId, classId, sectionId, attendanceDate],
    queryFn: () =>
      attendanceApi.roster({
        sessionId: Number(sessionId),
        classId: Number(classId),
        sectionId: Number(sectionId),
        attendanceDate,
      }),
    enabled: canQueryRoster,
  });

  // Local state map for in-progress student attendance entries: studentId -> { status, remarks }
  const [markingMap, setMarkingMap] = useState<Record<number, { status: string; remarks: string }>>({});

  // Sync roster when loaded
  useEffect(() => {
    if (rosterQuery.data) {
      const initial: Record<number, { status: string; remarks: string }> = {};
      rosterQuery.data.forEach((student) => {
        initial[student.studentId] = {
          status: student.status ?? 'PRESENT', // default to PRESENT for swift marking
          remarks: student.remarks ?? '',
        };
      });
      setMarkingMap(initial);
      setMessage(null);
    }
  }, [rosterQuery.data]);

  const updateStatus = (studentId: number, status: string) => {
    setMarkingMap((prev) => {
      const cur = prev[studentId] ?? { status: 'PRESENT', remarks: '' };
      return {
        ...prev,
        [studentId]: { ...cur, status },
      };
    });
  };

  const updateRemarks = (studentId: number, remarks: string) => {
    setMarkingMap((prev) => {
      const cur = prev[studentId] ?? { status: 'PRESENT', remarks: '' };
      return {
        ...prev,
        [studentId]: { ...cur, remarks },
      };
    });
  };

  const markAll = (status: string) => {
    setMarkingMap((prev) => {
      const next: Record<number, { status: string; remarks: string }> = {};
      Object.keys(prev).forEach((idStr) => {
        const id = Number(idStr);
        const cur = prev[id] ?? { status: 'PRESENT', remarks: '' };
        next[id] = { ...cur, status };
      });
      return next;
    });
  };

  // Real-time live counts of currently chosen statuses
  const counts = useMemo(() => {
    const list = Object.values(markingMap);
    return {
      total: list.length,
      present: list.filter((x) => x.status === 'PRESENT').length,
      absent: list.filter((x) => x.status === 'ABSENT').length,
      late: list.filter((x) => x.status === 'LATE').length,
      halfDay: list.filter((x) => x.status === 'HALF_DAY').length,
      excused: list.filter((x) => x.status === 'EXCUSED').length,
      holiday: list.filter((x) => x.status === 'HOLIDAY').length,
    };
  }, [markingMap]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!rosterQuery.data?.length) return null;
      const entries = rosterQuery.data.map((s) => ({
        studentId: s.studentId,
        status: (markingMap[s.studentId]?.status || 'PRESENT') as
          | 'PRESENT'
          | 'ABSENT'
          | 'LATE'
          | 'HALF_DAY'
          | 'HOLIDAY'
          | 'EXCUSED',
        remarks: markingMap[s.studentId]?.remarks || undefined,
      }));
      // Stable idempotency key based on scope and date so fast double clicks are safely idempotent
      const key = `mark-s${sessionId}-c${classId}-sec${sectionId}-${attendanceDate}`;
      return attendanceApi.markStudents(
        {
          sessionId: Number(sessionId),
          classId: Number(classId),
          sectionId: Number(sectionId),
          attendanceDate,
          entries,
        },
        key
      );
    },
    onSuccess: (res) => {
      if (res) {
        setMessage({
          tone: 'success',
          text: `Attendance saved successfully! Recorded ${res.created} new record(s), ${res.alreadyMarked} already recorded.`,
        });
      }
      queryClient.invalidateQueries({ queryKey: ['attendance-roster'] });
      queryClient.invalidateQueries({ queryKey: ['attendance-records'] });
      queryClient.invalidateQueries({ queryKey: ['attendance-summary'] });
    },
    onError: (err: Error) => {
      setMessage({ tone: 'danger', text: err.message });
    },
  });

  const columns = [
    {
      key: 'admissionNo',
      header: 'Admission No',
      render: (r: RosterStudent) => <strong>{r.admissionNo}</strong>,
    },
    {
      key: 'studentName',
      header: 'Student Name',
      render: (r: RosterStudent) => (
        <div>
          <span>{r.studentName}</span>
          {r.corrected && <span className="badge-corrected">Corrected</span>}
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Attendance Status (Quick Select)',
      render: (r: RosterStudent) => {
        const cur = markingMap[r.studentId]?.status ?? 'PRESENT';
        return (
          <div className="status-toggle-group" role="group" aria-label={`Status for ${r.studentName}`}>
            <button
              type="button"
              className={`status-btn ${cur === 'PRESENT' ? 'active-present' : ''}`}
              onClick={() => updateStatus(r.studentId, 'PRESENT')}
              title="Present (P)"
            >
              P
            </button>
            <button
              type="button"
              className={`status-btn ${cur === 'ABSENT' ? 'active-absent' : ''}`}
              onClick={() => updateStatus(r.studentId, 'ABSENT')}
              title="Absent (A)"
            >
              A
            </button>
            <button
              type="button"
              className={`status-btn ${cur === 'LATE' ? 'active-late' : ''}`}
              onClick={() => updateStatus(r.studentId, 'LATE')}
              title="Late (L)"
            >
              L
            </button>
            <button
              type="button"
              className={`status-btn ${cur === 'HALF_DAY' ? 'active-half_day' : ''}`}
              onClick={() => updateStatus(r.studentId, 'HALF_DAY')}
              title="Half Day (HD)"
            >
              HD
            </button>
            <button
              type="button"
              className={`status-btn ${cur === 'EXCUSED' ? 'active-excused' : ''}`}
              onClick={() => updateStatus(r.studentId, 'EXCUSED')}
              title="Excused (EX)"
            >
              EX
            </button>
            <button
              type="button"
              className={`status-btn ${cur === 'HOLIDAY' ? 'active-holiday' : ''}`}
              onClick={() => updateStatus(r.studentId, 'HOLIDAY')}
              title="Holiday (H)"
            >
              H
            </button>
          </div>
        );
      },
    },
    {
      key: 'remarks',
      header: 'Remarks',
      render: (r: RosterStudent) => (
        <Input
          placeholder="Optional note..."
          value={markingMap[r.studentId]?.remarks ?? ''}
          onChange={(e) => updateRemarks(r.studentId, e.target.value)}
          style={{ minWidth: '160px', padding: '0.25rem 0.5rem', fontSize: '0.8125rem' }}
        />
      ),
    },
  ];

  return (
    <div style={{ display: 'grid', gap: '1.15rem' }}>
      <Card className="attendance-filter-card">
        <div className="attendance-filter-grid">
          <FormField label="Academic Session" required>
            <Select value={sessionId} onChange={(e) => setSessionId(e.target.value)}>
              <option value="">Select session</option>
              {sessionsQuery.data?.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </FormField>

          <FormField label="Class" required>
            <Select
              value={classId}
              onChange={(e) => {
                setClassId(e.target.value);
                setSectionId('');
              }}
            >
              <option value="">Select class</option>
              {classesQuery.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          </FormField>

          <FormField label="Section" required>
            <Select
              value={sectionId}
              onChange={(e) => setSectionId(e.target.value)}
              disabled={!classId || sectionsQuery.isPending}
            >
              <option value="">Select section</option>
              {sectionsQuery.data?.map((sec) => (
                <option key={sec.id} value={sec.id}>
                  {sec.name}
                </option>
              ))}
            </Select>
          </FormField>

          <FormField label="Attendance Date" required>
            <Input
              type="date"
              value={attendanceDate}
              onChange={(e) => setAttendanceDate(e.target.value)}
              max={todayIso()}
            />
          </FormField>
        </div>
      </Card>

      {message && <Alert tone={message.tone}>{message.text}</Alert>}

      {!canQueryRoster ? (
        <EmptyState
          title="Select attendance parameters"
          description="Choose an academic session, class, section, and date above to load the student roster."
        />
      ) : rosterQuery.isPending ? (
        <Card className="attendance-loading">Loading student roster…</Card>
      ) : rosterQuery.isError ? (
        <ErrorState
          title="Roster unavailable"
          description={(rosterQuery.error as Error).message}
          onRetry={() => void rosterQuery.refetch()}
        />
      ) : !rosterQuery.data?.length ? (
        <EmptyState
          title="No students found in section"
          description="There are no active enrolled students in this section for the selected session."
        />
      ) : (
        <>
          <div className="attendance-quick-actions">
            <div className="quick-btn-group">
              <button type="button" className="quick-action-btn" onClick={() => markAll('PRESENT')}>
                ✓ Mark All Present
              </button>
              <button type="button" className="quick-action-btn" onClick={() => markAll('ABSENT')}>
                ✗ Mark All Absent
              </button>
              <button type="button" className="quick-action-btn" onClick={() => markAll('HOLIDAY')}>
                🏖 Mark All Holiday
              </button>
            </div>

            <Button
              variant="primary"
              disabled={saveMutation.isPending}
              onClick={() => {
                setMessage(null);
                saveMutation.mutate();
              }}
            >
              {saveMutation.isPending ? 'Saving attendance…' : 'Submit Attendance'}
            </Button>
          </div>

          <div className="attendance-stats-bar">
            <div className="stat-pill">
              <span className="stat-pill-label">Total Roster</span>
              <span className="stat-pill-value">{counts.total}</span>
            </div>
            <div className="stat-pill pill-present">
              <span className="stat-pill-label">Present</span>
              <span className="stat-pill-value">{counts.present}</span>
            </div>
            <div className="stat-pill pill-absent">
              <span className="stat-pill-label">Absent</span>
              <span className="stat-pill-value">{counts.absent}</span>
            </div>
            <div className="stat-pill pill-late">
              <span className="stat-pill-label">Late</span>
              <span className="stat-pill-value">{counts.late}</span>
            </div>
            <div className="stat-pill pill-halfday">
              <span className="stat-pill-label">Half Day</span>
              <span className="stat-pill-value">{counts.halfDay}</span>
            </div>
            <div className="stat-pill pill-excused">
              <span className="stat-pill-label">Excused</span>
              <span className="stat-pill-value">{counts.excused}</span>
            </div>
          </div>

          <Card className="attendance-table-card">
            <DataTable
              columns={columns}
              rows={rosterQuery.data}
              rowKey={(r) => String(r.studentId)}
              caption="Student attendance roster"
            />
          </Card>
        </>
      )}
    </div>
  );
}

// ========================================================
// 2. ATTENDANCE RECORDS VIEW (Filters & Correction Trigger)
// ========================================================
function AttendanceRecordsView() {
  const { hasPermission } = useAuth();
  const canCorrect = hasPermission('ATTENDANCE:CORRECT') || hasPermission('ATTENDANCE:MANAGE');

  const [from, setFrom] = useState(todayIso());
  const [to, setTo] = useState(todayIso());
  const [sessionId, setSessionId] = useState('');
  const [classId, setClassId] = useState('');
  const [sectionId, setSectionId] = useState('');
  const [status, setStatus] = useState('');

  const [correctionTarget, setCorrectionTarget] = useState<StudentAttendance | null>(null);
  const [historyTargetId, setHistoryTargetId] = useState<number | null>(null);

  const sessionsQuery = useQuery({ queryKey: ['attendance-sessions'], queryFn: attendanceApi.sessionOptions });
  const classesQuery = useQuery({ queryKey: ['attendance-classes'], queryFn: attendanceApi.classOptions });
  const sectionsQuery = useQuery({
    queryKey: ['attendance-sections', classId],
    queryFn: () => attendanceApi.sectionOptions(classId ? Number(classId) : undefined),
    enabled: Boolean(classId),
  });

  const recordsQuery = useQuery({
    queryKey: ['attendance-records', from, to, sessionId, classId, sectionId, status],
    queryFn: () =>
      attendanceApi.studentRecords({
        from: from || undefined,
        to: to || undefined,
        sessionId: sessionId ? Number(sessionId) : undefined,
        classId: classId ? Number(classId) : undefined,
        sectionId: sectionId ? Number(sectionId) : undefined,
        status: status || undefined,
      }),
  });

  const statusBadge = (s: string) => {
    const cls = `badge-status badge-${s.toLowerCase()}`;
    return <span className={cls}>{s.replace('_', ' ')}</span>;
  };

  const columns = [
    {
      key: 'attendanceDate',
      header: 'Date',
      render: (r: StudentAttendance) => <span>{r.attendanceDate}</span>,
    },
    {
      key: 'admissionNo',
      header: 'Admission No',
      render: (r: StudentAttendance) => <strong>{r.admissionNo}</strong>,
    },
    {
      key: 'studentName',
      header: 'Student Name',
      render: (r: StudentAttendance) => <span>{r.studentName}</span>,
    },
    {
      key: 'classSection',
      header: 'Class & Section',
      render: (r: StudentAttendance) => (
        <span>
          {r.className} · {r.sectionName}
        </span>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      render: (r: StudentAttendance) => (
        <div style={{ display: 'inline-flex', alignItems: 'center' }}>
          {statusBadge(r.status)}
          {r.corrected && (
            <button
              type="button"
              className="badge-corrected"
              style={{ cursor: 'pointer' }}
              onClick={() => setHistoryTargetId(r.id)}
              title="Click to view correction audit trail"
            >
              Corrected ↗
            </button>
          )}
        </div>
      ),
    },
    {
      key: 'markerName',
      header: 'Marked By',
      render: (r: StudentAttendance) => (
        <small style={{ color: 'var(--text-secondary, #64748b)' }}>{r.markerName || 'System'}</small>
      ),
    },
    {
      key: 'remarks',
      header: 'Remarks',
      render: (r: StudentAttendance) => <small>{r.remarks || '—'}</small>,
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (r: StudentAttendance) => (
        <div style={{ display: 'inline-flex', gap: '0.35rem' }}>
          {canCorrect && (
            <Button size="sm" variant="secondary" onClick={() => setCorrectionTarget(r)}>
              Correct
            </Button>
          )}
          {r.corrected && (
            <Button size="sm" variant="ghost" onClick={() => setHistoryTargetId(r.id)}>
              History
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div style={{ display: 'grid', gap: '1.15rem' }}>
      <Card className="attendance-filter-card">
        <div className="attendance-filter-grid">
          <FormField label="From Date">
            <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </FormField>

          <FormField label="To Date">
            <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </FormField>

          <FormField label="Session">
            <Select value={sessionId} onChange={(e) => setSessionId(e.target.value)}>
              <option value="">All sessions</option>
              {sessionsQuery.data?.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </FormField>

          <FormField label="Class">
            <Select
              value={classId}
              onChange={(e) => {
                setClassId(e.target.value);
                setSectionId('');
              }}
            >
              <option value="">All classes</option>
              {classesQuery.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          </FormField>

          <FormField label="Section">
            <Select
              value={sectionId}
              onChange={(e) => setSectionId(e.target.value)}
              disabled={!classId || sectionsQuery.isPending}
            >
              <option value="">All sections</option>
              {sectionsQuery.data?.map((sec) => (
                <option key={sec.id} value={sec.id}>
                  {sec.name}
                </option>
              ))}
            </Select>
          </FormField>

          <FormField label="Status Filter">
            <Select value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All statuses</option>
              <option value="PRESENT">Present</option>
              <option value="ABSENT">Absent</option>
              <option value="LATE">Late</option>
              <option value="HALF_DAY">Half Day</option>
              <option value="EXCUSED">Excused</option>
              <option value="HOLIDAY">Holiday</option>
            </Select>
          </FormField>
        </div>
      </Card>

      {recordsQuery.isPending ? (
        <Card className="attendance-loading">Loading attendance records…</Card>
      ) : recordsQuery.isError ? (
        <ErrorState
          title="Failed to load attendance records"
          description={(recordsQuery.error as Error).message}
          onRetry={() => void recordsQuery.refetch()}
        />
      ) : !recordsQuery.data?.length ? (
        <EmptyState
          title="No records found"
          description="Try broadening your date range or filters above."
        />
      ) : (
        <Card className="attendance-table-card">
          <DataTable
            columns={columns}
            rows={recordsQuery.data}
            rowKey={(r) => String(r.id)}
            caption="Student attendance history"
          />
        </Card>
      )}

      {correctionTarget && (
        <StudentCorrectionModal
          record={correctionTarget}
          onClose={() => setCorrectionTarget(null)}
          onSuccess={() => {
            setCorrectionTarget(null);
            recordsQuery.refetch();
          }}
        />
      )}

      {historyTargetId !== null && (
        <CorrectionHistoryModal
          attendanceId={historyTargetId}
          onClose={() => setHistoryTargetId(null)}
        />
      )}
    </div>
  );
}

// ========================================================
// 3. STUDENT CORRECTION MODAL (Audit & Reason)
// ========================================================
function StudentCorrectionModal({
  record,
  onClose,
  onSuccess,
}: {
  record: StudentAttendance;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [newStatus, setNewStatus] = useState<string>(
    record.status === 'PRESENT' ? 'ABSENT' : 'PRESENT'
  );
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: () => attendanceApi.correctStudent(record.id, { status: newStatus, reason }),
    onSuccess,
    onError: (err: Error) => setError(err.message),
  });

  return (
    <Modal open onClose={onClose} title="Correct Attendance Record">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (newStatus === record.status) {
            setError('New status must be different from current status.');
            return;
          }
          if (reason.trim().length < 4) {
            setError('Please supply a reason of at least 4 characters.');
            return;
          }
          setError('');
          mutation.mutate();
        }}
        style={{ display: 'grid', gap: '1rem' }}
      >
        <div style={{ padding: '0.75rem', background: 'var(--bg-subtle, #f8fafc)', borderRadius: '8px' }}>
          <strong>{record.studentName}</strong> ({record.admissionNo})<br />
          <small>
            Date: {record.attendanceDate} · Class: {record.className} · Section: {record.sectionName}
          </small>
          <div style={{ marginTop: '0.4rem' }}>
            Current Status: <strong>{record.status}</strong>
          </div>
        </div>

        {error && <Alert tone="danger">{error}</Alert>}

        <FormField label="Corrected Status" required>
          <Select value={newStatus} onChange={(e) => setNewStatus(e.target.value)}>
            <option value="PRESENT">Present</option>
            <option value="ABSENT">Absent</option>
            <option value="LATE">Late</option>
            <option value="HALF_DAY">Half Day</option>
            <option value="EXCUSED">Excused</option>
            <option value="HOLIDAY">Holiday</option>
          </Select>
        </FormField>

        <FormField label="Reason for Correction (Audited)" required hint="Required audit log explanation">
          <Textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            required
            minLength={4}
            maxLength={500}
            rows={3}
            placeholder="e.g. Doctor's certificate provided for late arrival..."
          />
        </FormField>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" disabled={mutation.isPending}>
            {mutation.isPending ? 'Saving…' : 'Save Correction'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// ========================================================
// 4. CORRECTION HISTORY AUDIT TIMELINE
// ========================================================
function CorrectionHistoryModal({
  attendanceId,
  onClose,
}: {
  attendanceId: number;
  onClose: () => void;
}) {
  const query = useQuery({
    queryKey: ['attendance-corrections', attendanceId],
    queryFn: () => attendanceApi.studentCorrectionHistory(attendanceId),
  });

  return (
    <Modal open onClose={onClose} title="Attendance Correction Audit Trail">
      {query.isPending ? (
        <div className="attendance-loading">Loading audit timeline…</div>
      ) : query.isError ? (
        <Alert tone="danger">{(query.error as Error).message}</Alert>
      ) : !query.data?.length ? (
        <EmptyState title="No corrections" description="This record has not been altered since initial marking." />
      ) : (
        <div className="history-timeline">
          {query.data.map((c) => (
            <div key={c.id} className="history-item">
              <div className="history-item-header">
                <span className="history-transition">
                  <span className={`badge-status badge-${c.oldStatus.toLowerCase()}`}>{c.oldStatus}</span>
                  <span>→</span>
                  <span className={`badge-status badge-${c.newStatus.toLowerCase()}`}>{c.newStatus}</span>
                </span>
                <span className="history-meta">{new Date(c.correctedAt).toLocaleString()}</span>
              </div>
              <div className="history-reason">"{c.reason}"</div>
              <div className="history-meta">Corrected by: {c.correctedByName}</div>
            </div>
          ))}
        </div>
      )}
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1.25rem' }}>
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </div>
    </Modal>
  );
}

// ========================================================
// 5. ATTENDANCE SUMMARY VIEW (KPI & Rate Visuals)
// ========================================================
function AttendanceSummaryView() {
  const [attendanceDate, setAttendanceDate] = useState(todayIso());
  const [sessionId, setSessionId] = useState('');
  const [classId, setClassId] = useState('');
  const [sectionId, setSectionId] = useState('');

  const sessionsQuery = useQuery({ queryKey: ['attendance-sessions'], queryFn: attendanceApi.sessionOptions });
  const classesQuery = useQuery({ queryKey: ['attendance-classes'], queryFn: attendanceApi.classOptions });
  const sectionsQuery = useQuery({
    queryKey: ['attendance-sections', classId],
    queryFn: () => attendanceApi.sectionOptions(classId ? Number(classId) : undefined),
    enabled: Boolean(classId),
  });

  const summaryQuery = useQuery({
    queryKey: ['attendance-summary', attendanceDate, sessionId, classId, sectionId],
    queryFn: () =>
      attendanceApi.summary({
        attendanceDate,
        sessionId: sessionId ? Number(sessionId) : undefined,
        classId: classId ? Number(classId) : undefined,
        sectionId: sectionId ? Number(sectionId) : undefined,
      }),
  });

  const data: AttendanceSummary = summaryQuery.data ?? {
    total: 0,
    present: 0,
    absent: 0,
    late: 0,
    halfDay: 0,
    excused: 0,
    holiday: 0,
  };

  const presentPercentage = data.total > 0 ? Math.round((data.present / data.total) * 100) : 0;
  const latePercentage = data.total > 0 ? Math.round((data.late / data.total) * 100) : 0;
  const halfDayPercentage = data.total > 0 ? Math.round((data.halfDay / data.total) * 100) : 0;
  const absentPercentage = data.total > 0 ? Math.round((data.absent / data.total) * 100) : 0;
  const excusedPercentage = data.total > 0 ? Math.round((data.excused / data.total) * 100) : 0;

  return (
    <div style={{ display: 'grid', gap: '1.25rem' }}>
      <Card className="attendance-filter-card">
        <div className="attendance-filter-grid">
          <FormField label="Summary Date" required>
            <Input type="date" value={attendanceDate} onChange={(e) => setAttendanceDate(e.target.value)} />
          </FormField>

          <FormField label="Session">
            <Select value={sessionId} onChange={(e) => setSessionId(e.target.value)}>
              <option value="">All sessions</option>
              {sessionsQuery.data?.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name}
                </option>
              ))}
            </Select>
          </FormField>

          <FormField label="Class">
            <Select
              value={classId}
              onChange={(e) => {
                setClassId(e.target.value);
                setSectionId('');
              }}
            >
              <option value="">All classes</option>
              {classesQuery.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </Select>
          </FormField>

          <FormField label="Section">
            <Select
              value={sectionId}
              onChange={(e) => setSectionId(e.target.value)}
              disabled={!classId || sectionsQuery.isPending}
            >
              <option value="">All sections</option>
              {sectionsQuery.data?.map((sec) => (
                <option key={sec.id} value={sec.id}>
                  {sec.name}
                </option>
              ))}
            </Select>
          </FormField>
        </div>
      </Card>

      {summaryQuery.isPending ? (
        <Card className="attendance-loading">Calculating attendance stats…</Card>
      ) : summaryQuery.isError ? (
        <ErrorState
          title="Summary unavailable"
          description={(summaryQuery.error as Error).message}
          onRetry={() => void summaryQuery.refetch()}
        />
      ) : (
        <div className="attendance-summary-grid">
          <div className="attendance-progress-card">
            <h3 style={{ margin: '0 0 0.5rem', fontSize: '1.1rem' }}>Overall Attendance Ratio</h3>
            <div className="attendance-rate-display">
              <span className="rate-percentage">{presentPercentage}%</span>
              <span className="rate-label">Present on {attendanceDate}</span>
            </div>

            <div className="progress-bar-track" title="Attendance breakdown">
              <div
                className="progress-segment seg-present"
                style={{ width: `${presentPercentage}%` }}
                title={`Present: ${presentPercentage}%`}
              />
              <div
                className="progress-segment seg-late"
                style={{ width: `${latePercentage}%` }}
                title={`Late: ${latePercentage}%`}
              />
              <div
                className="progress-segment seg-halfday"
                style={{ width: `${halfDayPercentage}%` }}
                title={`Half Day: ${halfDayPercentage}%`}
              />
              <div
                className="progress-segment seg-excused"
                style={{ width: `${excusedPercentage}%` }}
                title={`Excused: ${excusedPercentage}%`}
              />
              <div
                className="progress-segment seg-absent"
                style={{ width: `${absentPercentage}%` }}
                title={`Absent: ${absentPercentage}%`}
              />
            </div>

            <div className="attendance-stats-bar">
              <div className="stat-pill">
                <span className="stat-pill-label">Total Marked</span>
                <span className="stat-pill-value">{data.total}</span>
              </div>
              <div className="stat-pill pill-present">
                <span className="stat-pill-label">Present</span>
                <span className="stat-pill-value">{data.present}</span>
              </div>
              <div className="stat-pill pill-absent">
                <span className="stat-pill-label">Absent</span>
                <span className="stat-pill-value">{data.absent}</span>
              </div>
              <div className="stat-pill pill-late">
                <span className="stat-pill-label">Late</span>
                <span className="stat-pill-value">{data.late}</span>
              </div>
              <div className="stat-pill pill-halfday">
                <span className="stat-pill-label">Half Day</span>
                <span className="stat-pill-value">{data.halfDay}</span>
              </div>
              <div className="stat-pill pill-excused">
                <span className="stat-pill-label">Excused</span>
                <span className="stat-pill-value">{data.excused}</span>
              </div>
            </div>
          </div>

          <Card style={{ padding: '1.25rem' }}>
            <h4 style={{ margin: '0 0 0.75rem', fontSize: '1rem' }}>Metric Details</h4>
            <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'grid', gap: '0.65rem' }}>
              <li style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Present Rate</span>
                <strong>{presentPercentage}%</strong>
              </li>
              <li style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Absent Rate</span>
                <strong style={{ color: '#dc2626' }}>{absentPercentage}%</strong>
              </li>
              <li style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Late Rate</span>
                <strong style={{ color: '#d97706' }}>{latePercentage}%</strong>
              </li>
              <li style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Half Day Rate</span>
                <strong style={{ color: '#9333ea' }}>{halfDayPercentage}%</strong>
              </li>
              <li style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>Holidays Recorded</span>
                <strong>{data.holiday}</strong>
              </li>
            </ul>
          </Card>
        </div>
      )}
    </div>
  );
}

// ========================================================
// 6. STAFF ATTENDANCE VIEW (Marking, Query, Corrections)
// ========================================================
function StaffAttendanceView() {
  const { hasPermission } = useAuth();
  const canMarkStaff = hasPermission('STAFF_ATTENDANCE:MARK') || hasPermission('STAFF_ATTENDANCE:MANAGE');
  const canCorrectStaff = hasPermission('STAFF_ATTENDANCE:CORRECT') || hasPermission('STAFF_ATTENDANCE:MANAGE');

  const [mode, setMode] = useState<'mark' | 'records'>(canMarkStaff ? 'mark' : 'records');
  const [attendanceDate, setAttendanceDate] = useState(todayIso());
  const [from, setFrom] = useState(todayIso());
  const [to, setTo] = useState(todayIso());
  const [staffIdFilter, setStaffIdFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [message, setMessage] = useState<{ tone: 'success' | 'danger'; text: string } | null>(null);

  const [correctionTarget, setCorrectionTarget] = useState<StaffAttendance | null>(null);
  const [historyTargetId, setHistoryTargetId] = useState<number | null>(null);

  const staffListQuery = useQuery({
    queryKey: ['attendance-staff-options'],
    queryFn: attendanceApi.staffOptions,
  });

  const [staffMarkingMap, setStaffMarkingMap] = useState<Record<number, { status: string; remarks: string }>>({});

  useEffect(() => {
    if (staffListQuery.data) {
      const initial: Record<number, { status: string; remarks: string }> = {};
      staffListQuery.data.forEach((s) => {
        initial[s.id] = { status: 'PRESENT', remarks: '' };
      });
      setStaffMarkingMap(initial);
    }
  }, [staffListQuery.data]);

  const updateStaffStatus = (id: number, status: string) => {
    setStaffMarkingMap((prev) => {
      const cur = prev[id] ?? { status: 'PRESENT', remarks: '' };
      return {
        ...prev,
        [id]: { ...cur, status },
      };
    });
  };

  const updateStaffRemarks = (id: number, remarks: string) => {
    setStaffMarkingMap((prev) => {
      const cur = prev[id] ?? { status: 'PRESENT', remarks: '' };
      return {
        ...prev,
        [id]: { ...cur, remarks },
      };
    });
  };

  const markAllStaff = (status: string) => {
    setStaffMarkingMap((prev) => {
      const next: Record<number, { status: string; remarks: string }> = {};
      Object.keys(prev).forEach((idStr) => {
        const id = Number(idStr);
        const cur = prev[id] ?? { status: 'PRESENT', remarks: '' };
        next[id] = { ...cur, status };
      });
      return next;
    });
  };

  const saveStaffMutation = useMutation({
    mutationFn: async () => {
      if (!staffListQuery.data?.length) return null;
      const entries = staffListQuery.data.map((s) => ({
        staffId: s.id,
        status: (staffMarkingMap[s.id]?.status || 'PRESENT') as
          | 'PRESENT'
          | 'ABSENT'
          | 'LATE'
          | 'HALF_DAY'
          | 'ON_LEAVE',
        remarks: staffMarkingMap[s.id]?.remarks || undefined,
      }));
      const key = `staff-mark-${attendanceDate}`;
      return attendanceApi.markStaff({ attendanceDate, entries }, key);
    },
    onSuccess: (res) => {
      if (res) {
        setMessage({
          tone: 'success',
          text: `Staff attendance saved! Recorded ${res.created} new record(s), ${res.alreadyMarked} already recorded.`,
        });
      }
    },
    onError: (err: Error) => {
      setMessage({ tone: 'danger', text: err.message });
    },
  });

  const staffRecordsQuery = useQuery({
    queryKey: ['staff-records', from, to, staffIdFilter, statusFilter],
    queryFn: () =>
      attendanceApi.staffRecords({
        from: from || undefined,
        to: to || undefined,
        staffId: staffIdFilter ? Number(staffIdFilter) : undefined,
        status: statusFilter || undefined,
      }),
    enabled: mode === 'records',
  });

  const markColumns = [
    {
      key: 'name',
      header: 'Staff Member',
      render: (r: { id: number; name: string }) => <strong>{r.name}</strong>,
    },
    {
      key: 'status',
      header: 'Status',
      render: (r: { id: number; name: string }) => {
        const cur = staffMarkingMap[r.id]?.status ?? 'PRESENT';
        return (
          <div className="status-toggle-group">
            <button
              type="button"
              className={`status-btn ${cur === 'PRESENT' ? 'active-present' : ''}`}
              onClick={() => updateStaffStatus(r.id, 'PRESENT')}
            >
              Present
            </button>
            <button
              type="button"
              className={`status-btn ${cur === 'ABSENT' ? 'active-absent' : ''}`}
              onClick={() => updateStaffStatus(r.id, 'ABSENT')}
            >
              Absent
            </button>
            <button
              type="button"
              className={`status-btn ${cur === 'LATE' ? 'active-late' : ''}`}
              onClick={() => updateStaffStatus(r.id, 'LATE')}
            >
              Late
            </button>
            <button
              type="button"
              className={`status-btn ${cur === 'HALF_DAY' ? 'active-half_day' : ''}`}
              onClick={() => updateStaffStatus(r.id, 'HALF_DAY')}
            >
              Half Day
            </button>
            <button
              type="button"
              className={`status-btn ${cur === 'ON_LEAVE' ? 'active-on_leave' : ''}`}
              onClick={() => updateStaffStatus(r.id, 'ON_LEAVE')}
            >
              Leave
            </button>
          </div>
        );
      },
    },
    {
      key: 'remarks',
      header: 'Remarks',
      render: (r: { id: number; name: string }) => (
        <Input
          placeholder="Optional remarks..."
          value={staffMarkingMap[r.id]?.remarks ?? ''}
          onChange={(e) => updateStaffRemarks(r.id, e.target.value)}
          style={{ minWidth: '160px', padding: '0.25rem 0.5rem', fontSize: '0.8125rem' }}
        />
      ),
    },
  ];

  const recordColumns = [
    {
      key: 'attendanceDate',
      header: 'Date',
      render: (r: StaffAttendance) => <span>{r.attendanceDate}</span>,
    },
    {
      key: 'employeeNo',
      header: 'Employee No',
      render: (r: StaffAttendance) => <strong>{r.employeeNo || '—'}</strong>,
    },
    {
      key: 'staffName',
      header: 'Staff Name',
      render: (r: StaffAttendance) => <span>{r.staffName}</span>,
    },
    {
      key: 'status',
      header: 'Status',
      render: (r: StaffAttendance) => (
        <div style={{ display: 'inline-flex', alignItems: 'center' }}>
          <span className={`badge-status badge-${r.status.toLowerCase()}`}>{r.status.replace('_', ' ')}</span>
          {r.corrected && <span className="badge-corrected">Corrected</span>}
        </div>
      ),
    },
    {
      key: 'markerName',
      header: 'Marked By',
      render: (r: StaffAttendance) => (
        <small style={{ color: 'var(--text-secondary, #64748b)' }}>{r.markerName || 'Admin'}</small>
      ),
    },
    {
      key: 'remarks',
      header: 'Remarks',
      render: (r: StaffAttendance) => <small>{r.remarks || '—'}</small>,
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (r: StaffAttendance) => (
        <div style={{ display: 'inline-flex', gap: '0.35rem' }}>
          {canCorrectStaff && (
            <Button size="sm" variant="secondary" onClick={() => setCorrectionTarget(r)}>
              Correct
            </Button>
          )}
          {r.corrected && (
            <Button size="sm" variant="ghost" onClick={() => setHistoryTargetId(r.id)}>
              History
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div style={{ display: 'grid', gap: '1.15rem' }}>
      <div style={{ display: 'flex', gap: '0.5rem' }}>
        {canMarkStaff && (
          <Button
            variant={mode === 'mark' ? 'primary' : 'secondary'}
            size="sm"
            onClick={() => {
              setMode('mark');
              setMessage(null);
            }}
          >
            Mark Staff Attendance
          </Button>
        )}
        <Button
          variant={mode === 'records' ? 'primary' : 'secondary'}
          size="sm"
          onClick={() => {
            setMode('records');
            setMessage(null);
          }}
        >
          Staff Attendance Records
        </Button>
      </div>

      {message && <Alert tone={message.tone}>{message.text}</Alert>}

      {mode === 'mark' ? (
        <>
          <Card className="attendance-filter-card">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '1rem' }}>
              <FormField label="Marking Date" required>
                <Input
                  type="date"
                  value={attendanceDate}
                  onChange={(e) => setAttendanceDate(e.target.value)}
                  max={todayIso()}
                />
              </FormField>

              <div className="quick-btn-group" style={{ alignSelf: 'flex-end' }}>
                <button type="button" className="quick-action-btn" onClick={() => markAllStaff('PRESENT')}>
                  ✓ All Present
                </button>
                <button type="button" className="quick-action-btn" onClick={() => markAllStaff('ABSENT')}>
                  ✗ All Absent
                </button>
                <Button
                  variant="primary"
                  disabled={saveStaffMutation.isPending}
                  onClick={() => saveStaffMutation.mutate()}
                >
                  {saveStaffMutation.isPending ? 'Saving…' : 'Submit Staff Attendance'}
                </Button>
              </div>
            </div>
          </Card>

          {staffListQuery.isPending ? (
            <Card className="attendance-loading">Loading staff list…</Card>
          ) : !staffListQuery.data?.length ? (
            <EmptyState title="No active staff members" description="Active staff are needed to mark attendance." />
          ) : (
            <Card className="attendance-table-card">
              <DataTable
                columns={markColumns}
                rows={staffListQuery.data}
                rowKey={(r) => String(r.id)}
                caption="Staff attendance list"
              />
            </Card>
          )}
        </>
      ) : (
        <>
          <Card className="attendance-filter-card">
            <div className="attendance-filter-grid">
              <FormField label="From Date">
                <Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
              </FormField>

              <FormField label="To Date">
                <Input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
              </FormField>

              <FormField label="Staff Member">
                <Select value={staffIdFilter} onChange={(e) => setStaffIdFilter(e.target.value)}>
                  <option value="">All staff</option>
                  {staffListQuery.data?.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </Select>
              </FormField>

              <FormField label="Status">
                <Select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
                  <option value="">All statuses</option>
                  <option value="PRESENT">Present</option>
                  <option value="ABSENT">Absent</option>
                  <option value="LATE">Late</option>
                  <option value="HALF_DAY">Half Day</option>
                  <option value="ON_LEAVE">On Leave</option>
                </Select>
              </FormField>
            </div>
          </Card>

          {staffRecordsQuery.isPending ? (
            <Card className="attendance-loading">Loading staff records…</Card>
          ) : staffRecordsQuery.isError ? (
            <ErrorState
              title="Staff records unavailable"
              description={(staffRecordsQuery.error as Error).message}
              onRetry={() => void staffRecordsQuery.refetch()}
            />
          ) : !staffRecordsQuery.data?.length ? (
            <EmptyState title="No staff records found" description="Adjust your date range or filters." />
          ) : (
            <Card className="attendance-table-card">
              <DataTable
                columns={recordColumns}
                rows={staffRecordsQuery.data}
                rowKey={(r) => String(r.id)}
                caption="Staff attendance records"
              />
            </Card>
          )}
        </>
      )}

      {correctionTarget && (
        <StaffCorrectionModal
          record={correctionTarget}
          onClose={() => setCorrectionTarget(null)}
          onSuccess={() => {
            setCorrectionTarget(null);
            staffRecordsQuery.refetch();
          }}
        />
      )}

      {historyTargetId !== null && (
        <StaffHistoryModal
          attendanceId={historyTargetId}
          onClose={() => setHistoryTargetId(null)}
        />
      )}
    </div>
  );
}

// Staff correction modal
function StaffCorrectionModal({
  record,
  onClose,
  onSuccess,
}: {
  record: StaffAttendance;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [newStatus, setNewStatus] = useState<string>(
    record.status === 'PRESENT' ? 'ON_LEAVE' : 'PRESENT'
  );
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  const mutation = useMutation({
    mutationFn: () => attendanceApi.correctStaff(record.id, { status: newStatus, reason }),
    onSuccess,
    onError: (err: Error) => setError(err.message),
  });

  return (
    <Modal open onClose={onClose} title="Correct Staff Attendance">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (newStatus === record.status) {
            setError('New status must differ from current status.');
            return;
          }
          if (reason.trim().length < 4) {
            setError('Reason must be at least 4 characters long.');
            return;
          }
          setError('');
          mutation.mutate();
        }}
        style={{ display: 'grid', gap: '1rem' }}
      >
        <div style={{ padding: '0.75rem', background: 'var(--bg-subtle, #f8fafc)', borderRadius: '8px' }}>
          <strong>{record.staffName}</strong> ({record.employeeNo || 'Staff'})<br />
          <small>Date: {record.attendanceDate}</small>
          <div style={{ marginTop: '0.35rem' }}>
            Current Status: <strong>{record.status}</strong>
          </div>
        </div>

        {error && <Alert tone="danger">{error}</Alert>}

        <FormField label="Corrected Status" required>
          <Select value={newStatus} onChange={(e) => setNewStatus(e.target.value)}>
            <option value="PRESENT">Present</option>
            <option value="ABSENT">Absent</option>
            <option value="LATE">Late</option>
            <option value="HALF_DAY">Half Day</option>
            <option value="ON_LEAVE">On Leave</option>
          </Select>
        </FormField>

        <FormField label="Reason (Audited)" required>
          <Textarea
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            required
            minLength={4}
            rows={3}
            placeholder="Official reason for correction..."
          />
        </FormField>

        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" disabled={mutation.isPending}>
            {mutation.isPending ? 'Saving…' : 'Save Correction'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// Staff correction history
function StaffHistoryModal({
  attendanceId,
  onClose,
}: {
  attendanceId: number;
  onClose: () => void;
}) {
  const query = useQuery({
    queryKey: ['staff-attendance-corrections', attendanceId],
    queryFn: () => attendanceApi.staffCorrectionHistory(attendanceId),
  });

  return (
    <Modal open onClose={onClose} title="Staff Attendance Audit Trail">
      {query.isPending ? (
        <div className="attendance-loading">Loading audit timeline…</div>
      ) : query.isError ? (
        <Alert tone="danger">{(query.error as Error).message}</Alert>
      ) : !query.data?.length ? (
        <EmptyState title="No corrections" description="This record has not been corrected." />
      ) : (
        <div className="history-timeline">
          {query.data.map((c) => (
            <div key={c.id} className="history-item">
              <div className="history-item-header">
                <span className="history-transition">
                  <span className={`badge-status badge-${c.oldStatus.toLowerCase()}`}>{c.oldStatus}</span>
                  <span>→</span>
                  <span className={`badge-status badge-${c.newStatus.toLowerCase()}`}>{c.newStatus}</span>
                </span>
                <span className="history-meta">{new Date(c.correctedAt).toLocaleString()}</span>
              </div>
              <div className="history-reason">"{c.reason}"</div>
              <div className="history-meta">Corrected by: {c.correctedByName}</div>
            </div>
          ))}
        </div>
      )}
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '1.25rem' }}>
        <Button variant="secondary" onClick={onClose}>
          Close
        </Button>
      </div>
    </Modal>
  );
}

// ========================================================
// 7. DATE LOCKS VIEW (Manager Access)
// ========================================================
function DateLocksView() {
  const queryClient = useQueryClient();
  const [openLockModal, setOpenLockModal] = useState(false);
  const [scope, setScope] = useState<'STUDENT' | 'STAFF'>('STUDENT');
  const [sessionId, setSessionId] = useState('');
  const [date, setDate] = useState(todayIso());
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');

  const sessionsQuery = useQuery({ queryKey: ['attendance-sessions'], queryFn: attendanceApi.sessionOptions });
  const locksQuery = useQuery({ queryKey: ['attendance-locks'], queryFn: () => attendanceApi.locks() });

  const lockMutation = useMutation({
    mutationFn: () =>
      attendanceApi.createLock({
        scope,
        sessionId: scope === 'STUDENT' ? Number(sessionId) : null,
        attendanceDate: date,
        reason,
      }),
    onSuccess: () => {
      setOpenLockModal(false);
      setReason('');
      queryClient.invalidateQueries({ queryKey: ['attendance-locks'] });
    },
    onError: (err: Error) => setError(err.message),
  });

  const unlockMutation = useMutation({
    mutationFn: (id: number) => attendanceApi.unlock(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['attendance-locks'] }),
    onError: (err: Error) => setError(err.message),
  });

  const columns = [
    {
      key: 'scope',
      header: 'Scope',
      render: (r: DateLock) => <StatusBadge status={r.scope === 'STUDENT' ? 'Active' : 'Neutral'} />,
    },
    {
      key: 'attendanceDate',
      header: 'Locked Date',
      render: (r: DateLock) => <strong>{r.attendanceDate}</strong>,
    },
    {
      key: 'reason',
      header: 'Reason',
      render: (r: DateLock) => <span>{r.reason}</span>,
    },
    {
      key: 'lockedBy',
      header: 'Locked By',
      render: (r: DateLock) => <small>{r.lockedByName}</small>,
    },
    {
      key: 'lockedAt',
      header: 'Locked At',
      render: (r: DateLock) => <small>{new Date(r.lockedAt).toLocaleString()}</small>,
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (r: DateLock) => (
        <Button
          size="sm"
          variant="secondary"
          disabled={unlockMutation.isPending}
          onClick={() => unlockMutation.mutate(r.id)}
        >
          Unlock
        </Button>
      ),
    },
  ];

  return (
    <div style={{ display: 'grid', gap: '1.15rem' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ margin: 0, color: 'var(--text-secondary, #64748b)' }}>
          Locked dates prevent any new attendance marking or edits by teachers and staff.
        </p>
        <Button variant="primary" onClick={() => setOpenLockModal(true)}>
          + Lock A Date
        </Button>
      </div>

      {error && <Alert tone="danger">{error}</Alert>}

      {locksQuery.isPending ? (
        <Card className="attendance-loading">Loading date locks…</Card>
      ) : locksQuery.isError ? (
        <ErrorState
          title="Date locks unavailable"
          description={(locksQuery.error as Error).message}
          onRetry={() => void locksQuery.refetch()}
        />
      ) : !locksQuery.data?.length ? (
        <EmptyState title="No locked dates" description="All session dates are currently open for marking." />
      ) : (
        <Card className="attendance-table-card">
          <DataTable
            columns={columns}
            rows={locksQuery.data}
            rowKey={(r) => String(r.id)}
            caption="Active date locks"
          />
        </Card>
      )}

      {openLockModal && (
        <Modal open onClose={() => setOpenLockModal(false)} title="Lock Attendance Date">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (scope === 'STUDENT' && !sessionId) {
                setError('Session is required for student date locks.');
                return;
              }
              if (reason.trim().length < 4) {
                setError('Reason must be at least 4 characters long.');
                return;
              }
              setError('');
              lockMutation.mutate();
            }}
            style={{ display: 'grid', gap: '1rem' }}
          >
            {error && <Alert tone="danger">{error}</Alert>}

            <FormField label="Lock Scope" required>
              <Select value={scope} onChange={(e) => setScope(e.target.value as 'STUDENT' | 'STAFF')}>
                <option value="STUDENT">Student Attendance</option>
                <option value="STAFF">Staff Attendance</option>
              </Select>
            </FormField>

            {scope === 'STUDENT' && (
              <FormField label="Academic Session" required>
                <Select value={sessionId} onChange={(e) => setSessionId(e.target.value)} required>
                  <option value="">Select session</option>
                  {sessionsQuery.data?.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
                </Select>
              </FormField>
            )}

            <FormField label="Attendance Date to Lock" required>
              <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
            </FormField>

            <FormField label="Lock Reason" required>
              <Textarea
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                required
                minLength={4}
                rows={3}
                placeholder="e.g. Month end payroll closure / term end audit..."
              />
            </FormField>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
              <Button type="button" variant="secondary" onClick={() => setOpenLockModal(false)}>
                Cancel
              </Button>
              <Button variant="primary" disabled={lockMutation.isPending}>
                {lockMutation.isPending ? 'Locking…' : 'Lock Date'}
              </Button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
