import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, DataTable, EmptyState, ErrorState, FormField, Input, PageHeader, Pagination, Select, StatusBadge, Textarea } from '../components';
import { useAuth } from '../auth/AuthProvider';
import { studentApi } from '../api/students';
import type { Admission, ApiFailure, Guardian, Student, StudentDocument, StudentHistory, StudentSummary } from '../api/students';
import './student-pages.css';

const EMPTY_FILTERS = { search: '', status: '', sortBy: 'createdAt', direction: 'DESC', page: 0, size: 20 };

export function StudentListPage() {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const query = useQuery({ queryKey: ['students', filters], queryFn: ({ signal }) => studentApi.list(filters, signal) });
  const { hasPermission, user } = useAuth();
  const isParent = Boolean(user?.roles?.includes('PARENT_GUARDIAN') && !user?.roles?.some(r => r === 'SUPER_ADMIN' || r === 'ADMIN' || r === 'TEACHER'));
  const rows = query.data?.content ?? [];
  const columns = [
    { key: 'admissionNo', header: 'Admission no.', render: (row: StudentSummary) => <Link className="student-table-link" to={`/app/students/${row.id}`}>{row.admissionNo}</Link> },
    { key: 'name', header: isParent ? 'Child' : 'Student', render: (row: StudentSummary) => <span className="student-table-name">{row.firstName} {row.lastName ?? ''}</span> },
    { key: 'status', header: 'Status', render: (row: StudentSummary) => <StatusBadge status={row.status} /> },
    { key: 'admissionDate', header: 'Admission date', render: (row: StudentSummary) => row.admissionDate ? new Date(`${row.admissionDate}T00:00:00`).toLocaleDateString() : '—' },
  ];
  return <div className="workspace-page student-page">
    <PageHeader
      eyebrow={isParent ? "PARENT PORTAL" : "STUDENT LIFECYCLE"}
      title={isParent ? "My Children" : "Students"}
      description={isParent ? "View linked student profiles and academic details for your children." : "Search, filter and manage student profiles."}
      actions={hasPermission('STUDENT:CREATE') ? <Link className="ds-button ds-button--primary" to="/app/students/new">Add student <span aria-hidden="true">＋</span></Link> : undefined}
    />
    {!isParent && <Card className="student-filter-card"><form className="student-filter-form" onSubmit={event => { event.preventDefault(); setFilters(value => ({ ...value, page: 0 })); }}>
      <FormField label="Search students"><Input aria-label="Search students" placeholder="Name, admission number or email" value={filters.search} onChange={e => setFilters(value => ({ ...value, search: e.target.value, page: 0 }))} /></FormField>
      <FormField label="Status"><Select aria-label="Filter by status" value={filters.status} onChange={e => setFilters(value => ({ ...value, status: e.target.value, page: 0 }))}><option value="">All statuses</option><option value="APPLIED">Applied</option><option value="ACTIVE">Active</option><option value="TRANSFERRED">Transferred</option><option value="EXITED">Exited</option></Select></FormField>
      <FormField label="Sort by"><Select aria-label="Sort students by" value={filters.sortBy} onChange={e => setFilters(value => ({ ...value, sortBy: e.target.value }))}><option value="createdAt">Recently added</option><option value="admissionNo">Admission no.</option><option value="firstName">First name</option><option value="lastName">Last name</option><option value="admissionDate">Admission date</option><option value="status">Status</option></Select></FormField>
      <FormField label="Order"><Select aria-label="Sort order" value={filters.direction} onChange={e => setFilters(value => ({ ...value, direction: e.target.value }))}><option value="DESC">Descending</option><option value="ASC">Ascending</option></Select></FormField>
      <Button type="submit">Apply</Button>
    </form></Card>}
    {query.isPending ? <Card className="student-loading" role="status">Loading students…</Card> : query.isError ? <ErrorState title="Students couldn’t be loaded" description={(query.error as Error).message} onRetry={() => void query.refetch()} /> : <Card className="student-list-card">
      {rows.length ? <DataTable columns={columns} rows={rows} rowKey={row => String(row.id)} caption={isParent ? "My Children" : "Students"} /> : <EmptyState title={isParent ? "No linked children found" : "No students found"} description={isParent ? "No students are currently linked to your guardian account. Please contact school administration to link your child's profile." : "Try changing the search or filters, or add a student if you have access."} />}
      {!!query.data && (!isParent || query.data.totalPages > 1) && <Pagination page={query.data.page + 1} pageCount={Math.max(1, query.data.totalPages)} onPageChange={page => setFilters(value => ({ ...value, page: page - 1 }))} itemLabel="page" />}
    </Card>}
  </div>;
}

