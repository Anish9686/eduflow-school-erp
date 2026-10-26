import { useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, ConfirmDialog, DataTable, DatePicker, EmptyState, ErrorState, FormField, Input, PageHeader, Select, StatusBadge, Textarea } from '../components';
import { useAuth } from '../auth/AuthProvider';
import { financeApi } from '../api/finance';
import type { FeeDemand, FeeStructure, Payment, Discount, Expense } from '../api/finance';
import './finance-pages.css';

type Draft = Record<string, string>;
type Confirmation = { title: string; message: string; action: () => void };
const today = new Date().toISOString().slice(0, 10);
const monthStart = `${today.slice(0, 7)}-01`;
const money = (value: string | number | null | undefined) => {
  if (value == null || value === '') return '—';
  try {
    const isNegative = String(value).startsWith('-');
    const [whole = '0', fraction = ''] = String(value).replace('-', '').split('.');
    const formatted = new Intl.NumberFormat('en-IN').format(BigInt(whole || '0'));
    return `${isNegative ? '-' : ''}₹${formatted}.${fraction.padEnd(2, '0').slice(0, 2)}`;
  } catch {
    const num = Number(value);
    return Number.isNaN(num) ? '—' : `₹${num.toFixed(2)}`;
  }
};
const positiveMoney = (value: string | number | null | undefined) => {
  if (value == null) return false;
  return Number(value) > 0;
};

