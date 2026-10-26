
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Card,
  StatCard,
  DataTable,
  EmptyState,
  ErrorState,
  FormField,
  Input,
  Select,
  SearchInput,
  Modal,
  StatusBadge,
  Badge,
  ConfirmDialog,
  Alert,
} from '../components';
import {
  transportApi,
  type BusResponse,
  type RouteResponse,
  type StudentAssignmentResponse,
  type AssignmentHistoryResponse,
  type CreateBusRequest,
  type UpdateBusRequest,
  type CreateRouteRequest,
  type UpdateRouteRequest,
  type RouteStopRequest,
  type AssignStudentRequest,
  type ReassignStudentRequest,
  type BusDetailResponse,
  type TransportStaffOption,
} from '../api/transport';
import { useAuth } from '../auth/AuthProvider';
import './transport-pages.css';

// ==========================================
// 1. OVERVIEW VIEW
// ==========================================

export function TransportOverviewView() {
  const query = useQuery({
    queryKey: ['transport', 'overview'],
    queryFn: transportApi.getOverview,
    staleTime: 15000,
  });

  if (query.isLoading) return <EmptyState title="Loading transport metrics…" compact />;
  if (query.error) return <ErrorState title="Could not load transport overview" onRetry={() => void query.refetch()} />;

  const data = query.data;
  if (!data) return <EmptyState title="No transport data available" compact />;

  const historyColumns = [
    { key: 'studentName', header: 'Student', render: (r: AssignmentHistoryResponse) => <div><strong>{r.studentName}</strong><div className="ds-muted" style={{ fontSize: '11px' }}>{r.admissionNo}</div></div> },
    { key: 'busName', header: 'Bus' },
    { key: 'routeName', header: 'Route' },
    { key: 'action', header: 'Action', render: (r: AssignmentHistoryResponse) => <Badge tone={r.action === 'ASSIGNED' ? 'success' : r.action === 'REASSIGNED' ? 'info' : 'warning'}>{r.action}</Badge> },
    { key: 'reason', header: 'Reason', render: (r: AssignmentHistoryResponse) => r.reason || '—' },
    { key: 'changerName', header: 'Changed By' },
    { key: 'changedAt', header: 'Date', render: (r: AssignmentHistoryResponse) => new Date(r.changedAt).toLocaleString() },
  ];

  return (
    <div className="transport-container">
      <div className="transport-stats-grid">
        <StatCard label="Active Buses" value={data.totalActiveBuses} icon="🚌" tone="neutral" />
        <StatCard label="Active Routes" value={data.totalActiveRoutes} icon="🗺️" tone="neutral" />
        <StatCard label="Assigned Students" value={data.totalAssignedStudents} icon="🎒" tone="success" />
        <StatCard label="Available Seats" value={data.totalAvailableSeats} icon="💺" tone="info" />
        <StatCard label="Full Buses" value={data.fullBusesCount} icon="⚠️" tone={data.fullBusesCount > 0 ? 'warning' : 'neutral'} />
        <StatCard label="Buses without Driver" value={data.busesWithoutDriver} icon="👤" tone={data.busesWithoutDriver > 0 ? 'danger' : 'neutral'} />
        <StatCard label="Buses without Conductor" value={data.busesWithoutConductor} icon="👥" tone={data.busesWithoutConductor > 0 ? 'warning' : 'neutral'} />
        <StatCard label="Routes without Bus" value={data.routesWithoutBus} icon="📍" tone={data.routesWithoutBus > 0 ? 'warning' : 'neutral'} />
      </div>

      <Card>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 700 }}>Recent Assignment Activity</h3>
            <p className="ds-muted" style={{ margin: '2px 0 0', fontSize: '12px' }}>Latest changes to student bus and route allocations</p>
          </div>
        </div>
        <DataTable<AssignmentHistoryResponse>
          columns={historyColumns}
          rows={data.recentHistory || []}
          rowKey={r => String(r.id)}
          caption="Recent transport activity"
          emptyMessage="No recent transport activity recorded"
        />
      </Card>
    </div>
  );
}

// ==========================================
// 2. BUS MANAGEMENT VIEW
// ==========================================