type StudentFields = { admissionNo: string; firstName: string; lastName: string; dateOfBirth: string; gender: string; bloodGroup: string; religion: string; category: string; phone: string; email: string; address: string; photoUrl: string };
const initialStudent: StudentFields = { admissionNo: '', firstName: '', lastName: '', dateOfBirth: '', gender: '', bloodGroup: '', religion: '', category: '', phone: '', email: '', address: '', photoUrl: '' };

export const GUARDIAN_RELATIONS = [
  'Father',
  'Mother',
  'Guardian',
  'Grandfather',
  'Grandmother',
  'Uncle',
  'Aunt',
  'Brother',
  'Sister',
  'Other',
];

function extract10Digits(raw: string | null | undefined): string {
  if (!raw) return '';
  let s = String(raw).trim();
  if (s.startsWith('+91')) {
    s = s.slice(3).trim();
  } else if (s.startsWith('+')) {
    s = s.slice(1).trim();
  }
  let digits = s.replace(/\D/g, '');
  if (digits.length > 10 && digits.startsWith('91')) {
    digits = digits.slice(2);
  }
  digits = digits.replace(/^0+/, '');
  return digits.slice(0, 10);
}

function formatDisplayPhone(raw: string | null | undefined): string {
  if (!raw) return '—';
  const digits = extract10Digits(raw);
  if (digits.length === 10) {
    return `+91 ${digits.slice(0, 5)} ${digits.slice(5)}`;
  }
  return raw;
}

function IndianPhoneInput({ value, onChange, disabled, id, placeholder = '98765 43210' }: { value: string; onChange: (val: string) => void; disabled?: boolean; id?: string; placeholder?: string }) {
  const digits = extract10Digits(value);
  return (
    <div className="phone-input-group">
      <span className="phone-country-code" aria-hidden="true">+91</span>
      <Input
        id={id}
        type="tel"
        inputMode="numeric"
        placeholder={placeholder}
        maxLength={10}
        value={digits}
        disabled={disabled}
        onChange={e => {
          let clean = e.target.value.replace(/\D/g, '');
          clean = clean.replace(/^0+/, '');
          clean = clean.slice(0, 10);
          onChange(clean ? `+91 ${clean}` : '');
        }}
        onPaste={e => {
          e.preventDefault();
          const pasted = e.clipboardData.getData('text');
          const clean = extract10Digits(pasted);
          onChange(clean ? `+91 ${clean}` : '');
        }}
      />
    </div>
  );
}

function sanitizePayload<T extends Record<string, unknown>>(payload: T): T {
  return Object.fromEntries(Object.entries(payload).flatMap(([key, value]) => {
    if (value === undefined || value === null) return [];
    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (!trimmed) return [];
      return [[key, trimmed]];
    }
    if (typeof value === 'object' && !Array.isArray(value)) {
      return [[key, sanitizePayload(value as Record<string, unknown>)]];
    }
    return [[key, value]];
  })) as T;
}