export function FinancePage() {
  const auth = useAuth();
  const can = (permission: string) => auth.hasPermission(permission);
  const canSeeStructures = can('FEE:READ') || can('FEE:MANAGE');
  const canSeeDemands = can('FEE:READ') || can('FEE:MANAGE') || can('FEE:COLLECT') || can('FEE_DISCOUNT:REQUEST');
  const canCollect = can('FEE:COLLECT');
  const canRefund = can('FEE:REFUND');
  const canDiscount = can('FEE_DISCOUNT:REQUEST') || can('FEE_DISCOUNT:APPROVE');
  const canExpense = can('EXPENSE:MANAGE') || can('EXPENSE:APPROVE');
  const canReport = can('FINANCE_REPORT:READ');
  const canExport = can('FINANCE_REPORT:EXPORT');
  const permissions = [canSeeStructures, canSeeDemands, canCollect, canRefund, canDiscount, canExpense, canReport, canExport];
  const initialTab = canSeeStructures ? 'structures' : canCollect ? 'payment' : canRefund ? 'collection' : canDiscount ? 'discounts' : canSeeDemands ? 'demands' : canExpense ? 'expenses' : 'reports';
  const [tab, setTab] = useState(initialTab);
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [error, setError] = useState('');
  const [dates, setDates] = useState({ from: monthStart, to: today });
  const [receiptId, setReceiptId] = useState('');
  const [structure, setStructure] = useState<Draft>({ sessionId: '', classId: '', categoryId: '', amount: '', dueDate: '', effectiveFrom: today, effectiveTo: '', frequency: 'ONE_TIME' });
  const [demand, setDemand] = useState<Draft>({ studentId: '', feeStructureId: '', billingPeriod: today });
  const [payment, setPayment] = useState<Draft>({ demandId: '', referenceNo: '', amount: '', paymentMethod: 'CASH', paymentDate: today, remarks: '' });
  const [discount, setDiscount] = useState<Draft>({ demandId: '', amount: '', reason: '' });
  const [expense, setExpense] = useState<Draft>({ category: '', description: '', amount: '', expenseDate: today, vendor: '', receiptUrl: '' });
  const queryClient = useQueryClient();
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['finance'] });
  const options = useQuery({ queryKey: ['finance', 'options'], queryFn: financeApi.options });
  const structures = useQuery({ queryKey: ['finance', 'structures'], queryFn: financeApi.structures, enabled: canSeeStructures || can('FEE:MANAGE') || canCollect });
  const demands = useQuery({ queryKey: ['finance', 'demands'], queryFn: financeApi.demands, enabled: canSeeDemands });
  const payments = useQuery({ queryKey: ['finance', 'payments', dates], queryFn: () => financeApi.payments(dates.from, dates.to), enabled: canCollect || canRefund || can('FEE:READ') });
  const receipt = useQuery({ queryKey: ['finance', 'receipt', receiptId], queryFn: () => financeApi.receipt(Number(receiptId)), enabled: Boolean(receiptId) && (canCollect || canRefund || can('FEE:READ')) });
  const discounts = useQuery({ queryKey: ['finance', 'discounts'], queryFn: financeApi.discounts, enabled: canDiscount });
  const expenses = useQuery({ queryKey: ['finance', 'expenses'], queryFn: financeApi.expenses, enabled: canExpense });
  const summary = useQuery({ queryKey: ['finance', 'summary', dates], queryFn: () => financeApi.summary(dates.from, dates.to), enabled: canReport });
  const reconciliation = useQuery({ queryKey: ['finance', 'reconciliation'], queryFn: financeApi.reconciliation, enabled: canReport && tab === 'reports' });

  const run = (action: () => void, title: string, message: string) => { setError(''); setConfirmation({ action, title, message }); };
  const onError = (cause: Error) => setError(cause.message);
  const createStructure = useMutation({ mutationFn: financeApi.createStructure, onSuccess: () => { void refresh(); setStructure({ sessionId: '', classId: '', categoryId: '', amount: '', dueDate: '', effectiveFrom: today, effectiveTo: '', frequency: 'ONE_TIME' }); }, onError });
  const generateDemand = useMutation({ mutationFn: financeApi.generateDemand, onSuccess: () => { void refresh(); setDemand(v => ({ ...v, studentId: '' })); setError(''); }, onError });
  const deleteDemand = useMutation({ mutationFn: financeApi.deleteDemand, onSuccess: () => { void refresh(); setError(''); }, onError });
  const postPayment = useMutation({ mutationFn: financeApi.postPayment, onSuccess: (value: Payment) => { void refresh(); setReceiptId(String(value.id)); setPayment(v => ({ ...v, referenceNo: '', amount: '', remarks: '' })); setTab('receipt'); }, onError });
  const adjustPayment = useMutation({ mutationFn: ({ id, operation, reason }: { id: number; operation: 'refund' | 'reverse'; reason: string }) => financeApi.adjustPayment(id, operation, reason), onSuccess: () => { void refresh(); }, onError });
  const requestDiscount = useMutation({ mutationFn: financeApi.requestDiscount, onSuccess: () => { void refresh(); setDiscount({ demandId: '', amount: '', reason: '' }); }, onError });
  const decideDiscount = useMutation({ mutationFn: ({ id, decision, reason }: { id: number; decision: 'approve' | 'reject'; reason: string }) => financeApi.decideDiscount(id, decision, reason), onSuccess: () => { void refresh(); }, onError });
  const createExpense = useMutation({ mutationFn: financeApi.createExpense, onSuccess: () => { void refresh(); setExpense({ category: '', description: '', amount: '', expenseDate: today, vendor: '', receiptUrl: '' }); }, onError });
  const submitExpense = useMutation({ mutationFn: financeApi.submitExpense, onSuccess: () => { void refresh(); }, onError });
  const decideExpense = useMutation({ mutationFn: ({ id, decision, reason }: { id: number; decision: 'approve' | 'reject'; reason: string }) => financeApi.decideExpense(id, decision, reason), onSuccess: () => { void refresh(); }, onError });

  const visibleTabs = [
    ...(canSeeStructures ? [{ id: 'structures', label: 'Fee Structures' }] : []),
    ...(canSeeDemands ? [{ id: 'demands', label: 'Fee Demands' }] : []),
    ...(canCollect || canRefund || can('FEE:READ') ? [{ id: 'collection', label: 'Collection' }, ...(canCollect ? [{ id: 'payment', label: 'Payment' }] : []), { id: 'receipt', label: 'Receipt' }] : []),
    ...(canDiscount ? [{ id: 'discounts', label: 'Discount Approval' }] : []),
    ...(canExpense ? [{ id: 'expenses', label: 'Expenses' }] : []),
    ...(canReport || canExport ? [{ id: 'reports', label: 'Finance Reports' }] : []),
  ];
  const tabIcons: Record<string, string> = {
    structures: '▤',
    demands: '◷',
    collection: '⇄',
    payment: '₹',
    receipt: '▧',
    discounts: '✓',
    expenses: '↗',
    reports: '▥',
  };
  const selectedDemand = (id: string) => (demands.data ?? []).find(row => row.id === Number(id));
  const selectedStructure = (structures.data ?? []).find(item => item.id === Number(demand.feeStructureId));
  const classStudents = (options.data?.students ?? []).filter(row => {
    return !selectedStructure || (Number(row.sessionId) === Number(selectedStructure.sessionId) && Number(row.classId) === Number(selectedStructure.classId));
  });
  const getExistingDemand = (studentId: number) => {
    if (!selectedStructure) return null;
    return (demands.data ?? []).find(d => {
      if (d.studentId !== studentId || d.feeStructureId !== selectedStructure.id) return false;
      if (selectedStructure.frequency === 'ONE_TIME') return true;
      if (!demand.billingPeriod || !d.billingPeriod) return false;
      if (selectedStructure.frequency === 'MONTHLY') {
        return d.billingPeriod.slice(0, 7) === demand.billingPeriod.slice(0, 7);
      }
      return d.billingPeriod === demand.billingPeriod;
    });
  };
  const selectedStudentDemand = demand.studentId ? getExistingDemand(Number(demand.studentId)) : null;
  const allStudentsBilled = Boolean(selectedStructure && classStudents.length > 0 && classStudents.every(s => Boolean(getExistingDemand(s.id))));

  const demandColumns = [
    { key: 'id', header: 'Demand #', render: (row: FeeDemand) => `#${row.id}` },
    { key: 'studentName', header: 'Student' },
    { key: 'structure', header: 'Fee structure', render: (row: FeeDemand) => {
        const s = (structures.data ?? []).find(item => item.id === row.feeStructureId);
        return s ? `${s.className} · ${s.categoryName}` : `Structure #${row.feeStructureId}`;
      }
    },
    { key: 'billingPeriod', header: 'Period' },
    { key: 'amountDue', header: 'Demand', render: (row: FeeDemand) => money(row.amountDue) },
    { key: 'amountPaid', header: 'Paid', render: (row: FeeDemand) => money(row.amountPaid) },
    { key: 'discountAmount', header: 'Discount', render: (row: FeeDemand) => money(row.discountAmount) },
    { key: 'balance', header: 'Balance', render: (row: FeeDemand) => money(row.balance) },
    { key: 'status', header: 'Status', render: (row: FeeDemand) => <StatusBadge status={row.status} /> },
    { key: 'actions', header: 'Actions', render: (row: FeeDemand) => (
        <div className="finance-row-actions">
          {canCollect && positiveMoney(row.balance) && (
            <Button size="sm" variant="secondary" onClick={() => {
              setPayment(v => ({
                ...v,
                demandId: String(row.id),
                amount: String(row.balance),
                referenceNo: v.referenceNo || `PAY-${Date.now().toString().slice(-6)}`
              }));
              if (tab !== 'payment') setTab('payment');
            }}>{tab === 'payment' ? 'Select' : 'Collect'}</Button>
          )}
          {can('FEE:MANAGE') && row.status === 'PENDING' && (row.amountPaid === '0.00' || Number(row.amountPaid) === 0) && (row.discountAmount === '0.00' || Number(row.discountAmount) === 0) ? (
            <Button size="sm" variant="danger" onClick={() => run(() => deleteDemand.mutate(row.id), 'Cancel fee demand', `Delete fee demand #${row.id} for ${row.studentName}? This will cancel the pending demand and allow generating a new demand if needed.`)}>Cancel</Button>
          ) : null}
        </div>
      )
    },
  ];
  const paymentColumns = [
    { key: 'id', header: 'Payment ID', render: (row: Payment) => `#${row.id}` },
    { key: 'paymentDate', header: 'Date' }, { key: 'receiptNo', header: 'Receipt' }, { key: 'referenceNo', header: 'Reference' },
    { key: 'studentId', header: 'Student ID' }, { key: 'amount', header: 'Amount', render: (row: Payment) => money(row.amount) },
    { key: 'paymentMethod', header: 'Method' }, { key: 'status', header: 'Status', render: (row: Payment) => <StatusBadge status={row.status} /> },
    { key: 'actions', header: 'Actions', render: (row: Payment) => <div className="finance-row-actions"><Button size="sm" variant="ghost" onClick={() => { setReceiptId(String(row.id)); setTab('receipt'); }}>Receipt</Button>{can('FEE:REFUND') && row.status === 'POSTED' && <><Button size="sm" variant="secondary" onClick={() => run(() => adjustPayment.mutate({ id: row.id, operation: 'refund', reason: 'Approved refund' }), 'Refund payment', `Refund ${money(row.amount)} against receipt ${row.receiptNo}?`)}>Refund</Button><Button size="sm" variant="danger" onClick={() => run(() => adjustPayment.mutate({ id: row.id, operation: 'reverse', reason: 'Payment reversal' }), 'Reverse payment', `Reverse payment ${row.receiptNo}? The original record remains in the ledger.`)}>Reverse</Button></>}</div> },
  ];
  const feeStructureColumns = [
    { key: 'sessionId', header: 'Session' }, { key: 'className', header: 'Class' }, { key: 'categoryName', header: 'Category' },
    { key: 'amount', header: 'Amount', render: (row: FeeStructure) => money(row.amount) }, { key: 'frequency', header: 'Frequency' },
    { key: 'dueDate', header: 'Due date' }, { key: 'active', header: 'Status', render: (row: FeeStructure) => <StatusBadge status={row.active ? 'Active' : 'Inactive'} /> },
  ];
  const discountColumns = [
    { key: 'studentId', header: 'Student ID' }, { key: 'demandId', header: 'Demand' }, { key: 'amount', header: 'Amount', render: (row: Discount) => money(row.amount) },
    { key: 'reason', header: 'Reason' }, { key: 'approvalStatus', header: 'Status', render: (row: Discount) => <StatusBadge status={row.approvalStatus} /> },
    { key: 'actions', header: 'Actions', render: (row: Discount) => can('FEE_DISCOUNT:APPROVE') && row.approvalStatus === 'PENDING' ? <div className="finance-row-actions"><Button size="sm" onClick={() => run(() => decideDiscount.mutate({ id: row.id, decision: 'approve', reason: 'Approved' }), 'Approve discount', `Approve ${money(row.amount)} discount for demand ${row.demandId}?`)}>Approve</Button><Button size="sm" variant="danger" onClick={() => run(() => decideDiscount.mutate({ id: row.id, decision: 'reject', reason: 'Rejected' }), 'Reject discount', `Reject the discount request for demand ${row.demandId}?`)}>Reject</Button></div> : null },
  ];
  const expenseColumns = [
    { key: 'expenseDate', header: 'Date' }, { key: 'category', header: 'Category' }, { key: 'description', header: 'Description' },
    { key: 'amount', header: 'Amount', render: (row: Expense) => money(row.amount) }, { key: 'vendor', header: 'Vendor' },
    { key: 'approvalStatus', header: 'Status', render: (row: Expense) => <StatusBadge status={row.approvalStatus} /> },
    { key: 'actions', header: 'Actions', render: (row: Expense) => <div className="finance-row-actions">{can('EXPENSE:MANAGE') && row.approvalStatus === 'DRAFT' && <Button size="sm" onClick={() => run(() => submitExpense.mutate(row.id), 'Submit expense', `Submit ${money(row.amount)} for approval?`)}>Submit</Button>}{can('EXPENSE:APPROVE') && row.approvalStatus === 'PENDING' && <><Button size="sm" onClick={() => run(() => decideExpense.mutate({ id: row.id, decision: 'approve', reason: 'Approved' }), 'Approve expense', `Approve expense for ${money(row.amount)}?`)}>Approve</Button><Button size="sm" variant="danger" onClick={() => run(() => decideExpense.mutate({ id: row.id, decision: 'reject', reason: 'Rejected' }), 'Reject expense', `Reject expense for ${money(row.amount)}?`)}>Reject</Button></>}</div> },
  ];

  if (!permissions.some(Boolean)) return <section className="workspace-page"><PageHeader eyebrow="FINANCE" title="Finance" /><Card><EmptyState title="No Finance access" description="Your account does not have a Finance permission." /></Card></section>;
  if (options.isPending) return <section className="workspace-page"><PageHeader eyebrow="FINANCE" title="Finance" /><Card role="status">Loading Finance workspace…</Card></section>;
  if (options.isError) return <section className="workspace-page"><PageHeader eyebrow="FINANCE" title="Finance" /><ErrorState title="Finance options unavailable" description={options.error.message} onRetry={() => void options.refetch()} /></section>;

  return <section className="workspace-page finance-page">
    <PageHeader eyebrow="FINANCE OPERATIONS" title="Finance" description="Fee collection, approvals and transactional reporting." />
    {error && <Alert tone="danger" title="Finance operation failed">{error}</Alert>}
    <div className="ds-tabs" role="tablist" aria-label="Finance workflows">{visibleTabs.map(item => <button key={item.id} type="button" role="tab" id={`finance-tab-${item.id}`} aria-controls={`finance-panel-${item.id}`} aria-selected={tab === item.id} tabIndex={tab === item.id ? 0 : -1} onClick={() => setTab(item.id)}><span className="ds-tab-icon" aria-hidden="true">{tabIcons[item.id]}</span>{item.label}</button>)}</div>
    <div id={`finance-panel-${tab}`} role="tabpanel" aria-labelledby={`finance-tab-${tab}`} tabIndex={0}>

    {tab === 'structures' && canSeeStructures && <div className="finance-panel"><Card className="finance-form-card"><h2>New fee structure</h2><form className="finance-form" onSubmit={event => { event.preventDefault(); run(() => createStructure.mutate({ ...structure, sessionId: Number(structure.sessionId), classId: Number(structure.classId), categoryId: Number(structure.categoryId), amount: structure.amount, dueDate: structure.dueDate || null, effectiveTo: structure.effectiveTo || null }), 'Create fee structure', `Create ${money(structure.amount)} ${(structure.frequency ?? 'ONE_TIME').toLowerCase().replace('_', ' ')} fee for this class?`); }}>
      <FormField label="Session" required><Select required value={structure.sessionId} onChange={event => setStructure(v => ({ ...v, sessionId: event.target.value }))}><option value="">Choose session</option>{options.data.sessions.map(row => <option key={row.id} value={row.id}>{row.label}</option>)}</Select></FormField>
      <FormField label="Class" required><Select required value={structure.classId} onChange={event => setStructure(v => ({ ...v, classId: event.target.value }))}><option value="">Choose class</option>{options.data.classes.map(row => <option key={row.id} value={row.id}>{row.label}</option>)}</Select></FormField>
      <FormField label="Fee category" required hint={options.data.categories.length ? undefined : 'No fee categories are configured. Contact an administrator.'}><Select required disabled={!options.data.categories.length} value={structure.categoryId} onChange={event => setStructure(v => ({ ...v, categoryId: event.target.value }))}><option value="">{options.data.categories.length ? 'Choose category' : 'No categories available'}</option>{options.data.categories.map(row => <option key={row.id} value={row.id}>{row.label}</option>)}</Select></FormField>
      <FormField label="Amount" required><Input type="number" min="0" step="0.01" required value={structure.amount} onChange={event => setStructure(v => ({ ...v, amount: event.target.value }))} /></FormField>
      <FormField label="Frequency" required><Select value={structure.frequency} onChange={event => setStructure(v => ({ ...v, frequency: event.target.value }))}>{['ONE_TIME', 'MONTHLY', 'QUARTERLY', 'ANNUALLY'].map(value => <option key={value}>{value}</option>)}</Select></FormField>
      <FormField label="Due date"><DatePicker value={structure.dueDate} onChange={event => setStructure(v => ({ ...v, dueDate: event.target.value }))} /></FormField>
      <FormField label="Effective from" required><DatePicker required value={structure.effectiveFrom} onChange={event => setStructure(v => ({ ...v, effectiveFrom: event.target.value }))} /></FormField>
      <FormField label="Effective to"><DatePicker value={structure.effectiveTo} onChange={event => setStructure(v => ({ ...v, effectiveTo: event.target.value }))} /></FormField>
      <div className="finance-form-actions"><Button disabled={createStructure.isPending}>Create structure</Button></div>
    </form></Card><FinanceTable query={structures} caption="Fee structures" columns={feeStructureColumns} rows={structures.data ?? []} /></div>}

    {tab === 'demands' && canSeeDemands && <div className="finance-panel">
      {can('FEE:MANAGE') && <Card className="finance-form-card">
        <h2>Generate fee demand</h2>
        <form className="finance-form" onSubmit={event => { event.preventDefault(); run(() => generateDemand.mutate({ studentId: Number(demand.studentId), feeStructureId: Number(demand.feeStructureId), billingPeriod: demand.billingPeriod }), 'Generate fee demand', 'Create a new demand for the selected student and billing period?'); }}>
          <FormField label="Fee structure" required>
            <Select required value={demand.feeStructureId} onChange={event => setDemand(v => ({ ...v, feeStructureId: event.target.value, studentId: '' }))}>
              <option value="">Choose structure</option>
              {(structures.data ?? []).map(row => <option key={row.id} value={row.id}>{row.className} · {row.categoryName} · {money(row.amount)} ({row.frequency.toLowerCase().replace('_', ' ')})</option>)}
            </Select>
          </FormField>
          <FormField label="Enrolled student" required>
            <Select required value={demand.studentId} onChange={event => setDemand(v => ({ ...v, studentId: event.target.value }))}>
              <option value="">Choose student</option>
              {classStudents.map(row => {
                const existing = getExistingDemand(row.id);
                return (
                  <option key={`${row.id}-${row.sessionId}`} value={row.id}>
                    {row.label} {existing ? `(Demand #${existing.id} exists · ${existing.status})` : ''}
                  </option>
                );
              })}
            </Select>
          </FormField>
          <FormField label="Billing period" required>
            <DatePicker required value={demand.billingPeriod} onChange={event => setDemand(v => ({ ...v, billingPeriod: event.target.value }))} />
          </FormField>
          {selectedStudentDemand && (
            <Alert tone="warning" title="Demand already exists">
              A {selectedStructure?.frequency === 'ONE_TIME' ? 'one-time' : ''} fee demand (#{selectedStudentDemand.id}) already exists for this student for {selectedStructure?.categoryName} (Demand: {money(selectedStudentDemand.amountDue)}, Status: {selectedStudentDemand.status}). You cannot generate a duplicate demand. To modify or regenerate, cancel the existing demand below first.
            </Alert>
          )}
          {allStudentsBilled && !demand.studentId && (
            <Alert tone="info" title="All students already billed">
              All {classStudents.length} enrolled student(s) in {selectedStructure?.className} have already had fee demands generated for this {selectedStructure?.frequency === 'ONE_TIME' ? 'one-time' : ''} fee structure ({selectedStructure?.categoryName}).
            </Alert>
          )}
          <div className="finance-form-actions">
            <Button disabled={generateDemand.isPending || !demand.feeStructureId || !demand.studentId || Boolean(selectedStudentDemand)}>
              Generate demand
            </Button>
          </div>
        </form>
      </Card>}
      <FinanceTable query={demands} caption="Fee demands" columns={demandColumns} rows={demands.data ?? []} />
    </div>}

    {tab === 'collection' && <div className="finance-panel"><Card className="finance-filter-card"><div className="finance-date-filters"><FormField label="From"><DatePicker value={dates.from} onChange={event => setDates(v => ({ ...v, from: event.target.value }))} /></FormField><FormField label="To"><DatePicker value={dates.to} onChange={event => setDates(v => ({ ...v, to: event.target.value }))} /></FormField></div></Card><FinanceTable query={payments} caption="Finance collections" columns={paymentColumns} rows={payments.data ?? []} /></div>}

    {tab === 'payment' && canCollect && <div className="finance-panel">
      <Card className="finance-form-card">
        <h2>Collect payment</h2>
        <form className="finance-form" onSubmit={event => { event.preventDefault(); const outstanding = selectedDemand(payment.demandId ?? '')?.balance ?? '0'; run(() => postPayment.mutate({ ...payment, demandId: Number(payment.demandId), amount: payment.amount }), 'Confirm payment', `Post ${money(payment.amount)} against demand ${payment.demandId} with outstanding balance ${money(outstanding)}? A receipt will be issued atomically.`); }}>
          <FormField label="Outstanding demand" required>
            <Select required value={payment.demandId} onChange={event => setPayment(v => ({ ...v, demandId: event.target.value }))}>
              <option value="">Choose demand</option>
              {(demands.data ?? []).filter(row => positiveMoney(row.balance)).map(row => <option key={row.id} value={row.id}>{row.studentName} · demand #{row.id} · balance {money(row.balance)}</option>)}
            </Select>
          </FormField>
          {(demands.data ?? []).filter(row => positiveMoney(row.balance)).length === 0 && (
            <Alert tone="info" title="No outstanding demands">
              There are currently no fee demands with an outstanding balance to collect. Generate a demand first from the Fee Demands tab.
            </Alert>
          )}
          <FormField label="Amount" required><Input type="number" min="0.01" step="0.01" required value={payment.amount} onChange={event => setPayment(v => ({ ...v, amount: event.target.value }))} /></FormField>
          <FormField label="Payment reference" required><Input required maxLength={40} value={payment.referenceNo} onChange={event => setPayment(v => ({ ...v, referenceNo: event.target.value }))} /></FormField>
          <FormField label="Method" required><Select value={payment.paymentMethod} onChange={event => setPayment(v => ({ ...v, paymentMethod: event.target.value }))}>{['CASH', 'UPI', 'CARD', 'CHEQUE', 'NEFT', 'RTGS', 'DD', 'OTHER'].map(value => <option key={value}>{value}</option>)}</Select></FormField>
          <FormField label="Payment date" required><DatePicker required value={payment.paymentDate} onChange={event => setPayment(v => ({ ...v, paymentDate: event.target.value }))} /></FormField>
          <FormField label="Remarks"><Input maxLength={500} value={payment.remarks} onChange={event => setPayment(v => ({ ...v, remarks: event.target.value }))} /></FormField>
          <div className="finance-form-actions"><Button disabled={postPayment.isPending || !payment.demandId}>Post payment & issue receipt</Button></div>
        </form>
      </Card>
      <FinanceTable query={demands} caption="Outstanding fee demands" columns={demandColumns} rows={(demands.data ?? []).filter(row => positiveMoney(row.balance))} />
    </div>}

    {tab === 'receipt' && <div className="finance-panel">
      <Card className="finance-form-card">
        <h2>Receipt lookup</h2>
        <div className="finance-inline-form">
          <FormField label="Payment ID">
            <Input type="number" min="1" placeholder="e.g. 1" value={receiptId} onChange={event => setReceiptId(event.target.value)} />
          </FormField>
          <Button variant="secondary" onClick={() => void receipt.refetch()} disabled={!receiptId}>Find receipt</Button>
          {receiptId && <Button variant="ghost" onClick={() => setReceiptId('')}>Clear</Button>}
        </div>
      </Card>
      {receipt.data && (() => {
        const student = (options.data?.students ?? []).find(s => s.id === receipt.data.studentId);
        const demandInfo = (demands.data ?? []).find(d => d.id === receipt.data.demandId);
        const structureInfo = demandInfo ? (structures.data ?? []).find(s => s.id === demandInfo.feeStructureId) : null;
        return (
          <Card className="finance-receipt">
            <div className="finance-receipt-header">
              <div className="finance-receipt-brand">
                <span className="brand-mark">E</span>
                <div>
                  <strong>EduFlow Portal</strong>
                  <p className="eyebrow">OFFICIAL PAYMENT RECEIPT</p>
                </div>
              </div>
              <div className="finance-receipt-number">
                <small>RECEIPT NO</small>
                <h2>{receipt.data.receiptNo}</h2>
              </div>
            </div>
            <dl>
              <dt>Payment ID</dt>
              <dd>#{receipt.data.id}</dd>
              <dt>Student</dt>
              <dd>{student ? `${student.label} (#${receipt.data.studentId})` : `Student #${receipt.data.studentId}`}</dd>
              <dt>Fee Demand</dt>
              <dd>{structureInfo ? `${structureInfo.className} · ${structureInfo.categoryName} (#${receipt.data.demandId})` : `Demand #${receipt.data.demandId}`}</dd>
              <dt>Payment Reference</dt>
              <dd>{receipt.data.referenceNo || 'N/A'}</dd>
              <dt>Payment Date</dt>
              <dd>{receipt.data.paymentDate}</dd>
              <dt>Payment Method</dt>
              <dd>{receipt.data.paymentMethod}</dd>
              <dt>Status</dt>
              <dd><StatusBadge status={receipt.data.status} /></dd>
              <dt>Amount Paid</dt>
              <dd style={{ fontSize: '18px', color: 'var(--accent, #10b981)', fontWeight: 700 }}>{money(receipt.data.amount)}</dd>
            </dl>
            <div className="finance-receipt-footer">
              <div className="finance-receipt-stamp">
                <p style={{ margin: '0 0 4px' }}>This is a computer-generated fee payment receipt issued by EduFlow.</p>
                <p style={{ margin: 0 }}>Issued at: {receipt.data.issuedAt ? new Date(receipt.data.issuedAt).toLocaleString() : receipt.data.paymentDate}</p>
              </div>
              <div className="finance-receipt-sig">
                <div className="sig-line" />
                <span>Authorized Signatory</span>
              </div>
            </div>
            <div className="finance-receipt-actions">
              <Button onClick={() => window.print()}>Print receipt</Button>
              <Button variant="secondary" onClick={() => setReceiptId('')}>Close receipt</Button>
            </div>
          </Card>
        );
      })()}
      {receipt.isError && <Alert tone="danger">{receipt.error.message}</Alert>}
      <FinanceTable query={payments} caption="Recent posted payments" columns={paymentColumns} rows={payments.data ?? []} />
    </div>}

    {tab === 'discounts' && canDiscount && <div className="finance-panel">{can('FEE_DISCOUNT:REQUEST') && <Card className="finance-form-card"><h2>Request discount</h2><form className="finance-form" onSubmit={event => { event.preventDefault(); run(() => requestDiscount.mutate({ demandId: Number(discount.demandId), amount: discount.amount, reason: discount.reason }), 'Submit discount request', `Submit a ${money(discount.amount)} discount request for approval?`); }}><FormField label="Fee demand" required><Select required value={discount.demandId} onChange={event => setDiscount(v => ({ ...v, demandId: event.target.value }))}><option value="">Choose demand</option>{(demands.data ?? []).filter(row => Number(row.balance) > 0).map(row => <option key={row.id} value={row.id}>{row.studentName} · demand {row.id} · balance {money(row.balance)}</option>)}</Select></FormField><FormField label="Discount amount" required><Input type="number" min="0.01" step="0.01" required value={discount.amount} onChange={event => setDiscount(v => ({ ...v, amount: event.target.value }))} /></FormField><FormField label="Reason" required><Textarea required maxLength={500} value={discount.reason} onChange={event => setDiscount(v => ({ ...v, reason: event.target.value }))} /></FormField><div className="finance-form-actions"><Button>Request approval</Button></div></form></Card>}<FinanceTable query={discounts} caption="Discount approvals" columns={discountColumns} rows={discounts.data ?? []} /></div>}

    {tab === 'expenses' && canExpense && <div className="finance-panel">{can('EXPENSE:MANAGE') && <Card className="finance-form-card"><h2>Record expense</h2><form className="finance-form" onSubmit={event => { event.preventDefault(); run(() => createExpense.mutate({ ...expense, amount: expense.amount }), 'Save expense draft', `Record ${money(expense.amount)} as a draft expense?`); }}><FormField label="Category" required><Input required maxLength={100} value={expense.category} onChange={event => setExpense(v => ({ ...v, category: event.target.value }))} /></FormField><FormField label="Description" required><Input required maxLength={500} value={expense.description} onChange={event => setExpense(v => ({ ...v, description: event.target.value }))} /></FormField><FormField label="Amount" required><Input type="number" min="0.01" step="0.01" required value={expense.amount} onChange={event => setExpense(v => ({ ...v, amount: event.target.value }))} /></FormField><FormField label="Expense date" required><DatePicker required value={expense.expenseDate} onChange={event => setExpense(v => ({ ...v, expenseDate: event.target.value }))} /></FormField><FormField label="Vendor"><Input maxLength={200} value={expense.vendor} onChange={event => setExpense(v => ({ ...v, vendor: event.target.value }))} /></FormField><FormField label="Receipt URL"><Input type="url" maxLength={1000} value={expense.receiptUrl} onChange={event => setExpense(v => ({ ...v, receiptUrl: event.target.value }))} /></FormField><div className="finance-form-actions"><Button>Save draft</Button></div></form></Card>}<FinanceTable query={expenses} caption="Finance expenses" columns={expenseColumns} rows={expenses.data ?? []} /></div>}

    {tab === 'reports' && (canReport || canExport) && <div className="finance-panel"><Card className="finance-filter-card"><div className="finance-date-filters"><FormField label="From"><DatePicker value={dates.from} onChange={event => setDates(v => ({ ...v, from: event.target.value }))} /></FormField><FormField label="To"><DatePicker value={dates.to} onChange={event => setDates(v => ({ ...v, to: event.target.value }))} /></FormField>{canExport && <Button variant="secondary" onClick={() => { setError(''); void financeApi.exportPayments(dates.from, dates.to).then(csv => { const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' }); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.setAttribute('download', `finance-payments-${dates.from}-to-${dates.to}.csv`); document.body.appendChild(link); link.click(); document.body.removeChild(link); URL.revokeObjectURL(url); }).catch(onError); }}>Export payments CSV</Button>}</div></Card>{canReport && summary.data && <div className="finance-summary-grid"><Stat label="Collections" value={money(summary.data.collections)} detail={`${summary.data.paymentCount} posted payments`} /><Stat label="Refunds" value={money(summary.data.refunds)} detail="Refund ledger" /><Stat label="Approved expenses" value={money(summary.data.expenses)} detail={`${summary.data.expenseCount} approved records`} /><Stat label="Outstanding" value={money(summary.data.outstanding)} detail="Open fee demands" /></div>}{canReport && <Card className="finance-table-card"><div className="finance-section-title"><h2>Reconciliation</h2><span>{reconciliation.data?.length ?? 0} discrepancies</span></div>{reconciliation.isError ? <Alert tone="danger">{reconciliation.error.message}</Alert> : reconciliation.data?.length ? <DataTable caption="Finance reconciliation" rowKey={row => `${row.recordType}-${row.recordId}`} rows={reconciliation.data} columns={[{ key: 'recordType', header: 'Record' }, { key: 'recordId', header: 'ID' }, { key: 'recordedAmount', header: 'Recorded', render: row => money(row.recordedAmount) }, { key: 'calculatedAmount', header: 'Calculated', render: row => money(row.calculatedAmount) }, { key: 'difference', header: 'Difference', render: row => money(row.difference) }]} /> : <EmptyState compact title="No reconciliation differences" description="Demand balances match the posted payment ledger." />}</Card>}</div>}

    </div>
    <ConfirmDialog open={Boolean(confirmation)} onClose={() => setConfirmation(null)} onConfirm={() => { const action = confirmation?.action; setConfirmation(null); action?.(); }} title={confirmation?.title ?? 'Confirm Finance operation'} confirmLabel="Confirm operation">{confirmation?.message}</ConfirmDialog>
  </section>;
}

function FinanceTable<T>({ query, caption, columns, rows }: { query: { isPending: boolean; isError: boolean; error: Error | null; refetch: () => unknown }; caption: string; columns: { key: string; header: string; render?: (row: T) => ReactNode }[]; rows: T[] }) {
  if (query.isPending) return <Card role="status">Loading {caption.toLowerCase()}…</Card>;
  if (query.isError) return <ErrorState title={`${caption} unavailable`} description={query.error?.message} onRetry={() => void query.refetch()} />;
  return <Card className="finance-table-card"><DataTable columns={columns} rows={rows} rowKey={row => String((row as { id: number }).id)} caption={caption} emptyMessage={`No ${caption.toLowerCase()} found`} /></Card>;
}

function Stat({ label, value, detail }: { label: string; value: string; detail: string }) {
  return <Card className="finance-stat"><span>{label}</span><strong>{value}</strong><small>{detail}</small></Card>;
}