export function TransportBusesView() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [createModal, setCreateModal] = useState(false);
  const [editBus, setEditBus] = useState<BusResponse | null>(null);
  const [deactivateId, setDeactivateId] = useState<number | null>(null);
  const [detailsId, setDetailsId] = useState<number | null>(null);
  const [formError, setFormError] = useState('');
  const [staffModal, setStaffModal] = useState(false);
  const [defaultStaffRole, setDefaultStaffRole] = useState<'Bus Driver' | 'Bus Conductor'>('Bus Driver');

  const canCreate = hasPermission('TRANSPORT:CREATE');
  const canUpdate = hasPermission('TRANSPORT:UPDATE');
  const canDelete = hasPermission('TRANSPORT:DELETE') || canUpdate;

  const busesQuery = useQuery({
    queryKey: ['transport', 'buses', search, status],
    queryFn: () => transportApi.getBuses({ search, status: status || undefined }),
  });

  const routesQuery = useQuery({ queryKey: ['transport', 'routes-options'], queryFn: () => transportApi.getRoutes({ status: 'ACTIVE' }) });
  const staffQuery = useQuery({ queryKey: ['transport', 'staff-options'], queryFn: () => transportApi.getStaffOptions() });

  const createMutation = useMutation({
    mutationFn: transportApi.createBus,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['transport'] });
      setCreateModal(false);
      setFormError('');
    },
    onError: err => setFormError(err instanceof Error ? err.message : 'Failed to create bus'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateBusRequest }) => transportApi.updateBus(id, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['transport'] });
      setEditBus(null);
      setFormError('');
    },
    onError: err => setFormError(err instanceof Error ? err.message : 'Failed to update bus'),
  });

  const deactivateMutation = useMutation({
    mutationFn: transportApi.deactivateBus,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['transport'] });
      setDeactivateId(null);
    },
    onError: err => setFormError(err instanceof Error ? err.message : 'Failed to deactivate bus'),
  });

  const detailsQuery = useQuery({
    queryKey: ['transport', 'bus-details', detailsId],
    queryFn: () => transportApi.getBus(detailsId!),
    enabled: detailsId !== null,
  });

  const columns = [
    {
      key: 'busName',
      header: 'Bus Name',
      render: (b: BusResponse) => (
        <div>
          <strong>{b.busName}</strong>
          {b.fleetNo && <span className="ds-muted" style={{ fontSize: '11px', marginLeft: '6px' }}>({b.fleetNo})</span>}
          <div className="ds-muted" style={{ fontSize: '11px' }}>{b.busType}</div>
        </div>
      ),
    },
    { key: 'registrationNo', header: 'Registration No.' },
    { key: 'routeName', header: 'Assigned Route', render: (b: BusResponse) => b.routeName ? `${b.routeName} (${b.routeCode})` : <span className="ds-muted">Unassigned</span> },
    { key: 'driverName', header: 'Driver', render: (b: BusResponse) => b.driverName ? <div>{b.driverName}{b.driverPhone && <div style={{ fontSize: '11px', color: 'var(--muted)' }}>{b.driverPhone}</div>}</div> : <span className="ds-muted">None</span> },
    { key: 'conductorName', header: 'Conductor', render: (b: BusResponse) => b.conductorName ? <div>{b.conductorName}</div> : <span className="ds-muted">None</span> },
    {
      key: 'capacity',
      header: 'Capacity & Occupancy',
      render: (b: BusResponse) => {
        const pct = b.capacity > 0 ? Math.min(100, Math.round((b.activeOccupancy / b.capacity) * 100)) : 0;
        const fillClass = pct >= 100 ? 'fill-full' : pct >= 80 ? 'fill-warning' : 'fill-normal';
        return (
          <div className="transport-capacity-bar">
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px' }}>
              <strong>{b.activeOccupancy} / {b.capacity}</strong>
              <span className="ds-muted">{b.availableSeats} free</span>
            </div>
            <div className="transport-capacity-track">
              <div className={`transport-capacity-fill ${fillClass}`} style={{ width: `${pct}%` }} />
            </div>
          </div>
        );
      },
    },
    { key: 'status', header: 'Status', render: (b: BusResponse) => <StatusBadge status={b.status} /> },
    {
      key: 'actions',
      header: 'Actions',
      render: (b: BusResponse) => (
        <div style={{ display: 'flex', gap: '0.4rem' }}>
          <Button size="sm" variant="secondary" onClick={() => setDetailsId(b.id)}>Details</Button>
          {canUpdate && <Button size="sm" variant="ghost" onClick={() => { setFormError(''); setEditBus(b); }}>Edit</Button>}
          {canDelete && b.status === 'ACTIVE' && (
            <Button size="sm" variant="danger" onClick={() => setDeactivateId(b.id)}>Deactivate</Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="transport-container">
      <div className="transport-filter-row">
        <div className="transport-filter-inputs">
          <SearchInput
            placeholder="Search bus name, registration, route…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            style={{ width: '280px' }}
          />
          <Select value={status} onChange={e => setStatus(e.target.value)} style={{ width: '150px' }}>
            <option value="">All Statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
            <option value="MAINTENANCE">Maintenance</option>
          </Select>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem' }}>
          {canCreate && (
            <Button variant="secondary" onClick={() => { setDefaultStaffRole('Bus Driver'); setStaffModal(true); }}>
              + Add Driver / Conductor
            </Button>
          )}
          {canCreate && <Button onClick={() => { setFormError(''); setCreateModal(true); }}>Add Bus</Button>}
        </div>
      </div>

      {formError && !createModal && !editBus && (
        <div style={{ marginBottom: '1rem' }}><Alert tone="danger">{formError}</Alert></div>
      )}

      <Card>
        {busesQuery.isLoading ? (
          <EmptyState title="Loading buses…" compact />
        ) : busesQuery.error ? (
          <ErrorState title="Could not load buses" onRetry={() => void busesQuery.refetch()} />
        ) : (
          <DataTable<BusResponse>
            columns={columns}
            rows={busesQuery.data ?? []}
            rowKey={b => String(b.id)}
            caption="Buses table"
            emptyMessage="No buses found"
          />
        )}
      </Card>

      {/* CREATE BUS MODAL */}
      <Modal open={createModal} onClose={() => setCreateModal(false)} title="Add School Bus" size="md">
        <BusForm
          routes={routesQuery.data ?? []}
          staff={staffQuery.data ?? []}
          error={formError}
          isSubmitting={createMutation.isPending}
          onSubmit={data => createMutation.mutate(data as CreateBusRequest)}
          onCancel={() => setCreateModal(false)}
          onQuickAddStaff={role => { setDefaultStaffRole(role); setStaffModal(true); }}
        />
      </Modal>

      {/* EDIT BUS MODAL */}
      <Modal open={editBus !== null} onClose={() => setEditBus(null)} title={`Edit Bus: ${editBus?.busName}`} size="md">
        {editBus && (
          <BusForm
            initial={editBus}
            routes={routesQuery.data ?? []}
            staff={staffQuery.data ?? []}
            error={formError}
            isSubmitting={updateMutation.isPending}
            onSubmit={data => updateMutation.mutate({ id: editBus.id, data: data as UpdateBusRequest })}
            onCancel={() => setEditBus(null)}
            onQuickAddStaff={role => { setDefaultStaffRole(role); setStaffModal(true); }}
          />
        )}
      </Modal>

      {/* CREATE DRIVER / CONDUCTOR MODAL */}
      <CreateStaffModal
        isOpen={staffModal}
        onClose={() => setStaffModal(false)}
        defaultDesignation={defaultStaffRole}
      />

      {/* DEACTIVATE CONFIRMATION */}
      <ConfirmDialog
        open={deactivateId !== null}
        onClose={() => setDeactivateId(null)}
        onConfirm={() => deactivateId && deactivateMutation.mutate(deactivateId)}
        title="Deactivate Bus"
        confirmLabel="Deactivate"
      >
        Are you sure you want to deactivate this bus? The bus will become inactive. Deactivation will be rejected if active student assignments are linked to it.
      </ConfirmDialog>

      {/* BUS DETAILS MODAL */}
      <Modal open={detailsId !== null} onClose={() => setDetailsId(null)} title="Bus Details" size="lg">
        {detailsQuery.isLoading ? (
          <EmptyState title="Loading bus details…" compact />
        ) : detailsQuery.data ? (
          <BusDetailView details={detailsQuery.data} />
        ) : null}
      </Modal>
    </div>
  );
}

function BusForm({
  initial,
  routes,
  staff,
  error,
  isSubmitting,
  onSubmit,
  onCancel,
  onQuickAddStaff,
}: {
  initial?: BusResponse;
  routes: RouteResponse[];
  staff: TransportStaffOption[];
  error?: string;
  isSubmitting: boolean;
  onSubmit: (data: CreateBusRequest | UpdateBusRequest) => void;
  onCancel: () => void;
  onQuickAddStaff?: (role: 'Bus Driver' | 'Bus Conductor') => void;
}) {
  const [busName, setBusName] = useState(initial?.busName ?? '');
  const [registrationNo, setRegistrationNo] = useState(initial?.registrationNo ?? '');
  const [fleetNo, setFleetNo] = useState(initial?.fleetNo ?? '');
  const [busType, setBusType] = useState(initial?.busType ?? 'STANDARD');
  const [capacity, setCapacity] = useState(initial?.capacity ? String(initial.capacity) : '30');
  const [routeId, setRouteId] = useState(initial?.currentRouteId ? String(initial.currentRouteId) : '');
  const [driverStaffId, setDriverStaffId] = useState(initial?.driverStaffId ? String(initial.driverStaffId) : '');
  const [conductorStaffId, setConductorStaffId] = useState(initial?.conductorStaffId ? String(initial.conductorStaffId) : '');
  const [status, setStatus] = useState(initial?.status ?? 'ACTIVE');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const capNum = parseInt(capacity, 10);
    onSubmit({
      busName: busName.trim(),
      registrationNo: registrationNo.trim(),
      fleetNo: fleetNo.trim() || undefined,
      busType,
      capacity: isNaN(capNum) ? 0 : capNum,
      routeId: routeId ? parseInt(routeId, 10) : null,
      driverStaffId: driverStaffId ? parseInt(driverStaffId, 10) : null,
      conductorStaffId: conductorStaffId ? parseInt(conductorStaffId, 10) : null,
      status: initial ? status : undefined,
    });
  };

  return (
    <form onSubmit={handleSubmit} style={{ display: 'grid', gap: '1rem' }}>
      {error && <Alert tone="danger">{error}</Alert>}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
        <FormField label="Bus Name" required>
          <Input value={busName} onChange={e => setBusName(e.target.value)} placeholder="e.g. School Bus 01" required />
        </FormField>
        <FormField label="Registration No." required>
          <Input value={registrationNo} onChange={e => setRegistrationNo(e.target.value)} placeholder="e.g. RJ14 XX 1234" required />
        </FormField>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '0.75rem' }}>
        <FormField label="Fleet No.">
          <Input value={fleetNo} onChange={e => setFleetNo(e.target.value)} placeholder="e.g. FL-01" />
        </FormField>
        <FormField label="Bus Type">
          <Select value={busType} onChange={e => setBusType(e.target.value)}>
            <option value="STANDARD">Standard</option>
            <option value="MINI">Mini Bus</option>
            <option value="VAN">Van</option>
            <option value="AC">AC Deluxe</option>
          </Select>
        </FormField>
        <FormField label="Seating Capacity" required hint={initial ? `Active occupied: ${initial.activeOccupancy}` : undefined}>
          <Input type="number" min="1" max="120" value={capacity} onChange={e => setCapacity(e.target.value)} required />
        </FormField>
      </div>

      <FormField label="Assign to Route">
        <Select value={routeId} onChange={e => setRouteId(e.target.value)}>
          <option value="">-- No Route Assigned --</option>
          {routes.map(r => (
            <option key={r.id} value={r.id}>{r.routeName} ({r.routeCode})</option>
          ))}
        </Select>
      </FormField>

      {/* DRIVER & CONDUCTOR SELECTION - STRICTLY FILTERED BY DESIGNATION */}
      {(() => {
        const drivers = staff.filter(s =>
          (s.designation && s.designation.toLowerCase().includes('driver')) ||
          (initial && initial.driverStaffId === s.id)
        );
        const conductors = staff.filter(s =>
          (s.designation && s.designation.toLowerCase().includes('conductor')) ||
          (initial && initial.conductorStaffId === s.id)
        );

        return (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                <label className="ds-label" htmlFor="assigned-driver-select" style={{ margin: 0 }}>Assigned Driver (Staff)</label>
                {onQuickAddStaff && (
                  <button type="button" className="transport-link-btn" onClick={() => onQuickAddStaff('Bus Driver')}>
                    + New Driver
                  </button>
                )}
              </div>
              <Select
                id="assigned-driver-select"
                value={driverStaffId}
                onChange={e => setDriverStaffId(e.target.value)}
              >
                <option value="">-- Select Driver --</option>
                {drivers.map(s => (
                  <option key={s.id} value={s.id}>
                    {s.name} ({s.designation}){s.assignedBusName && s.assignedBusId !== initial?.id ? ` [Bus: ${s.assignedBusName}]` : ''}
                  </option>
                ))}
              </Select>
            </div>

            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
                <label className="ds-label" htmlFor="assigned-conductor-select" style={{ margin: 0 }}>Assigned Conductor (Staff)</label>
                {onQuickAddStaff && (
                  <button type="button" className="transport-link-btn" onClick={() => onQuickAddStaff('Bus Conductor')}>
                    + New Conductor
                  </button>
                )}
              </div>
              <Select
                id="assigned-conductor-select"
                value={conductorStaffId}
                onChange={e => setConductorStaffId(e.target.value)}
              >
                <option value="">-- Select Conductor --</option>
                {conductors.map(s => (
                  <option key={s.id} value={s.id}>
                    {s.name} ({s.designation}){s.assignedBusName && s.assignedBusId !== initial?.id ? ` [Bus: ${s.assignedBusName}]` : ''}
                  </option>
                ))}
              </Select>
            </div>
          </div>
        );
      })()}

      {initial && (
        <FormField label="Status">
          <Select value={status} onChange={e => setStatus(e.target.value as BusResponse['status'])}>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
            <option value="MAINTENANCE">Maintenance</option>
          </Select>
        </FormField>
      )}

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '0.5rem' }}>
        <Button variant="secondary" type="button" onClick={onCancel}>Cancel</Button>
        <Button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Saving…' : 'Save Bus'}</Button>
      </div>
    </form>
  );
}