export function StudentFormPage() {
  const { id } = useParams(); const studentId = Number(id); const editing = Boolean(id); const navigate = useNavigate(); const client = useQueryClient();
  const detail = useQuery({ queryKey: ['student', studentId], queryFn: ({ signal }) => studentApi.get(studentId, signal), enabled: editing });
  const [form, setForm] = useState<StudentFields>(initialStudent); const [failure, setFailure] = useState<ApiFailure | null>(null);
  useEffect(() => { if (detail.data) setForm(studentToForm(detail.data)); }, [detail.data]);
  const phoneDigits = extract10Digits(form.phone);
  const phoneError = (fieldErrorName: string) => failure?.fieldErrors?.find(item => item.field === fieldErrorName)?.message;
  const validationPhoneError = phoneDigits.length > 0 && phoneDigits.length < 10 ? 'Mobile number must be exactly 10 digits' : undefined;
  const save = useMutation({
    mutationFn: () => {
      const payload = sanitizePayload({
        ...form,
        phone: phoneDigits ? `+91 ${phoneDigits}` : undefined,
      });
      return editing ? studentApi.update(studentId, payload) : studentApi.create(payload);
    },
    onSuccess: async record => {
      await client.invalidateQueries({ queryKey: ['students'] });
      await client.invalidateQueries({ queryKey: ['student'] });
      navigate(`/app/students/${record.id}`);
    },
    onError: error => setFailure(error as ApiFailure)
  });
  if (editing && detail.isPending) return <div className="auth-loading" role="status">Loading student…</div>;
  if (editing && detail.isError) return <ErrorState title="Student couldn’t be loaded" description={(detail.error as Error).message} onRetry={() => void detail.refetch()} />;
  const set = (key: keyof StudentFields, value: string) => setForm(current => ({ ...current, [key]: value }));
  const fieldError = (name: string) => phoneError(name);
  return <div className="workspace-page student-page"><PageHeader eyebrow={editing ? 'STUDENT PROFILE' : 'STUDENT LIFECYCLE'} title={editing ? 'Edit student' : 'Add student'} description="Enter the student profile details. The admission number must be unique." />
    {failure && <Alert tone="danger" title="Couldn’t save student">{failure.message}</Alert>}
    <Card><form className="student-form-grid" onSubmit={event => { event.preventDefault(); setFailure(null); save.mutate(); }}>
      {!editing && <FormField label="Admission number" required error={fieldError('admissionNo')}><Input value={form.admissionNo} onChange={e => set('admissionNo', e.target.value)} required maxLength={50} /></FormField>}
      <FormField label="First name" required error={fieldError('firstName')}><Input value={form.firstName} onChange={e => set('firstName', e.target.value)} required maxLength={100} /></FormField>
      <FormField label="Last name"><Input value={form.lastName} onChange={e => set('lastName', e.target.value)} maxLength={100} /></FormField>
      <FormField label="Date of birth"><Input type="date" value={form.dateOfBirth} onChange={e => set('dateOfBirth', e.target.value)} /></FormField>
      <FormField label="Gender"><Select value={form.gender} onChange={e => set('gender', e.target.value)}><option value="">Select gender</option><option value="FEMALE">Female</option><option value="MALE">Male</option><option value="OTHER">Other</option></Select></FormField>
      <FormField label="Blood group"><Input value={form.bloodGroup} onChange={e => set('bloodGroup', e.target.value)} maxLength={5} /></FormField>
      <FormField label="Category"><Input value={form.category} onChange={e => set('category', e.target.value)} maxLength={50} /></FormField>
      <FormField label="Religion"><Input value={form.religion} onChange={e => set('religion', e.target.value)} maxLength={50} /></FormField>
      <FormField label="Phone" hint="10-digit Indian mobile number" error={fieldError('phone') || validationPhoneError}><IndianPhoneInput value={form.phone} onChange={val => set('phone', val)} /></FormField>
      <FormField label="Email"><Input type="email" value={form.email} onChange={e => set('email', e.target.value)} maxLength={254} /></FormField>
      <FormField label="Photo URL"><Input type="url" value={form.photoUrl} onChange={e => set('photoUrl', e.target.value)} maxLength={500} /></FormField>
      <FormField label="Address" className="student-form-wide"><Textarea value={form.address} onChange={e => set('address', e.target.value)} maxLength={4000} rows={3} /></FormField>
      <div className="student-form-actions"><Button type="button" variant="secondary" onClick={() => navigate(editing ? `/app/students/${studentId}` : '/app/students')}>Cancel</Button><Button type="submit" disabled={save.isPending || Boolean(validationPhoneError)}>{save.isPending ? 'Saving…' : editing ? 'Save changes' : 'Create student'}</Button></div>
    </form></Card>
  </div>;
}

function studentToForm(student: Student): StudentFields { return { admissionNo: student.admissionNo, firstName: student.firstName, lastName: student.lastName ?? '', dateOfBirth: student.dateOfBirth ?? '', gender: student.gender ?? '', bloodGroup: student.bloodGroup ?? '', religion: student.religion ?? '', category: student.category ?? '', phone: student.phone ?? '', email: student.email ?? '', address: student.address ?? '', photoUrl: student.photoUrl ?? '' }; }

export function StudentDetailPage() {
  const { id } = useParams(); const studentId = Number(id); const { hasPermission, user } = useAuth(); const queryClient = useQueryClient();
  const isParent = Boolean(user?.roles?.includes('PARENT_GUARDIAN') && !user?.roles?.some(r => r === 'SUPER_ADMIN' || r === 'ADMIN' || r === 'TEACHER'));
  const student = useQuery({ queryKey: ['student', studentId], queryFn: ({ signal }) => studentApi.get(studentId, signal) });
  const documents = useQuery({ queryKey: ['student-documents', studentId], queryFn: ({ signal }) => studentApi.documents(studentId, signal) });
  const history = useQuery({ queryKey: ['student-history', studentId], queryFn: ({ signal }) => studentApi.history(studentId, signal) });
  const [guardianOpen, setGuardianOpen] = useState(false); const [editingGuardian, setEditingGuardian] = useState<number | null>(null);
  const [guardian, setGuardian] = useState({ name: '', phone: '', email: '', address: '', occupation: '', relation: '', primary: false });
  const [customRelation, setCustomRelation] = useState('');
  const [file, setFile] = useState<File | null>(null); const [docType, setDocType] = useState('BIRTH_CERT'); const [reason, setReason] = useState(''); const [destination, setDestination] = useState(''); const [error, setError] = useState('');
  const refresh = async () => { await Promise.all([queryClient.invalidateQueries({ queryKey: ['student', studentId] }), queryClient.invalidateQueries({ queryKey: ['student-documents', studentId] }), queryClient.invalidateQueries({ queryKey: ['student-history', studentId] }), queryClient.invalidateQueries({ queryKey: ['students'] })]); };
  const guardianPhoneDigits = extract10Digits(guardian.phone);
  const guardianPhoneError = guardianPhoneDigits.length > 0 && guardianPhoneDigits.length < 10 ? 'Mobile number must be exactly 10 digits' : undefined;
  const saveGuardian = useMutation({
    mutationFn: () => {
      const chosenRelation = guardian.relation === 'Other' && customRelation.trim()
        ? customRelation.trim()
        : (guardian.relation || 'Guardian');
      const payload = sanitizePayload({
        ...guardian,
        relation: chosenRelation,
        phone: guardianPhoneDigits ? `+91 ${guardianPhoneDigits}` : undefined,
      });
      return editingGuardian ? studentApi.updateGuardian(studentId, editingGuardian, payload) : studentApi.addGuardian(studentId, payload);
    },
    onSuccess: async () => {
      setGuardianOpen(false);
      setEditingGuardian(null);
      setGuardian({ name: '', phone: '', email: '', address: '', occupation: '', relation: '', primary: false });
      setCustomRelation('');
      await refresh();
    },
    onError: e => setError((e as Error).message)
  });
  const upload = useMutation({ mutationFn: () => file ? studentApi.uploadDocument(studentId, docType, file) : Promise.reject(new Error('Choose a document first')), onSuccess: async () => { setFile(null); await refresh(); }, onError: e => setError((e as Error).message) });
  const transfer = useMutation({ mutationFn: () => studentApi.transfer(studentId, { destinationSchool: destination, reason }), onSuccess: refresh, onError: e => setError((e as Error).message) });
  const exit = useMutation({ mutationFn: () => studentApi.exit(studentId, { reason }), onSuccess: refresh, onError: e => setError((e as Error).message) });
  if (student.isPending) return <div className="auth-loading" role="status">Loading student…</div>;
  if (student.isError) return <ErrorState title="Student couldn’t be loaded" description={(student.error as Error).message} onRetry={() => void student.refetch()} />;
  const record = student.data;
  const canEdit = hasPermission('STUDENT:UPDATE');
  return <div className="workspace-page student-page">
    <PageHeader eyebrow={isParent ? "CHILD PROFILE" : "STUDENT PROFILE"} title={`${record.firstName} ${record.lastName ?? ''}`.trim()} description={`Admission no. ${record.admissionNo}`} actions={<><StatusBadge status={record.status} />{canEdit && record.status === 'ACTIVE' && <Link className="ds-button ds-button--secondary" to={`/app/students/${studentId}/edit`}>Edit profile</Link>}</>} />
    {error && <Alert tone="danger" title="Action failed">{error}</Alert>}
    <div className="student-detail-grid"><Card className="student-profile-card"><SectionTitle title="Student information" /><dl className="student-facts">{facts(record).map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value || '—'}</dd></div>)}</dl></Card>
      <Card className="student-profile-card"><div className="student-section-head"><SectionTitle title="Guardians" /><Button variant="secondary" size="sm" disabled={!canEdit} onClick={() => { setGuardianOpen(value => !value); setEditingGuardian(null); setGuardian({ name: '', phone: '', email: '', address: '', occupation: '', relation: '', primary: false }); setCustomRelation(''); }}>{guardianOpen ? 'Cancel' : 'Add guardian'}</Button></div>
        {guardianOpen && <form className="student-inline-form" onSubmit={event => { event.preventDefault(); saveGuardian.mutate(); }}>
          <FormField label="Name" required><Input value={guardian.name} onChange={e => setGuardian(v => ({ ...v, name: e.target.value }))} required maxLength={150} /></FormField>
          <FormField label="Relationship" required>
            <Select aria-label="Relationship" value={guardian.relation} onChange={e => setGuardian(v => ({ ...v, relation: e.target.value }))} required>
              <option value="">Select relationship</option>
              {GUARDIAN_RELATIONS.map(opt => <option key={opt} value={opt}>{opt}</option>)}
              {guardian.relation && !GUARDIAN_RELATIONS.includes(guardian.relation) && <option value={guardian.relation}>{guardian.relation}</option>}
            </Select>
          </FormField>
          {guardian.relation === 'Other' && <FormField label="Specify relationship" className="student-form-wide" required><Input placeholder="e.g. Legal guardian, Foster parent" value={customRelation} onChange={e => setCustomRelation(e.target.value)} required maxLength={30} /></FormField>}
          <FormField label="Phone" hint="10-digit Indian mobile number" error={guardianPhoneError}><IndianPhoneInput value={guardian.phone} onChange={val => setGuardian(v => ({ ...v, phone: val }))} /></FormField>
          <FormField label="Email"><Input type="email" value={guardian.email} onChange={e => setGuardian(v => ({ ...v, email: e.target.value }))} maxLength={254} /></FormField>
          <label className="student-primary-check"><input type="checkbox" checked={guardian.primary} onChange={e => setGuardian(v => ({ ...v, primary: e.target.checked }))} /> Primary guardian</label>
          <Button size="sm" disabled={saveGuardian.isPending || Boolean(guardianPhoneError)}>{saveGuardian.isPending ? 'Saving…' : 'Save guardian'}</Button>
        </form>}
        {record.guardians.length ? <div className="student-guardian-list">{record.guardians.map((item: Guardian) => <div className="student-guardian" key={item.id}><div><strong>{item.name}</strong><span>{item.relation}{item.primary ? ' · Primary' : ''}</span><small>{item.phone ? formatDisplayPhone(item.phone) : item.email || 'No contact details'}</small></div>{canEdit && <div className="student-guardian-actions"><Button variant="ghost" size="sm" onClick={() => { setGuardianOpen(true); setEditingGuardian(item.id); const isStandard = GUARDIAN_RELATIONS.includes(item.relation); setGuardian({ name: item.name, phone: item.phone ?? '', email: item.email ?? '', address: item.address ?? '', occupation: item.occupation ?? '', relation: isStandard ? item.relation : (item.relation ? 'Other' : ''), primary: item.primary }); setCustomRelation(isStandard ? '' : item.relation); }}>Edit</Button><Button variant="ghost" size="sm" onClick={() => { if (confirm(`Remove ${item.name} from this student?`)) void studentApi.removeGuardian(studentId, item.id).then(refresh).catch(e => setError((e as Error).message)); }}>Remove</Button></div>}</div>)}</div> : <EmptyState title="No guardians linked" compact />}</Card>
    </div>
    <DocumentPanel studentId={studentId} documents={documents.data ?? []} loading={documents.isPending} loadError={documents.isError ? (documents.error as Error).message : undefined} canEdit={canEdit} file={file} setFile={setFile} docType={docType} setDocType={setDocType} uploading={upload.isPending} onUpload={() => upload.mutate()} onRetry={() => void documents.refetch()} />
    <Card><SectionTitle title="Student history" />{history.isPending ? <div className="student-loading">Loading history…</div> : history.isError ? <ErrorState title="History couldn’t be loaded" onRetry={() => void history.refetch()} /> : (history.data?.length ?? 0) ? <HistoryList items={history.data ?? []} /> : <EmptyState title="No history recorded" compact />}</Card>
    {canEdit && record.status === 'ACTIVE' && <Card><SectionTitle title="Transfer or exit" /><p className="student-muted">These actions preserve the student’s previous status and reason in their history.</p><div className="student-lifecycle-actions"><form onSubmit={event => { event.preventDefault(); transfer.mutate(); }}><FormField label="Destination school" required><Input value={destination} onChange={e => setDestination(e.target.value)} required /></FormField><FormField label="Transfer reason" required><Input value={reason} onChange={e => setReason(e.target.value)} required /></FormField><Button variant="secondary" disabled={transfer.isPending}>Record transfer</Button></form><form onSubmit={event => { event.preventDefault(); if (confirm('Mark this student as exited?')) exit.mutate(); }}><FormField label="Exit reason" required><Input value={reason} onChange={e => setReason(e.target.value)} required /></FormField><Button variant="danger" disabled={exit.isPending}>Record exit</Button></form></div></Card>}
  </div>;
}