function BusDetailView({ details }: { details: BusDetailResponse }) {
  const { bus, assignedStudents, routeStops } = details;
  const studentCols = [
    { key: 'studentName', header: 'Student Name' },
    { key: 'admissionNo', header: 'Admission No.' },
    { key: 'pickupStopName', header: 'Pickup Stop', render: (s: StudentAssignmentResponse) => `${s.pickupStopName || '—'} (${s.pickupTime || '—'})` },
    { key: 'dropStopName', header: 'Drop Stop', render: (s: StudentAssignmentResponse) => `${s.dropStopName || '—'} (${s.dropTime || '—'})` },
    { key: 'startDate', header: 'Assigned Date' },
    { key: 'status', header: 'Status', render: (s: StudentAssignmentResponse) => <StatusBadge status={s.status} /> },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
      <div className="transport-card-highlight">
        <div className="transport-card-header">
          <div>
            <h3>{bus.busName}</h3>
            <p>{bus.registrationNo} • {bus.busType}</p>
          </div>
          <StatusBadge status={bus.status} />
        </div>
        <div className="transport-info-grid">
          <div className="transport-info-item"><span className="transport-info-label">Assigned Route</span><span className="transport-info-value">{bus.routeName || 'Unassigned'}</span></div>
          <div className="transport-info-item"><span className="transport-info-label">Capacity & Seats</span><span className="transport-info-value">{bus.activeOccupancy} assigned / {bus.capacity} seats ({bus.availableSeats} available)</span></div>
          <div className="transport-info-item"><span className="transport-info-label">Driver</span><span className="transport-info-value">{bus.driverName || 'None'}{bus.driverPhone && ` (${bus.driverPhone})`}</span></div>
          <div className="transport-info-item"><span className="transport-info-label">Conductor</span><span className="transport-info-value">{bus.conductorName || 'None'}{bus.conductorPhone && ` (${bus.conductorPhone})`}</span></div>
        </div>
      </div>

      <Card>
        <h4 style={{ margin: '0 0 0.75rem', fontSize: '0.95rem', fontWeight: 700 }}>Currently Assigned Students ({assignedStudents.length})</h4>
        <DataTable<StudentAssignmentResponse>
          columns={studentCols}
          rows={assignedStudents}
          rowKey={s => String(s.id)}
          caption="Assigned students list"
          emptyMessage="No students are currently assigned to this bus"
        />
      </Card>

      {routeStops.length > 0 && (
        <Card>
          <h4 style={{ margin: '0 0 0.75rem', fontSize: '0.95rem', fontWeight: 700 }}>Route Stops ({routeStops.length})</h4>
          <div className="transport-stops-list">
            {routeStops.map(stop => (
              <div key={stop.id} className="transport-stop-item">
                <span className="transport-stop-order">{stop.stopOrder}</span>
                <div className="transport-stop-details">
                  <span className="transport-stop-name">{stop.stopName}</span>
                  <div className="transport-stop-times">
                    <span>Pickup: {stop.pickupTime || '—'}</span>
                    <span>Drop-off: {stop.dropTime || '—'}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}

function CreateStaffModal({
  isOpen,
  onClose,
  defaultDesignation = 'Bus Driver',
  onSuccess,
}: {
  isOpen: boolean;
  onClose: () => void;
  defaultDesignation?: string;
  onSuccess?: () => void;
}) {
  const queryClient = useQueryClient();
  const [employeeNo, setEmployeeNo] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [designation, setDesignation] = useState(defaultDesignation);
  const [phone, setPhone] = useState('');
  const [error, setError] = useState('');

  const createMutation = useMutation({
    mutationFn: transportApi.createStaff,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['transport', 'staff-options'] });
      onSuccess?.();
      onClose();
      setError('');
      setEmployeeNo('');
      setFirstName('');
      setLastName('');
      setPhone('');
    },
    onError: err => setError(err instanceof Error ? err.message : 'Failed to register personnel'),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!employeeNo.trim() || !firstName.trim() || !designation.trim()) {
      setError('Please fill in employee number, first name, and designation');
      return;
    }
    createMutation.mutate({
      employeeNo: employeeNo.trim(),
      firstName: firstName.trim(),
      lastName: lastName.trim() || undefined,
      designation: designation.trim(),
      phone: phone.trim() || undefined,
    });
  };

  return (
    <Modal open={isOpen} onClose={onClose} title="Register Transport Personnel (Driver / Conductor)" size="md">
      <form onSubmit={handleSubmit} style={{ display: 'grid', gap: '1rem' }}>
        {error && <Alert tone="danger">{error}</Alert>}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
          <FormField label="Role / Designation" required>
            <Select value={designation} onChange={e => setDesignation(e.target.value)}>
              <option value="Bus Driver">Bus Driver</option>
              <option value="Senior Bus Driver">Senior Bus Driver</option>
              <option value="Bus Conductor">Bus Conductor</option>
            </Select>
          </FormField>
          <FormField label="Employee Number" required hint="e.g. DRV-003 or CND-002">
            <Input value={employeeNo} onChange={e => setEmployeeNo(e.target.value)} required placeholder="DRV-003" />
          </FormField>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
          <FormField label="First Name" required>
            <Input value={firstName} onChange={e => setFirstName(e.target.value)} required />
          </FormField>
          <FormField label="Last Name">
            <Input value={lastName} onChange={e => setLastName(e.target.value)} />
          </FormField>
        </div>
        <FormField label="Phone Number" hint="Optional contact number">
          <Input type="tel" value={phone} onChange={e => setPhone(e.target.value)} placeholder="+91 98765 43210" />
        </FormField>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '0.5rem' }}>
          <Button variant="secondary" type="button" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={createMutation.isPending}>
            {createMutation.isPending ? 'Registering…' : 'Register Personnel'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

// ==========================================
// 3. ROUTE MANAGEMENT VIEW
// ==========================================

export function TransportRoutesView() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('');
  const [createModal, setCreateModal] = useState(false);
  const [editRoute, setEditRoute] = useState<RouteResponse | null>(null);
  const [stopsRouteId, setStopsRouteId] = useState<number | null>(null);
  const [deactivateId, setDeactivateId] = useState<number | null>(null);
  const [formError, setFormError] = useState('');

  const canManage = hasPermission('TRANSPORT:MANAGE_ROUTES');

  const routesQuery = useQuery({
    queryKey: ['transport', 'routes', search, status],
    queryFn: () => transportApi.getRoutes({ search, status: status || undefined }),
  });

  const createMutation = useMutation({
    mutationFn: transportApi.createRoute,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['transport'] });
      setCreateModal(false);
      setFormError('');
    },
    onError: err => setFormError(err instanceof Error ? err.message : 'Failed to create route'),
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: UpdateRouteRequest }) => transportApi.updateRoute(id, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['transport'] });
      setEditRoute(null);
      setFormError('');
    },
    onError: err => setFormError(err instanceof Error ? err.message : 'Failed to update route'),
  });

  const deactivateMutation = useMutation({
    mutationFn: transportApi.deactivateRoute,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['transport'] });
      setDeactivateId(null);
    },
    onError: err => setFormError(err instanceof Error ? err.message : 'Failed to deactivate route'),
  });

  const columns = [
    { key: 'routeName', header: 'Route Name', render: (r: RouteResponse) => <div><strong>{r.routeName}</strong><div className="ds-muted" style={{ fontSize: '11px' }}>{r.routeCode}</div></div> },
    { key: 'description', header: 'Description', render: (r: RouteResponse) => r.description || '—' },
    { key: 'assignedBusName', header: 'Assigned Bus', render: (r: RouteResponse) => r.assignedBusName ? <strong>{r.assignedBusName}</strong> : <span className="ds-muted">None</span> },
    { key: 'stopsCount', header: 'Stops', render: (r: RouteResponse) => `${r.stopsCount} stop${r.stopsCount === 1 ? '' : 's'}` },
    { key: 'activeStudentsCount', header: 'Active Students', render: (r: RouteResponse) => <Badge tone="info">{r.activeStudentsCount} students</Badge> },
    { key: 'status', header: 'Status', render: (r: RouteResponse) => <StatusBadge status={r.status} /> },
    {
      key: 'actions',
      header: 'Actions',
      render: (r: RouteResponse) => (
        <div style={{ display: 'flex', gap: '0.4rem' }}>
          <Button size="sm" variant="secondary" onClick={() => setStopsRouteId(r.id)}>Manage Stops</Button>
          {canManage && <Button size="sm" variant="ghost" onClick={() => { setFormError(''); setEditRoute(r); }}>Edit</Button>}
          {canManage && r.status === 'ACTIVE' && (
            <Button size="sm" variant="danger" onClick={() => setDeactivateId(r.id)}>Deactivate</Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="transport-container">
      <div className="transport-filter-row">
        <div className="transport-filter-inputs">
          <SearchInput
            placeholder="Search route name, route code…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            style={{ width: '280px' }}
          />
          <Select value={status} onChange={e => setStatus(e.target.value)} style={{ width: '150px' }}>
            <option value="">All Statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="INACTIVE">Inactive</option>
          </Select>
        </div>
        {canManage && <Button onClick={() => { setFormError(''); setCreateModal(true); }}>Add Route</Button>}
      </div>

      {formError && !createModal && !editRoute && (
        <div style={{ marginBottom: '1rem' }}><Alert tone="danger">{formError}</Alert></div>
      )}

      <Card>
        {routesQuery.isLoading ? (
          <EmptyState title="Loading routes…" compact />
        ) : routesQuery.error ? (
          <ErrorState title="Could not load routes" onRetry={() => void routesQuery.refetch()} />
        ) : (
          <DataTable<RouteResponse>
            columns={columns}
            rows={routesQuery.data ?? []}
            rowKey={r => String(r.id)}
            caption="Transport routes table"
            emptyMessage="No routes found"
          />
        )}
      </Card>

      {/* CREATE ROUTE MODAL */}
      <Modal open={createModal} onClose={() => setCreateModal(false)} title="Create Transport Route" size="lg">
        <RouteForm
          error={formError}
          isSubmitting={createMutation.isPending}
          onSubmit={data => createMutation.mutate(data)}
          onCancel={() => setCreateModal(false)}
        />
      </Modal>

      {/* EDIT ROUTE MODAL */}
      <Modal open={editRoute !== null} onClose={() => setEditRoute(null)} title={`Edit Route: ${editRoute?.routeName}`} size="md">
        {editRoute && (
          <form
            onSubmit={e => {
              e.preventDefault();
              const target = e.target as HTMLFormElement;
              const name = (target.elements.namedItem('routeName') as HTMLInputElement).value;
              const code = (target.elements.namedItem('routeCode') as HTMLInputElement).value;
              const desc = (target.elements.namedItem('description') as HTMLTextAreaElement).value;
              const st = (target.elements.namedItem('status') as HTMLSelectElement).value;
              updateMutation.mutate({ id: editRoute.id, data: { routeName: name, routeCode: code, description: desc, status: st } });
            }}
            style={{ display: 'grid', gap: '1rem' }}
          >
            {formError && <Alert tone="danger">{formError}</Alert>}
            <FormField label="Route Name" required><Input name="routeName" defaultValue={editRoute.routeName} required /></FormField>
            <FormField label="Route Code" required><Input name="routeCode" defaultValue={editRoute.routeCode} required /></FormField>
            <FormField label="Description"><Input name="description" defaultValue={editRoute.description || ''} /></FormField>
            <FormField label="Status">
              <Select name="status" defaultValue={editRoute.status}>
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
              </Select>
            </FormField>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
              <Button variant="secondary" type="button" onClick={() => setEditRoute(null)}>Cancel</Button>
              <Button type="submit" disabled={updateMutation.isPending}>{updateMutation.isPending ? 'Saving…' : 'Save Changes'}</Button>
            </div>
          </form>
        )}
      </Modal>

      {/* STOPS MANAGER MODAL */}
      <Modal open={stopsRouteId !== null} onClose={() => setStopsRouteId(null)} title="Manage Route Stops" size="lg">
        {stopsRouteId !== null && <RouteStopsManager routeId={stopsRouteId} onClose={() => setStopsRouteId(null)} />}
      </Modal>

      {/* DEACTIVATE CONFIRMATION */}
      <ConfirmDialog
        open={deactivateId !== null}
        onClose={() => setDeactivateId(null)}
        onConfirm={() => deactivateId && deactivateMutation.mutate(deactivateId)}
        title="Deactivate Route"
        confirmLabel="Deactivate"
      >
        Are you sure you want to deactivate this route? Deactivation will be rejected if active student assignments or active buses are currently linked to this route.
      </ConfirmDialog>
    </div>
  );
}

function RouteForm({
  error,
  isSubmitting,
  onSubmit,
  onCancel,
}: {
  error?: string;
  isSubmitting: boolean;
  onSubmit: (data: CreateRouteRequest) => void;
  onCancel: () => void;
}) {
  const [routeName, setRouteName] = useState('');
  const [routeCode, setRouteCode] = useState('');
  const [description, setDescription] = useState('');
  const [stops, setStops] = useState<RouteStopRequest[]>([
    { stopName: '', stopOrder: 1, pickupTime: '07:15', dropTime: '15:15' },
    { stopName: 'School Campus', stopOrder: 2, pickupTime: '07:50', dropTime: '14:45' },
  ]);

  const addStopRow = () => {
    setStops(cur => [...cur, { stopName: '', stopOrder: cur.length + 1, pickupTime: '', dropTime: '' }]);
  };

  const removeStopRow = (index: number) => {
    setStops(cur => cur.filter((_, i) => i !== index).map((s, i) => ({ ...s, stopOrder: i + 1 })));
  };

  const updateStopField = (index: number, field: keyof RouteStopRequest, val: string | number) => {
    setStops(cur => cur.map((s, i) => (i === index ? { ...s, [field]: val } : s)));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const validStops = stops.filter(s => s.stopName.trim() !== '');
    onSubmit({
      routeName: routeName.trim(),
      routeCode: routeCode.trim(),
      description: description.trim() || undefined,
      stops: validStops,
    });
  };

  return (
    <form onSubmit={handleSubmit} style={{ display: 'grid', gap: '1rem' }}>
      {error && <Alert tone="danger">{error}</Alert>}
      <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '0.75rem' }}>
        <FormField label="Route Name" required><Input value={routeName} onChange={e => setRouteName(e.target.value)} placeholder="e.g. Vaishali Nagar Route" required /></FormField>
        <FormField label="Route Code" required><Input value={routeCode} onChange={e => setRouteCode(e.target.value)} placeholder="e.g. VSH-01" required /></FormField>
      </div>
      <FormField label="Description"><Input value={description} onChange={e => setDescription(e.target.value)} placeholder="e.g. Serves sectors 1 to 7, arriving before 07:50 AM" /></FormField>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '0.5rem' }}>
        <strong>Ordered Stops & Timings</strong>
        <Button size="sm" variant="secondary" type="button" onClick={addStopRow}>+ Add Stop</Button>
      </div>

      <div style={{ display: 'grid', gap: '0.5rem', maxHeight: '260px', overflowY: 'auto' }}>
        {stops.map((s, idx) => (
          <div key={idx} style={{ display: 'grid', gridTemplateColumns: '40px 1fr 120px 120px 36px', gap: '0.5rem', alignItems: 'center' }}>
            <span style={{ fontWeight: 700, textAlign: 'center', color: 'var(--muted)' }}>#{s.stopOrder}</span>
            <Input value={s.stopName} onChange={e => updateStopField(idx, 'stopName', e.target.value)} placeholder="Stop name (e.g. Sector 4 Gate)" required={idx === 0} />
            <Input type="time" value={s.pickupTime || ''} onChange={e => updateStopField(idx, 'pickupTime', e.target.value)} title="Pickup time" />
            <Input type="time" value={s.dropTime || ''} onChange={e => updateStopField(idx, 'dropTime', e.target.value)} title="Drop-off time" />
            <Button size="sm" variant="ghost" type="button" disabled={stops.length <= 1} onClick={() => removeStopRow(idx)} aria-label="Remove stop">×</Button>
          </div>
        ))}
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '0.5rem' }}>
        <Button variant="secondary" type="button" onClick={onCancel}>Cancel</Button>
        <Button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Creating Route…' : 'Create Route'}</Button>
      </div>
    </form>
  );
}