function DocumentPanel({ studentId, documents, loading, loadError, canEdit, file, setFile, docType, setDocType, uploading, onUpload, onRetry }: { studentId: number; documents: StudentDocument[]; loading: boolean; loadError?: string; canEdit: boolean; file: File | null; setFile: (file: File | null) => void; docType: string; setDocType: (type: string) => void; uploading: boolean; onUpload: () => void; onRetry: () => void }) {
  const [error, setError] = useState('');
  async function download(document: StudentDocument) { try { const response = await studentApi.downloadDocument(studentId, document.id); if (!response.ok) { setError('Document download failed.'); return; } const blob = await response.blob(); const url = URL.createObjectURL(blob); const anchor = documentAnchor(url, document.fileName); window.document.body.appendChild(anchor); anchor.click(); anchor.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 1000); } catch { setError('Document download failed.'); } }
  return <Card><div className="student-section-head"><SectionTitle title="Documents" /><span className="student-muted">PDF, JPEG or PNG · max 10 MB</span></div>
    {canEdit && <div className="student-document-upload"><Select aria-label="Document type" value={docType} onChange={e => setDocType(e.target.value)}><option value="BIRTH_CERT">Birth certificate</option><option value="TRANSFER_CERT">Transfer certificate</option><option value="MARKSHEET">Marksheet</option><option value="PHOTO">Photo</option><option value="OTHER">Other</option></Select><input aria-label="Choose document file" type="file" accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" onChange={e => setFile(e.target.files?.[0] ?? null)} /><Button variant="secondary" disabled={!file || uploading} onClick={onUpload}>{uploading ? 'Uploading…' : 'Upload document'}</Button></div>}
    {error && <Alert tone="danger">{error}</Alert>}
    {loading ? <div className="student-loading" role="status">Loading documents…</div> : loadError ? <ErrorState title="Documents couldn’t be loaded" description={loadError} onRetry={onRetry} /> : documents.length ? <div className="student-document-list">{documents.map(doc => <div className="student-document-row" key={doc.id}><div><strong>{doc.fileName}</strong><small>{doc.docType.replaceAll('_', ' ')} · {formatSize(doc.fileSizeBytes)} · {new Date(doc.uploadedAt).toLocaleDateString()}</small></div><Button variant="ghost" size="sm" onClick={() => void download(doc)}>Download</Button></div>)}</div> : <EmptyState title="No documents uploaded" description="At least one supporting document is required before an admission draft can be submitted." compact />}
    {!documents.length && !loading && <Button variant="ghost" size="sm" onClick={onRetry}>Refresh documents</Button>}
  </Card>;
}
function documentAnchor(url: string, fileName: string) { const anchor = window.document.createElement('a'); anchor.href = url; anchor.download = fileName; return anchor; }
function formatSize(size: number) { return size < 1024 * 1024 ? `${Math.ceil(size / 1024)} KB` : `${(size / 1024 / 1024).toFixed(1)} MB`; }
function facts(student: Student) { return [['Date of birth', student.dateOfBirth], ['Gender', student.gender], ['Blood group', student.bloodGroup], ['Category', student.category], ['Religion', student.religion], ['Phone', student.phone ? formatDisplayPhone(student.phone) : '—'], ['Email', student.email], ['Address', student.address], ['Admission date', student.admissionDate], ['Exit date', student.exitDate], ['Exit reason', student.exitReason]]; }
function SectionTitle({ title }: { title: string }) { return <h2 className="student-section-title">{title}</h2>; }
function HistoryList({ items }: { items: StudentHistory[] }) { return <ol className="student-history-list">{items.map(item => <li key={item.id}><span className="student-history-dot" /><div><strong>{item.eventType.replaceAll('_', ' ')}</strong><small>{item.note || [item.previousValue, item.newValue].filter(Boolean).join(' → ') || 'Student record updated'}</small></div><time dateTime={item.changedAt}>{new Date(item.changedAt).toLocaleString()}</time></li>)}</ol>; }