function RouteStopsManager({ routeId, onClose }: { routeId: number; onClose: () => void }) {
  const queryClient = useQueryClient();
  const stopsQuery = useQuery({ queryKey: ['transport', 'route-stops', routeId], queryFn: () => transportApi.getRouteStops(routeId) });
  const [stops, setStops] = useState<RouteStopRequest[]>([]);
  const [initialized, setInitialized] = useState(false);
  const [error, setError] = useState('');

  if (stopsQuery.data && !initialized) {
    setStops(stopsQuery.data.map(s => ({ id: s.id, stopName: s.stopName, stopOrder: s.stopOrder, pickupTime: s.pickupTime || '', dropTime: s.dropTime || '' })));
    setInitialized(true);
  }

  const saveMutation = useMutation({
    mutationFn: (updatedStops: RouteStopRequest[]) => transportApi.setRouteStops(routeId, updatedStops),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['transport'] });
      onClose();
    },
    onError: err => setError(err instanceof Error ? err.message : 'Failed to update stops'),
  });

  const moveStop = (index: number, direction: 'up' | 'down') => {
    const targetIndex = direction === 'up' ? index - 1 : index + 1;
    if (targetIndex < 0 || targetIndex >= stops.length) return;
    const reordered = [...stops];
    const [moved] = reordered.splice(index, 1);
    if (!moved) return;
    reordered.splice(targetIndex, 0, moved);
    setStops(reordered.map((s, i) => ({ ...s, stopOrder: i + 1 })));
  };

  const addStop = () => {
    setStops(cur => [...cur, { stopName: '', stopOrder: cur.length + 1, pickupTime: '', dropTime: '' }]);
  };

  const removeStop = (index: number) => {
    setStops(cur => cur.filter((_, i) => i !== index).map((s, i) => ({ ...s, stopOrder: i + 1 })));
  };

  const updateField = (index: number, field: keyof RouteStopRequest, val: string) => {
    setStops(cur => cur.map((s, i) => (i === index ? { ...s, [field]: val } : s)));
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
      {error && <Alert tone="danger">{error}</Alert>}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p className="ds-muted" style={{ margin: 0, fontSize: '13px' }}>Configure ordered pickup and drop-off points for this route.</p>
        <Button size="sm" variant="secondary" onClick={addStop}>+ Add Stop</Button>
      </div>

      <div style={{ display: 'grid', gap: '0.6rem', maxHeight: '350px', overflowY: 'auto' }}>
        {stops.map((s, idx) => (
          <div key={idx} style={{ display: 'grid', gridTemplateColumns: '40px 1fr 120px 120px 60px 36px', gap: '0.5rem', alignItems: 'center' }}>
            <span style={{ fontWeight: 700, textAlign: 'center', color: 'var(--muted)' }}>#{s.stopOrder}</span>
            <Input value={s.stopName} onChange={e => updateField(idx, 'stopName', e.target.value)} placeholder="Stop name" required />
            <Input type="time" value={s.pickupTime || ''} onChange={e => updateField(idx, 'pickupTime', e.target.value)} title="Pickup time" />
            <Input type="time" value={s.dropTime || ''} onChange={e => updateField(idx, 'dropTime', e.target.value)} title="Drop time" />
            <div style={{ display: 'flex', gap: '2px' }}>
              <Button size="sm" variant="ghost" disabled={idx === 0} onClick={() => moveStop(idx, 'up')} title="Move up">▲</Button>
              <Button size="sm" variant="ghost" disabled={idx === stops.length - 1} onClick={() => moveStop(idx, 'down')} title="Move down">▼</Button>
            </div>
            <Button size="sm" variant="ghost" onClick={() => removeStop(idx)} title="Remove stop">×</Button>
          </div>
        ))}
      </div>

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '0.5rem' }}>
        <Button variant="secondary" onClick={onClose}>Cancel</Button>
        <Button disabled={saveMutation.isPending} onClick={() => saveMutation.mutate(stops)}>
          {saveMutation.isPending ? 'Saving…' : 'Save Stops'}
        </Button>
      </div>
    </div>
  );
}

// ==========================================
// 4. STUDENT TRANSPORT ASSIGNMENTS VIEW
// ==========================================

export function TransportAssignmentsView() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [busFilter, setBusFilter] = useState('');
  const [routeFilter, setRouteFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('ACTIVE');
  const [assignModal, setAssignModal] = useState(false);
  const [reassignItem, setReassignItem] = useState<StudentAssignmentResponse | null>(null);
  const [endItemId, setEndItemId] = useState<number | null>(null);
  const [endReason, setEndReason] = useState('');
  const [formError, setFormError] = useState('');

  const canAssign = hasPermission('TRANSPORT:ASSIGN');

  const assignmentsQuery = useQuery({
    queryKey: ['transport', 'assignments', search, busFilter, routeFilter, statusFilter],
    queryFn: () => transportApi.getAssignments({
      search,
      busId: busFilter ? parseInt(busFilter, 10) : undefined,
      routeId: routeFilter ? parseInt(routeFilter, 10) : undefined,
      status: statusFilter || undefined,
    }),
  });

  const busesQuery = useQuery({ queryKey: ['transport', 'buses-active'], queryFn: () => transportApi.getBuses({ status: 'ACTIVE' }) });
  const routesQuery = useQuery({ queryKey: ['transport', 'routes-active'], queryFn: () => transportApi.getRoutes({ status: 'ACTIVE' }) });

  const assignMutation = useMutation({
    mutationFn: transportApi.assignStudent,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['transport'] });
      setAssignModal(false);
      setFormError('');
    },
    onError: err => setFormError(err instanceof Error ? err.message : 'Assignment failed'),
  });

  const reassignMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: ReassignStudentRequest }) => transportApi.reassignStudent(id, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['transport'] });
      setReassignItem(null);
      setFormError('');
    },
    onError: err => setFormError(err instanceof Error ? err.message : 'Reassignment failed'),
  });

  const endMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) => transportApi.endAssignment(id, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['transport'] });
      setEndItemId(null);
      setEndReason('');
    },
    onError: err => setFormError(err instanceof Error ? err.message : 'Failed to end assignment'),
  });

  const columns = [
    { key: 'studentName', header: 'Student', render: (a: StudentAssignmentResponse) => <div><strong>{a.studentName}</strong><div className="ds-muted" style={{ fontSize: '11px' }}>{a.admissionNo}</div></div> },
    { key: 'busName', header: 'Assigned Bus', render: (a: StudentAssignmentResponse) => <div><strong>{a.busName}</strong><div className="ds-muted" style={{ fontSize: '11px' }}>{a.registrationNo}</div></div> },
    { key: 'routeName', header: 'Route' },
    { key: 'pickupStopName', header: 'Pickup Stop', render: (a: StudentAssignmentResponse) => a.pickupStopName ? <div>{a.pickupStopName}{a.pickupTime && <span className="ds-muted" style={{ fontSize: '11px', marginLeft: '6px' }}>{a.pickupTime}</span>}</div> : <span className="ds-muted">—</span> },
    { key: 'dropStopName', header: 'Drop-off Stop', render: (a: StudentAssignmentResponse) => a.dropStopName ? <div>{a.dropStopName}{a.dropTime && <span className="ds-muted" style={{ fontSize: '11px', marginLeft: '6px' }}>{a.dropTime}</span>}</div> : <span className="ds-muted">—</span> },
    { key: 'startDate', header: 'Assigned Date' },
    { key: 'status', header: 'Status', render: (a: StudentAssignmentResponse) => <StatusBadge status={a.status} /> },
    {
      key: 'actions',
      header: 'Actions',
      render: (a: StudentAssignmentResponse) => (
        canAssign && a.status === 'ACTIVE' ? (
          <div style={{ display: 'flex', gap: '0.4rem' }}>
            <Button size="sm" variant="secondary" onClick={() => { setFormError(''); setReassignItem(a); }}>Reassign</Button>
            <Button size="sm" variant="danger" onClick={() => { setEndReason(''); setEndItemId(a.id); }}>End</Button>
          </div>
        ) : <span className="ds-muted">—</span>
      ),
    },
  ];

  return (
    <div className="transport-container">
      <div className="transport-filter-row">
        <div className="transport-filter-inputs">
          <SearchInput
            placeholder="Search student, admission no, bus…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            style={{ width: '260px' }}
          />
          <Select value={busFilter} onChange={e => setBusFilter(e.target.value)} style={{ width: '160px' }}>
            <option value="">All Buses</option>
            {busesQuery.data?.map(b => (
              <option key={b.id} value={b.id}>{b.busName} ({b.availableSeats} free)</option>
            ))}
          </Select>
          <Select value={routeFilter} onChange={e => setRouteFilter(e.target.value)} style={{ width: '160px' }}>
            <option value="">All Routes</option>
            {routesQuery.data?.map(r => (
              <option key={r.id} value={r.id}>{r.routeName}</option>
            ))}
          </Select>
          <Select value={statusFilter} onChange={e => setStatusFilter(e.target.value)} style={{ width: '130px' }}>
            <option value="">All Statuses</option>
            <option value="ACTIVE">Active</option>
            <option value="ENDED">Ended</option>
            <option value="TRANSFERRED">Transferred</option>
          </Select>
        </div>
        {canAssign && <Button onClick={() => { setFormError(''); setAssignModal(true); }}>Assign Transport</Button>}
      </div>

      {formError && !assignModal && !reassignItem && !endItemId && (
        <div style={{ marginBottom: '1rem' }}><Alert tone="danger">{formError}</Alert></div>
      )}

      <Card>
        {assignmentsQuery.isLoading ? (
          <EmptyState title="Loading transport allocations…" compact />
        ) : assignmentsQuery.error ? (
          <ErrorState title="Could not load assignments" onRetry={() => void assignmentsQuery.refetch()} />
        ) : (
          <DataTable<StudentAssignmentResponse>
            columns={columns}
            rows={assignmentsQuery.data ?? []}
            rowKey={a => String(a.id)}
            caption="Student transport allocations table"
            emptyMessage="No student transport assignments found"
          />
        )}
      </Card>

      {/* ASSIGN STUDENT MODAL */}
      <Modal open={assignModal} onClose={() => setAssignModal(false)} title="Assign Student to Bus & Route" size="md">
        <AssignStudentForm
          buses={busesQuery.data ?? []}
          routes={routesQuery.data ?? []}
          error={formError}
          isSubmitting={assignMutation.isPending}
          onSubmit={data => assignMutation.mutate(data)}
          onCancel={() => setAssignModal(false)}
        />
      </Modal>

      {/* REASSIGN STUDENT MODAL */}
      <Modal open={reassignItem !== null} onClose={() => setReassignItem(null)} title={`Reassign Student: ${reassignItem?.studentName}`} size="md">
        {reassignItem && (
          <ReassignStudentForm
            current={reassignItem}
            buses={busesQuery.data ?? []}
            routes={routesQuery.data ?? []}
            error={formError}
            isSubmitting={reassignMutation.isPending}
            onSubmit={data => reassignMutation.mutate({ id: reassignItem.id, data })}
            onCancel={() => setReassignItem(null)}
          />
        )}
      </Modal>

      {/* END ASSIGNMENT DIALOG */}
      <Modal open={endItemId !== null} onClose={() => setEndItemId(null)} title="End Transport Allocation" size="sm">
        <div style={{ display: 'grid', gap: '1rem' }}>
          <p className="ds-modal-copy">Are you sure you want to end this transport assignment? The seat will be released back to the bus capacity.</p>
          <FormField label="Reason for ending">
            <Input value={endReason} onChange={e => setEndReason(e.target.value)} placeholder="e.g. Student relocated or shifted to hostel" />
          </FormField>
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
            <Button variant="secondary" onClick={() => setEndItemId(null)}>Cancel</Button>
            <Button variant="danger" disabled={endMutation.isPending} onClick={() => endItemId && endMutation.mutate({ id: endItemId, reason: endReason })}>
              {endMutation.isPending ? 'Ending…' : 'End Allocation'}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}