export function AdmissionListPage() {
  const [filters, setFilters] = useState({ status: '', search: '', page: 0, size: 20 }); const { hasPermission } = useAuth();
  const admissions = useQuery({ queryKey: ['admissions', filters], queryFn: ({ signal }) => studentApi.admissions(filters, signal) });
  const columns = [
    { key: 'applicationNo', header: 'Application no.', render: (row: Admission) => <Link className="student-table-link" to={`/app/admissions/${row.id}`}>{row.applicationNo}</Link> },
    { key: 'applicantName', header: 'Applicant' },
    { key: 'appliedOn', header: 'Applied on', render: (row: Admission) => new Date(`${row.appliedOn}T00:00:00`).toLocaleDateString() },
    { key: 'status', header: 'Status', render: (row: Admission) => <StatusBadge status={row.status} /> },
  ];
  return <div className="workspace-page student-page"><PageHeader eyebrow="STUDENT LIFECYCLE" title="Admissions" description="Review applications and follow their admission state." actions={hasPermission('ADMISSION:CREATE') ? <Link className="ds-button ds-button--primary" to="/app/admissions/new">New application <span aria-hidden="true">＋</span></Link> : undefined} />
    <Card><div className="student-filter-form"><FormField label="Search applications"><Input placeholder="Application no. or applicant" value={filters.search} onChange={e => setFilters(v => ({ ...v, search: e.target.value, page: 0 }))} /></FormField><FormField label="Status"><Select value={filters.status} onChange={e => setFilters(v => ({ ...v, status: e.target.value, page: 0 }))}><option value="">All statuses</option>{['DRAFT','PENDING','UNDER_REVIEW','WAITLISTED','APPROVED','REJECTED'].map(status => <option key={status}>{status}</option>)}</Select></FormField></div></Card>
    {admissions.isPending ? <Card role="status">Loading admissions…</Card> : admissions.isError ? <ErrorState title="Admissions couldn’t be loaded" description={(admissions.error as Error).message} onRetry={() => void admissions.refetch()} /> : <Card className="student-list-card">{admissions.data?.content.length ? <DataTable columns={columns} rows={admissions.data.content} rowKey={row => String(row.id)} caption="Admissions" /> : <EmptyState title="No applications found" description="No applications match the selected search and status." />}{admissions.data && <Pagination page={admissions.data.page + 1} pageCount={Math.max(1, admissions.data.totalPages)} onPageChange={page => setFilters(v => ({ ...v, page: page - 1 }))} />}</Card>}
  </div>;
}