function AssignStudentForm({
  buses,
  routes,
  error,
  isSubmitting,
  onSubmit,
  onCancel,
}: {
  buses: BusResponse[];
  routes: RouteResponse[];
  error?: string;
  isSubmitting: boolean;
  onSubmit: (data: AssignStudentRequest) => void;
  onCancel: () => void;
}) {
  const [studentSearch, setStudentSearch] = useState('');
  const [selectedStudentId, setSelectedStudentId] = useState<number | null>(null);
  const [busId, setBusId] = useState('');
  const [routeId, setRouteId] = useState('');
  const [pickupStopId, setPickupStopId] = useState('');
  const [dropStopId, setDropStopId] = useState('');
  const [startDate, setStartDate] = useState(new Date().toISOString().slice(0, 10));

  const studentsQuery = useQuery({
    queryKey: ['transport', 'student-search', studentSearch],
    queryFn: () => transportApi.getStudentOptions(studentSearch),
  });

  const selectedBus = buses.find(b => b.id === parseInt(busId, 10));

  // Auto-set route when bus is picked
  const handleBusChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const val = e.target.value;
    setBusId(val);
    const bus = buses.find(b => b.id === parseInt(val, 10));
    if (bus?.currentRouteId) {
      setRouteId(String(bus.currentRouteId));
    }
  };

  const stopsQuery = useQuery({
    queryKey: ['transport', 'route-stops', routeId],
    queryFn: () => transportApi.getRouteStops(parseInt(routeId, 10)),
    enabled: Boolean(routeId),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedStudentId) return;
    onSubmit({
      studentId: selectedStudentId,
      busId: parseInt(busId, 10),
      routeId: parseInt(routeId, 10),
      pickupStopId: pickupStopId ? parseInt(pickupStopId, 10) : null,
      dropStopId: dropStopId ? parseInt(dropStopId, 10) : null,
      startDate,
    });
  };

  return (
    <form onSubmit={handleSubmit} style={{ display: 'grid', gap: '1rem' }}>
      {error && <Alert tone="danger">{error}</Alert>}

      <FormField label="Search & Select Student" required>
        <div>
          <Input
            value={studentSearch}
            onChange={e => setStudentSearch(e.target.value)}
            placeholder="Type name or admission number…"
            style={{ marginBottom: '0.4rem' }}
          />
          <Select
            value={selectedStudentId ? String(selectedStudentId) : ''}
            onChange={e => setSelectedStudentId(e.target.value ? parseInt(e.target.value, 10) : null)}
            required
          >
            <option value="">-- Choose student --</option>
            {studentsQuery.data?.map(s => (
              <option key={s.id} value={s.id} disabled={s.currentlyAssigned}>
                {s.name} ({s.admissionNo}) {s.currentlyAssigned ? `[Already on ${s.currentBusName}]` : ''}
              </option>
            ))}
          </Select>
        </div>
      </FormField>

      <FormField label="Select Bus" required hint={selectedBus ? `${selectedBus.availableSeats} of ${selectedBus.capacity} seats available` : undefined}>
        <Select value={busId} onChange={handleBusChange} required>
          <option value="">-- Select bus --</option>
          {buses.map(b => (
            <option key={b.id} value={b.id} disabled={b.availableSeats <= 0}>
              {b.busName} ({b.registrationNo}) — {b.availableSeats <= 0 ? 'FULL' : `${b.availableSeats} seats free`}
            </option>
          ))}
        </Select>
      </FormField>

      {selectedBus && selectedBus.availableSeats <= 0 && (
        <Alert tone="warning">This bus is at full capacity. Please select another bus.</Alert>
      )}

      <FormField label="Select Route" required>
        <Select value={routeId} onChange={e => setRouteId(e.target.value)} required>
          <option value="">-- Select route --</option>
          {routes.map(r => (
            <option key={r.id} value={r.id}>{r.routeName} ({r.routeCode})</option>
          ))}
        </Select>
      </FormField>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
        <FormField label="Pickup Stop">
          <Select value={pickupStopId} onChange={e => setPickupStopId(e.target.value)} disabled={!routeId}>
            <option value="">-- Select stop --</option>
            {stopsQuery.data?.map(s => (
              <option key={s.id} value={s.id}>#{s.stopOrder} {s.stopName} {s.pickupTime ? `(${s.pickupTime})` : ''}</option>
            ))}
          </Select>
        </FormField>

        <FormField label="Drop-off Stop">
          <Select value={dropStopId} onChange={e => setDropStopId(e.target.value)} disabled={!routeId}>
            <option value="">-- Select stop --</option>
            {stopsQuery.data?.map(s => (
              <option key={s.id} value={s.id}>#{s.stopOrder} {s.stopName} {s.dropTime ? `(${s.dropTime})` : ''}</option>
            ))}
          </Select>
        </FormField>
      </div>

      <FormField label="Allocation Start Date" required>
        <Input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} required />
      </FormField>

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '0.5rem' }}>
        <Button variant="secondary" type="button" onClick={onCancel}>Cancel</Button>
        <Button type="submit" disabled={isSubmitting || !selectedStudentId || !busId || (selectedBus?.availableSeats ?? 0) <= 0}>
          {isSubmitting ? 'Allocating…' : 'Allocate Seat'}
        </Button>
      </div>
    </form>
  );
}

function ReassignStudentForm({
  current,
  buses,
  routes,
  error,
  isSubmitting,
  onSubmit,
  onCancel,
}: {
  current: StudentAssignmentResponse;
  buses: BusResponse[];
  routes: RouteResponse[];
  error?: string;
  isSubmitting: boolean;
  onSubmit: (data: ReassignStudentRequest) => void;
  onCancel: () => void;
}) {
  const [busId, setBusId] = useState(String(current.busId));
  const [routeId, setRouteId] = useState(String(current.routeId));
  const [pickupStopId, setPickupStopId] = useState(current.pickupStopId ? String(current.pickupStopId) : '');
  const [dropStopId, setDropStopId] = useState(current.dropStopId ? String(current.dropStopId) : '');
  const [reason, setReason] = useState('');

  const selectedBus = buses.find(b => b.id === parseInt(busId, 10));

  const handleBusChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const val = e.target.value;
    setBusId(val);
    const bus = buses.find(b => b.id === parseInt(val, 10));
    if (bus?.currentRouteId) {
      setRouteId(String(bus.currentRouteId));
    }
  };

  const stopsQuery = useQuery({
    queryKey: ['transport', 'route-stops', routeId],
    queryFn: () => transportApi.getRouteStops(parseInt(routeId, 10)),
    enabled: Boolean(routeId),
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSubmit({
      busId: parseInt(busId, 10),
      routeId: parseInt(routeId, 10),
      pickupStopId: pickupStopId ? parseInt(pickupStopId, 10) : null,
      dropStopId: dropStopId ? parseInt(dropStopId, 10) : null,
      reason: reason.trim() || undefined,
    });
  };

  return (
    <form onSubmit={handleSubmit} style={{ display: 'grid', gap: '1rem' }}>
      {error && <Alert tone="danger">{error}</Alert>}
      <div style={{ background: 'var(--surface-muted)', padding: '0.75rem', borderRadius: '8px', fontSize: '13px' }}>
        <strong>Currently:</strong> {current.studentName} ({current.admissionNo}) on <strong>{current.busName}</strong> • {current.routeName}
      </div>

      <FormField label="Target Bus" required hint={selectedBus ? `${selectedBus.availableSeats} available seats` : undefined}>
        <Select value={busId} onChange={handleBusChange} required>
          {buses.map(b => (
            <option key={b.id} value={b.id} disabled={b.id !== current.busId && b.availableSeats <= 0}>
              {b.busName} ({b.registrationNo}) {b.id === current.busId ? '[Current]' : `— ${b.availableSeats} free`}
            </option>
          ))}
        </Select>
      </FormField>

      <FormField label="Target Route" required>
        <Select value={routeId} onChange={e => setRouteId(e.target.value)} required>
          {routes.map(r => (
            <option key={r.id} value={r.id}>{r.routeName} ({r.routeCode})</option>
          ))}
        </Select>
      </FormField>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
        <FormField label="Pickup Stop">
          <Select value={pickupStopId} onChange={e => setPickupStopId(e.target.value)}>
            <option value="">-- Select stop --</option>
            {stopsQuery.data?.map(s => (
              <option key={s.id} value={s.id}>#{s.stopOrder} {s.stopName} {s.pickupTime ? `(${s.pickupTime})` : ''}</option>
            ))}
          </Select>
        </FormField>

        <FormField label="Drop-off Stop">
          <Select value={dropStopId} onChange={e => setDropStopId(e.target.value)}>
            <option value="">-- Select stop --</option>
            {stopsQuery.data?.map(s => (
              <option key={s.id} value={s.id}>#{s.stopOrder} {s.stopName} {s.dropTime ? `(${s.dropTime})` : ''}</option>
            ))}
          </Select>
        </FormField>
      </div>

      <FormField label="Reason for Reassignment" required>
        <Input value={reason} onChange={e => setReason(e.target.value)} placeholder="e.g. Change in residence, bus route adjustment" required />
      </FormField>

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '0.5rem' }}>
        <Button variant="secondary" type="button" onClick={onCancel}>Cancel</Button>
        <Button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Reassigning…' : 'Confirm Reassignment'}</Button>
      </div>
    </form>
  );
}

// ==========================================
// 5. ASSIGNMENT HISTORY VIEW
// ==========================================

export function TransportHistoryView() {
  const [studentId, setStudentId] = useState('');
  const historyQuery = useQuery({
    queryKey: ['transport', 'history', studentId],
    queryFn: () => transportApi.getHistory({ studentId: studentId ? parseInt(studentId, 10) : undefined }),
  });

  const columns = [
    { key: 'studentName', header: 'Student', render: (r: AssignmentHistoryResponse) => <div><strong>{r.studentName}</strong><div className="ds-muted" style={{ fontSize: '11px' }}>{r.admissionNo}</div></div> },
    { key: 'busName', header: 'Bus' },
    { key: 'routeName', header: 'Route' },
    {
      key: 'action',
      header: 'Action',
      render: (r: AssignmentHistoryResponse) => (
        <Badge tone={r.action === 'ASSIGNED' ? 'success' : r.action === 'REASSIGNED' ? 'info' : 'warning'}>
          {r.action}
        </Badge>
      ),
    },
    { key: 'reason', header: 'Reason', render: (r: AssignmentHistoryResponse) => r.reason || '—' },
    { key: 'changerName', header: 'Changed By' },
    { key: 'changedAt', header: 'Date & Time', render: (r: AssignmentHistoryResponse) => new Date(r.changedAt).toLocaleString() },
  ];

  return (
    <div className="transport-container">
      <Card>
        <div style={{ marginBottom: '1rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <h3 style={{ margin: 0, fontSize: '1rem', fontWeight: 700 }}>Transport Assignment Audit History</h3>
            <p className="ds-muted" style={{ margin: '2px 0 0', fontSize: '12px' }}>Complete log of allocations, transfers, and terminations</p>
          </div>
          <Input
            type="search"
            placeholder="Filter by Student ID…"
            value={studentId}
            onChange={e => setStudentId(e.target.value)}
            style={{ width: '200px' }}
          />
        </div>

        {historyQuery.isLoading ? (
          <EmptyState title="Loading history records…" compact />
        ) : historyQuery.error ? (
          <ErrorState title="Could not load history" onRetry={() => void historyQuery.refetch()} />
        ) : (
          <DataTable<AssignmentHistoryResponse>
            columns={columns}
            rows={historyQuery.data ?? []}
            rowKey={r => String(r.id)}
            caption="Transport assignment history log"
            emptyMessage="No transport history records found"
          />
        )}
      </Card>
    </div>
  );
}

// ==========================================
// 6. STUDENT SELF-VIEW
// ==========================================

export function StudentTransportViewComponent() {
  const query = useQuery({
    queryKey: ['transport', 'my-assignment'],
    queryFn: transportApi.getMyAssignment,
  });

  if (query.isLoading) return <EmptyState title="Loading your transport details…" compact />;
  if (query.error) return <ErrorState title="Unable to retrieve transport details" onRetry={() => void query.refetch()} />;

  const data = query.data;
  if (!data || !data.assigned) {
    return (
      <Card>
        <EmptyState
          title="No transport allocated"
          description={data?.message || 'No bus or route is currently assigned to you.'}
        />
      </Card>
    );
  }

  return (
    <div className="transport-container">
      <div className="transport-card-highlight">
        <div className="transport-card-header">
          <div>
            <h3>{data.busName}</h3>
            <p>Registration: <strong>{data.registrationNo}</strong> • Route: <strong>{data.routeName}</strong></p>
          </div>
          <Badge tone="success">{data.status || 'Currently Assigned'}</Badge>
        </div>

        <div className="transport-info-grid">
          <div className="transport-info-item">
            <span className="transport-info-label">Assigned Pickup Stop</span>
            <span className="transport-info-value">{data.pickupStopName || 'Default Campus Gate'}</span>
            {data.pickupTime && <span className="ds-muted" style={{ fontSize: '12px' }}>Time: {data.pickupTime}</span>}
          </div>
          <div className="transport-info-item">
            <span className="transport-info-label">Assigned Drop-off Stop</span>
            <span className="transport-info-value">{data.dropStopName || 'Default Campus Gate'}</span>
            {data.dropTime && <span className="ds-muted" style={{ fontSize: '12px' }}>Time: {data.dropTime}</span>}
          </div>
          <div className="transport-info-item">
            <span className="transport-info-label">Driver</span>
            <span className="transport-info-value">{data.driverName || 'Designated Driver'}</span>
          </div>
          <div className="transport-info-item">
            <span className="transport-info-label">Conductor</span>
            <span className="transport-info-value">{data.conductorName || 'Designated Conductor'}</span>
          </div>
        </div>
      </div>

      {data.routeStops && data.routeStops.length > 0 && (
        <Card>
          <h4 style={{ margin: '0 0 0.75rem', fontSize: '0.95rem', fontWeight: 700 }}>Your Route Stops</h4>
          <div className="transport-stops-list">
            {data.routeStops.map(stop => {
              const isPickup = stop.stopName === data.pickupStopName;
              const isDrop = stop.stopName === data.dropStopName;
              return (
                <div
                  key={stop.id}
                  className="transport-stop-item"
                  style={isPickup || isDrop ? { borderLeft: '4px solid var(--accent)', background: 'var(--accent-soft)' } : undefined}
                >
                  <span className="transport-stop-order">{stop.stopOrder}</span>
                  <div className="transport-stop-details">
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                      <span className="transport-stop-name">{stop.stopName}</span>
                      {isPickup && <Badge tone="info">Your Pickup</Badge>}
                      {isDrop && <Badge tone="success">Your Drop-off</Badge>}
                    </div>
                    <div className="transport-stop-times">
                      <span>Pickup: {stop.pickupTime || '—'}</span>
                      <span>Drop-off: {stop.dropTime || '—'}</span>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </Card>
      )}
    </div>
  );
}

// ==========================================
// 7. PARENT VIEW
// ==========================================

export function ParentTransportViewComponent() {
  const query = useQuery({
    queryKey: ['transport', 'my-children'],
    queryFn: transportApi.getMyChildren,
  });

  if (query.isLoading) return <EmptyState title="Loading children's transport details…" compact />;
  if (query.error) return <ErrorState title="Unable to retrieve children transport" onRetry={() => void query.refetch()} />;

  const children = query.data || [];
  if (children.length === 0) {
    return (
      <Card>
        <EmptyState
          title="No children linked"
          description="There are no student profiles currently linked to your parent account."
        />
      </Card>
    );
  }

  return (
    <div className="transport-container">
      <div className="transport-children-grid">
        {children.map(child => (
          <div key={child.childId} className="transport-child-card">
            <div className="transport-child-head">
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <div className="transport-child-avatar">{child.childName ? child.childName.charAt(0) : '?'}</div>
                <div>
                  <h3 style={{ margin: 0, fontSize: '1.05rem', fontWeight: 700 }}>{child.childName}</h3>
                  <span className="ds-muted" style={{ fontSize: '11px' }}>Admission No: {child.admissionNo}</span>
                </div>
              </div>
              <Badge tone={child.assigned ? 'success' : 'neutral'}>
                {child.assigned ? 'Assigned' : 'Not Assigned'}
              </Badge>
            </div>

            {child.assigned ? (
              <>
                <div className="transport-info-grid">
                  <div className="transport-info-item">
                    <span className="transport-info-label">Assigned Bus</span>
                    <span className="transport-info-value">{child.busName}</span>
                    <span className="ds-muted" style={{ fontSize: '11px' }}>{child.registrationNo}</span>
                  </div>
                  <div className="transport-info-item">
                    <span className="transport-info-label">Route</span>
                    <span className="transport-info-value">{child.routeName}</span>
                  </div>
                  <div className="transport-info-item">
                    <span className="transport-info-label">Pickup Stop & Time</span>
                    <span className="transport-info-value">{child.pickupStopName || 'Campus Main Gate'}</span>
                    <span className="ds-muted" style={{ fontSize: '11px' }}>{child.pickupTime || '—'}</span>
                  </div>
                  <div className="transport-info-item">
                    <span className="transport-info-label">Drop-off Stop & Time</span>
                    <span className="transport-info-value">{child.dropStopName || 'Campus Main Gate'}</span>
                    <span className="ds-muted" style={{ fontSize: '11px' }}>{child.dropTime || '—'}</span>
                  </div>
                  <div className="transport-info-item">
                    <span className="transport-info-label">Driver</span>
                    <span className="transport-info-value">{child.driverName || 'Designated Driver'}</span>
                  </div>
                  <div className="transport-info-item">
                    <span className="transport-info-label">Conductor</span>
                    <span className="transport-info-value">{child.conductorName || 'Designated Conductor'}</span>
                  </div>
                </div>

                {child.capacity && (
                  <div style={{ fontSize: '12px', background: 'var(--surface-muted)', padding: '0.5rem 0.75rem', borderRadius: '6px' }}>
                    <strong>Bus Status:</strong> {child.capacity} seats | {child.occupancy} assigned | {child.availableSeats} available
                  </div>
                )}
              </>
            ) : (
              <EmptyState
                title="No bus assigned"
                description="No bus or route is currently assigned to this child."
                compact
              />
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

// ==========================================
// 7. DRIVERS & CONDUCTORS VIEW
// ==========================================

export function TransportStaffView() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<'ALL' | 'DRIVER' | 'CONDUCTOR'>('ALL');
  const [busFilter, setBusFilter] = useState<'ALL' | 'ASSIGNED' | 'UNASSIGNED'>('ALL');
  const [staffModal, setStaffModal] = useState(false);
  const [defaultStaffRole, setDefaultStaffRole] = useState<'Bus Driver' | 'Bus Conductor'>('Bus Driver');

  const canCreate = hasPermission('TRANSPORT:MANAGE_STAFF') || hasPermission('TRANSPORT:CREATE');

  const staffQuery = useQuery({
    queryKey: ['transport', 'staff-options'],
    queryFn: () => transportApi.getStaffOptions(),
  });

  const staffList = staffQuery.data ?? [];

  // Strictly filter to transport staff (drivers and conductors only; never teachers or administrative staff)
  const filtered = staffList.filter(s => {
    const isDriver = s.designation && s.designation.toLowerCase().includes('driver');
    const isConductor = s.designation && s.designation.toLowerCase().includes('conductor');

    if (!isDriver && !isConductor) return false;

    if (roleFilter === 'DRIVER' && !isDriver) return false;
    if (roleFilter === 'CONDUCTOR' && !isConductor) return false;

    if (busFilter === 'ASSIGNED' && !s.assignedBusId) return false;
    if (busFilter === 'UNASSIGNED' && s.assignedBusId) return false;

    if (search.trim()) {
      const q = search.toLowerCase();
      const matchName = s.name.toLowerCase().includes(q);
      const matchEmp = s.employeeNo.toLowerCase().includes(q);
      const matchPhone = s.phone && s.phone.toLowerCase().includes(q);
      const matchBus = s.assignedBusName && s.assignedBusName.toLowerCase().includes(q);
      const matchDesig = s.designation && s.designation.toLowerCase().includes(q);
      return matchName || matchEmp || matchPhone || matchBus || matchDesig;
    }
    return true;
  });

  const totalDrivers = staffList.filter(s => s.designation && s.designation.toLowerCase().includes('driver')).length;
  const totalConductors = staffList.filter(s => s.designation && s.designation.toLowerCase().includes('conductor')).length;
  const assignedCount = staffList.filter(s => s.assignedBusId).length;
  const unassignedCount = staffList.filter(s => !s.assignedBusId && ((s.designation && s.designation.toLowerCase().includes('driver')) || (s.designation && s.designation.toLowerCase().includes('conductor')))).length;

  const columns = [
    {
      key: 'name',
      header: 'Staff Personnel',
      render: (s: TransportStaffOption) => {
        const isDriver = s.designation?.toLowerCase().includes('driver');
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <div
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '50%',
                background: isDriver ? 'rgba(16, 185, 129, 0.15)' : 'rgba(59, 130, 246, 0.15)',
                color: isDriver ? 'var(--accent, #10b981)' : '#3b82f6',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 600,
                fontSize: '13px',
              }}
            >
              {isDriver ? '🚗' : '🎫'}
            </div>
            <div>
              <div style={{ fontWeight: 600 }}>{s.name}</div>
              <div style={{ fontSize: '11px', color: 'var(--muted)' }}>Emp ID: {s.employeeNo}</div>
            </div>
          </div>
        );
      },
    },
    {
      key: 'designation',
      header: 'Role / Designation',
      render: (s: TransportStaffOption) => {
        const isDriver = s.designation?.toLowerCase().includes('driver');
        return (
          <Badge tone={isDriver ? 'success' : 'info'}>
            {s.designation}
          </Badge>
        );
      },
    },
    {
      key: 'phone',
      header: 'Phone / Contact',
      render: (s: TransportStaffOption) => (
        <span>{s.phone || <span className="ds-muted">Not provided</span>}</span>
      ),
    },
    {
      key: 'assignedBusName',
      header: 'Assigned School Bus',
      render: (s: TransportStaffOption) =>
        s.assignedBusName ? (
          <span style={{ fontWeight: 500, color: 'var(--text)' }}>
            🚌 {s.assignedBusName}
          </span>
        ) : (
          <span className="ds-muted" style={{ fontStyle: 'italic' }}>
            Unassigned (Standby)
          </span>
        ),
    },
    {
      key: 'status',
      header: 'Status',
      render: () => <StatusBadge status="ACTIVE" />,
    },
  ];

  return (
    <div className="transport-container">
      <div className="transport-stats-grid">
        <StatCard label="Bus Drivers" value={totalDrivers} icon="🚗" tone="neutral" />
        <StatCard label="Bus Conductors" value={totalConductors} icon="🎫" tone="neutral" />
        <StatCard label="Assigned on Active Buses" value={assignedCount} icon="🚌" tone="neutral" />
        <StatCard label="Standby / Unassigned" value={unassignedCount} icon="👥" tone={unassignedCount > 0 ? 'warning' : 'neutral'} />
      </div>

      <div className="transport-filter-row">
        <div className="transport-filter-inputs">
          <SearchInput
            placeholder="Search driver, conductor, emp ID, bus…"
            value={search}
            onChange={e => setSearch(e.target.value)}
            style={{ width: '280px' }}
          />
          <Select
            value={roleFilter}
            onChange={e => setRoleFilter(e.target.value as any)}
            style={{ width: '160px' }}
          >
            <option value="ALL">All Roles</option>
            <option value="DRIVER">Drivers Only</option>
            <option value="CONDUCTOR">Conductors Only</option>
          </Select>
          <Select
            value={busFilter}
            onChange={e => setBusFilter(e.target.value as any)}
            style={{ width: '160px' }}
          >
            <option value="ALL">All Assignments</option>
            <option value="ASSIGNED">Assigned to Bus</option>
            <option value="UNASSIGNED">Unassigned (Standby)</option>
          </Select>
        </div>

        <div style={{ display: 'flex', gap: '0.5rem' }}>
          {canCreate && (
            <>
              <Button
                variant="secondary"
                onClick={() => {
                  setDefaultStaffRole('Bus Driver');
                  setStaffModal(true);
                }}
              >
                + Add Driver
              </Button>
              <Button
                onClick={() => {
                  setDefaultStaffRole('Bus Conductor');
                  setStaffModal(true);
                }}
              >
                + Add Conductor
              </Button>
            </>
          )}
        </div>
      </div>

      <Card>
        {staffQuery.isLoading ? (
          <EmptyState title="Loading transport staff…" compact />
        ) : staffQuery.error ? (
          <ErrorState title="Could not load transport personnel" onRetry={() => void staffQuery.refetch()} />
        ) : (
          <DataTable<TransportStaffOption>
            columns={columns}
            rows={filtered}
            rowKey={s => String(s.id)}
            caption="Transport drivers and conductors table"
            emptyMessage={
              search || roleFilter !== 'ALL' || busFilter !== 'ALL'
                ? 'No transport personnel match your filter criteria'
                : 'No transport personnel registered yet'
            }
          />
        )}
      </Card>

      <CreateStaffModal
        isOpen={staffModal}
        onClose={() => setStaffModal(false)}
        defaultDesignation={defaultStaffRole}
        onSuccess={() => void queryClient.invalidateQueries({ queryKey: ['transport'] })}
      />
    </div>
  );
}