type AdmissionForm = { applicationNo: string } & StudentFields;
export function AdmissionCreatePage() {
  const navigate = useNavigate(); const [form, setForm] = useState<AdmissionForm>({ ...initialStudent, applicationNo: '' }); const [failure, setFailure] = useState<ApiFailure | null>(null);
  const phoneDigits = extract10Digits(form.phone);
  const validationPhoneError = phoneDigits.length > 0 && phoneDigits.length < 10 ? 'Mobile number must be exactly 10 digits' : undefined;
  const create = useMutation({
    mutationFn: () => {
      const studentData = {
        ...form,
        phone: phoneDigits ? `+91 ${phoneDigits}` : undefined,
      };
      const payload = {
        applicationNo: form.applicationNo,
        student: sanitizePayload(Object.fromEntries(Object.entries(studentData).filter(([key]) => key !== 'applicationNo')) as Record<string, unknown>),
      };
      return studentApi.createAdmission(payload);
    },
    onSuccess: admission => navigate(`/app/admissions/${admission.id}`),
    onError: e => setFailure(e as ApiFailure),
  });
  const set = (key: keyof AdmissionForm, value: string) => { setFailure(null); setForm(current => ({ ...current, [key]: value })); };
  const fieldError = (name: string) => failure?.fieldErrors?.find(item => item.field === name)?.message;
  return <div className="workspace-page student-page"><PageHeader eyebrow="ADMISSION WORKFLOW" title="New application" description="Create an applicant draft, add guardians and upload required documents, then submit for review." />{failure && <Alert tone="danger" title="Couldn’t create application">{failure.message}{failure.traceId && failure.status === 500 && <p>Reference: {failure.traceId}</p>}{failure.fieldErrors?.map(item => <p key={`${item.field}-${item.message}`}>{item.field}: {item.message}</p>)}</Alert>}
    <Card><form className="student-form-grid" onSubmit={event => { event.preventDefault(); setFailure(null); create.mutate(); }}><FormField label="Application number" required error={fieldError('applicationNo')}><Input value={form.applicationNo} onChange={e => set('applicationNo', e.target.value)} required maxLength={50} /></FormField><FormField label="Student admission number" required error={fieldError('admissionNo')}><Input value={form.admissionNo} onChange={e => set('admissionNo', e.target.value)} required maxLength={50} /></FormField><FormField label="First name" required error={fieldError('student.firstName') ?? fieldError('firstName')}><Input value={form.firstName} onChange={e => set('firstName', e.target.value)} required /></FormField><FormField label="Last name"><Input value={form.lastName} onChange={e => set('lastName', e.target.value)} /></FormField><FormField label="Date of birth"><Input type="date" value={form.dateOfBirth} onChange={e => set('dateOfBirth', e.target.value)} /></FormField><FormField label="Gender"><Select value={form.gender} onChange={e => set('gender', e.target.value)}><option value="">Select gender</option><option value="FEMALE">Female</option><option value="MALE">Male</option><option value="OTHER">Other</option></Select></FormField><FormField label="Phone" hint="10-digit Indian mobile number" error={fieldError('student.phone') ?? fieldError('phone') ?? validationPhoneError}><IndianPhoneInput value={form.phone} onChange={val => set('phone', val)} /></FormField><FormField label="Email" error={fieldError('student.email') ?? fieldError('email')}><Input type="email" value={form.email} onChange={e => set('email', e.target.value)} /></FormField><div className="student-form-actions"><Button variant="secondary" type="button" onClick={() => navigate('/app/admissions')}>Cancel</Button><Button type="submit" disabled={create.isPending || Boolean(validationPhoneError)}>{create.isPending ? 'Creating…' : 'Create draft'}</Button></div></form></Card>
  </div>;
}

export function AdmissionDetailPage() {
  const { id } = useParams(); const admissionId = Number(id); const client = useQueryClient(); const { hasPermission } = useAuth(); const canDecide = hasPermission('ADMISSION:UPDATE');
  const admission = useQuery({ queryKey: ['admission', admissionId], queryFn: ({ signal }) => studentApi.admission(admissionId, signal) });
  const [note, setNote] = useState(''); const [nextStatus, setNextStatus] = useState('UNDER_REVIEW'); const [error, setError] = useState('');
  const [docType, setDocType] = useState('BIRTH_CERT');
  const student = useQuery({ queryKey: ['student', admission.data?.studentId], queryFn: ({ signal }) => studentApi.get(admission.data!.studentId, signal), enabled: Boolean(admission.data) });
  const documents = useQuery({ queryKey: ['student-documents', admission.data?.studentId], queryFn: ({ signal }) => studentApi.documents(admission.data!.studentId, signal), enabled: Boolean(admission.data) });
  const [file, setFile] = useState<File | null>(null);
  const refresh = async () => { await Promise.all([client.invalidateQueries({ queryKey: ['admission', admissionId] }), client.invalidateQueries({ queryKey: ['admissions'] }), client.invalidateQueries({ queryKey: ['student', admission.data?.studentId] }), client.invalidateQueries({ queryKey: ['student-documents', admission.data?.studentId] }), client.invalidateQueries({ queryKey: ['student-history', admission.data?.studentId] })]); };
  const upload = useMutation({ mutationFn: () => file && admission.data ? studentApi.uploadDocument(admission.data.studentId, docType, file) : Promise.reject(new Error('Choose a document file first')), onSuccess: async () => { setFile(null); setError(''); await refresh(); }, onError: e => setError((e as Error).message) });
  const submit = useMutation({ mutationFn: () => studentApi.submitAdmission(admissionId), onSuccess: async () => { setError(''); await refresh(); }, onError: e => setError((e as Error).message) });
  const decide = useMutation({ mutationFn: (status: string) => studentApi.decideAdmission(admissionId, status, note), onSuccess: async () => { setError(''); await refresh(); }, onError: e => setError((e as Error).message) });
  if (admission.isPending) return <div className="auth-loading" role="status">Loading application…</div>;
  if (admission.isError) return <ErrorState title="Application couldn’t be loaded" description={(admission.error as Error).message} onRetry={() => void admission.refetch()} />;
  const app = admission.data!;
  const allowed = app.status === 'DRAFT' ? ['PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'WAITLISTED'] : app.status === 'PENDING' ? ['UNDER_REVIEW','APPROVED','REJECTED','WAITLISTED'] : app.status === 'UNDER_REVIEW' ? ['APPROVED','REJECTED','WAITLISTED'] : app.status === 'WAITLISTED' ? ['PENDING','APPROVED','REJECTED'] : [];
  const selectedNextStatus = allowed.includes(nextStatus) ? nextStatus : allowed[0] ?? '';
  return <div className="workspace-page student-page"><PageHeader eyebrow="ADMISSION WORKFLOW" title={app.applicantName} description={`Application ${app.applicationNo} · applied ${new Date(`${app.appliedOn}T00:00:00`).toLocaleDateString()}`} actions={<StatusBadge status={app.status} />} />
    {error && <Alert tone="danger" title="Admission action failed">{error}</Alert>}
    <Card><div className="student-section-head"><SectionTitle title="Applicant profile" />{hasPermission('STUDENT:READ') && <Link className="ds-button ds-button--secondary" to={`/app/students/${app.studentId}`}>Open student profile</Link>}</div>{student.data && <dl className="student-facts"><div><dt>Admission no.</dt><dd>{student.data.admissionNo}</dd></div><div><dt>Phone</dt><dd>{student.data.phone ? formatDisplayPhone(student.data.phone) : '—'}</dd></div><div><dt>Email</dt><dd>{student.data.email || '—'}</dd></div></dl>}</Card>
    {student.isError && <ErrorState title="Applicant profile couldn’t be loaded" description={(student.error as Error).message} onRetry={() => void student.refetch()} />}
    {student.data && <><DocumentPanel studentId={app.studentId} documents={documents.data ?? []} loading={documents.isPending} loadError={documents.isError ? (documents.error as Error).message : undefined} canEdit={hasPermission('STUDENT:UPDATE') || hasPermission('ADMISSION:UPDATE')} file={file} setFile={setFile} docType={docType} setDocType={setDocType} uploading={upload.isPending} onUpload={() => upload.mutate()} onRetry={() => void documents.refetch()} />
      <Card><SectionTitle title="Submit application" /><p className="student-muted">At least one supporting document must be uploaded before submission. Missing required documents block submission.</p><Button disabled={!hasPermission('ADMISSION:CREATE') || app.status !== 'DRAFT' || submit.isPending} onClick={() => submit.mutate()}>{submit.isPending ? 'Submitting…' : 'Submit for review'}</Button></Card></>}
    {canDecide && allowed.length > 0 && <Card><SectionTitle title="Admission decision" /><form className="student-decision-form" onSubmit={event => { event.preventDefault(); decide.mutate(selectedNextStatus); }}><FormField label="Next status"><Select value={selectedNextStatus} onChange={e => setNextStatus(e.target.value)}>{allowed.map(status => <option key={status}>{status}</option>)}</Select></FormField><FormField label="Decision note"><Input value={note} onChange={e => setNote(e.target.value)} maxLength={1000} /></FormField><Button disabled={decide.isPending}>{decide.isPending ? 'Saving…' : 'Update status'}</Button></form></Card>}
  </div>;
}
