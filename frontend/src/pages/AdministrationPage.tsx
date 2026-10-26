import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../auth/AuthProvider';
import { administrationApi } from '../api/administration';
import type {
  UserItem,
  RoleItem,
  PermissionItem,
  AuditLogItem,
  ImportResult,
} from '../api/administration';
import {
  PageHeader,
  Tabs,
  Card,
  DataTable,
  Button,
  Input,
  Select,
  FormField,
  StatusBadge,
  Badge,
  Modal,
  Pagination,
  Alert,
  ConfirmDialog,
  EmptyState,
} from '../components';
import './administration-pages.css';

export function AdministrationPage() {
  const { hasPermission } = useAuth();

  const allTabs = [
    { id: 'users' as const, label: <><span className="ds-tab-icon" aria-hidden="true">♟</span>Users</>, content: <UsersTab />, visible: hasPermission('USER:READ') || hasPermission('USER:MANAGE') || hasPermission('USER:CREATE') },
    { id: 'roles' as const, label: <><span className="ds-tab-icon" aria-hidden="true">⚑</span>Roles</>, content: <RolesTab />, visible: hasPermission('RBAC:ROLE_READ') || hasPermission('RBAC:ROLE_MANAGE') },
    { id: 'permissions' as const, label: <><span className="ds-tab-icon" aria-hidden="true">◈</span>Permissions</>, content: <PermissionsTab />, visible: hasPermission('RBAC:PERMISSION_READ') },
    { id: 'audit' as const, label: <><span className="ds-tab-icon" aria-hidden="true">▤</span>Audit Logs</>, content: <AuditLogsTab />, visible: hasPermission('AUDIT:READ') },
    { id: 'import' as const, label: <><span className="ds-tab-icon" aria-hidden="true">⇅</span>Import & Export</>, content: <ImportExportTab />, visible: hasPermission('USER:IMPORT') || hasPermission('USER:MANAGE') },
  ];

  const tabs = allTabs.filter(t => t.visible);
  const defaultTab = tabs[0]?.id ?? 'users';
  const [activeTab, setActiveTab] = useState<'users' | 'roles' | 'permissions' | 'audit' | 'import'>(defaultTab);

  const currentTab = tabs.some(t => t.id === activeTab) ? activeTab : tabs[0]?.id;

  return (
    <div className="admin-container">
      <PageHeader
        eyebrow="SYSTEM GOVERNANCE"
        title="Administration"
        description="Manage users, access control, audit records, and administrative imports."
      />
      <div className="admin-tabs">
        {tabs.length > 0 && currentTab ? (
          <Tabs tabs={tabs} value={currentTab} onChange={(id) => setActiveTab(id as typeof activeTab)} label="Administration sections" />
        ) : (
          <Card><EmptyState title="No administrative sections accessible" description="Your account does not have permission to access these administrative modules." /></Card>
        )}
      </div>
    </div>
  );
}

// ==========================================
// 1. USERS TAB
// ==========================================
function UsersTab() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [roleFilter, setRoleFilter] = useState('');
  const [page, setPage] = useState(0);

  // Modals
  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState<UserItem | null>(null);
  const [statusUser, setStatusUser] = useState<UserItem | null>(null);
  const [targetStatus, setTargetStatus] = useState<'ACTIVE' | 'LOCKED' | 'DISABLED'>('ACTIVE');
  const [rolesUser, setRolesUser] = useState<UserItem | null>(null);
  const [selectedRoles, setSelectedRoles] = useState<string[]>([]);

  // Fetch Users
  const { data: userPage, isLoading, error } = useQuery({
    queryKey: ['admin-users', search, statusFilter, roleFilter, page],
    queryFn: () => administrationApi.listUsers({ search, status: statusFilter, role: roleFilter, page, size: 10 }),
  });

  // Fetch Roles for filters & assignment
  const { data: roles = [] } = useQuery({
    queryKey: ['admin-roles'],
    queryFn: administrationApi.listRoles,
  });

  // Create User Mutation
  const [createForm, setCreateForm] = useState({
    username: '',
    email: '',
    password: '',
    fullName: '',
    phone: '',
    roleCodes: [] as string[],
  });
  const [formError, setFormError] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: administrationApi.createUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      setCreateOpen(false);
      setCreateForm({ username: '', email: '', password: '', fullName: '', phone: '', roleCodes: [] });
      setFormError(null);
    },
    onError: (err: Error) => setFormError(err.message),
  });

  // Update User Mutation
  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: Parameters<typeof administrationApi.updateUser>[1] }) =>
      administrationApi.updateUser(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      setEditUser(null);
    },
    onError: (err: Error) => setFormError(err.message),
  });

  // Status Mutation
  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: 'ACTIVE' | 'LOCKED' | 'DISABLED' }) =>
      administrationApi.updateUserStatus(id, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      setStatusUser(null);
    },
  });

  // Roles Mutation
  const rolesMutation = useMutation({
    mutationFn: ({ id, roleCodes }: { id: number; roleCodes: string[] }) =>
      administrationApi.updateUserRoles(id, roleCodes),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      setRolesUser(null);
    },
  });

  const columns = [
    { key: 'id', header: 'ID' },
    {
      key: 'username',
      header: 'User',
      render: (u: UserItem) => (
        <div>
          <strong>{u.username}</strong>
          {u.fullName && <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{u.fullName}</div>}
        </div>
      ),
    },
    { key: 'email', header: 'Email' },
    { key: 'phone', header: 'Phone', render: (u: UserItem) => u.phone || '—' },
    {
      key: 'status',
      header: 'Status',
      render: (u: UserItem) => (
        <StatusBadge
          status={u.status}
          tone={u.status === 'ACTIVE' ? 'success' : u.status === 'LOCKED' ? 'warning' : 'danger'}
        />
      ),
    },
    {
      key: 'roles',
      header: 'Roles',
      render: (u: UserItem) => (
        <div className="admin-badge-list">
          {u.roles.length > 0 ? (
            u.roles.map((r) => <Badge key={r} tone="info">{r}</Badge>)
          ) : (
            <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>None</span>
          )}
        </div>
      ),
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (u: UserItem) => (
        <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
          {hasPermission('USER:UPDATE') && (
            <Button
              size="sm"
              variant="secondary"
              onClick={() => {
                setEditUser(u);
                setFormError(null);
              }}
            >
              Edit
            </Button>
          )}
          {hasPermission('RBAC:USER_ROLE_UPDATE') && (
            <Button
              size="sm"
              variant="secondary"
              onClick={() => {
                setRolesUser(u);
                setSelectedRoles(u.roles);
              }}
            >
              Roles
            </Button>
          )}
          {hasPermission('USER:MANAGE') && (
            <Button
              size="sm"
              variant={u.status === 'ACTIVE' ? 'ghost' : 'primary'}
              onClick={() => {
                setStatusUser(u);
                setTargetStatus(u.status === 'ACTIVE' ? 'LOCKED' : 'ACTIVE');
              }}
            >
              {u.status === 'ACTIVE' ? 'Lock/Disable' : 'Activate'}
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="admin-card-section">
      <div className="admin-filter-row">
        <Input
          placeholder="Search username, email, name..."
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          style={{ minWidth: 260 }}
        />
        <Select
          value={statusFilter}
          onChange={(e) => {
            setStatusFilter(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All Statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="LOCKED">Locked</option>
          <option value="DISABLED">Disabled</option>
        </Select>
        <Select
          value={roleFilter}
          onChange={(e) => {
            setRoleFilter(e.target.value);
            setPage(0);
          }}
        >
          <option value="">All Roles</option>
          {roles.map((r) => (
            <option key={r.code} value={r.code}>
              {r.name} ({r.code})
            </option>
          ))}
        </Select>
        <div style={{ marginLeft: 'auto', display: 'flex', gap: '0.5rem' }}>
          {hasPermission('USER:EXPORT') && (
            <Button variant="secondary" onClick={() => administrationApi.exportUsers()}>
              Export Users
            </Button>
          )}
          {hasPermission('USER:CREATE') && (
            <Button variant="primary" onClick={() => { setCreateOpen(true); setFormError(null); }}>
              + Add User
            </Button>
          )}
        </div>
      </div>

      {error ? (
        <Alert tone="danger" title="Error loading users">
          {(error as Error).message}
        </Alert>
      ) : (
        <DataTable
          columns={columns}
          rows={userPage?.content ?? []}
          rowKey={(u) => String(u.id)}
          caption="System Users Table"
          emptyMessage={isLoading ? 'Loading users...' : 'No users match the criteria.'}
        />
      )}

      {userPage && userPage.totalPages > 1 && (
        <Pagination
          page={page + 1}
          pageCount={userPage.totalPages}
          onPageChange={(p) => setPage(p - 1)}
          itemLabel="page"
        />
      )}

      {/* CREATE USER MODAL */}
      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="Create New User Account"
        footer={
          <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', width: '100%' }}>
            <Button variant="secondary" onClick={() => setCreateOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              disabled={createMutation.isPending || !createForm.username || !createForm.email || !createForm.password}
              onClick={() => createMutation.mutate(createForm)}
            >
              {createMutation.isPending ? 'Creating...' : 'Create Account'}
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          {formError && <Alert tone="danger">{formError}</Alert>}
          <FormField label="Username" required>
            <Input
              value={createForm.username}
              onChange={(e) => setCreateForm({ ...createForm, username: e.target.value })}
              placeholder="e.g. sarah.smith"
            />
          </FormField>
          <FormField label="Email" required>
            <Input
              type="email"
              value={createForm.email}
              onChange={(e) => setCreateForm({ ...createForm, email: e.target.value })}
              placeholder="sarah.smith@school.edu"
            />
          </FormField>
          <FormField label="Password" required hint="At least 8 characters">
            <Input
              type="password"
              value={createForm.password}
              onChange={(e) => setCreateForm({ ...createForm, password: e.target.value })}
            />
          </FormField>
          <FormField label="Full Name">
            <Input
              value={createForm.fullName}
              onChange={(e) => setCreateForm({ ...createForm, fullName: e.target.value })}
              placeholder="Sarah Smith"
            />
          </FormField>
          <FormField label="Phone Number">
            <Input
              value={createForm.phone}
              onChange={(e) => setCreateForm({ ...createForm, phone: e.target.value })}
              placeholder="+1 555-0123"
            />
          </FormField>
          <FormField label="Initial Roles">
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.5rem', maxHeight: 150, overflowY: 'auto', padding: '0.5rem', border: '1px solid var(--border-color)', borderRadius: '6px' }}>
              {roles.map((r) => (
                <label key={r.code} style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.85rem', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={createForm.roleCodes.includes(r.code)}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setCreateForm({ ...createForm, roleCodes: [...createForm.roleCodes, r.code] });
                      } else {
                        setCreateForm({ ...createForm, roleCodes: createForm.roleCodes.filter((c) => c !== r.code) });
                      }
                    }}
                  />
                  <span>{r.name}</span>
                </label>
              ))}
            </div>
          </FormField>
        </div>
      </Modal>

      {/* EDIT USER MODAL */}
      {editUser && (
        <Modal
          open={Boolean(editUser)}
          onClose={() => setEditUser(null)}
          title={`Edit User: ${editUser.username}`}
          footer={
            <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', width: '100%' }}>
              <Button variant="secondary" onClick={() => setEditUser(null)}>
                Cancel
              </Button>
              <Button
                variant="primary"
                disabled={updateMutation.isPending}
                onClick={() =>
                  updateMutation.mutate({
                    id: editUser.id,
                    payload: {
                      email: editUser.email,
                      fullName: editUser.fullName ?? '',
                      phone: editUser.phone ?? '',
                    },
                  })
                }
              >
                {updateMutation.isPending ? 'Saving...' : 'Save Changes'}
              </Button>
            </div>
          }
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            {formError && <Alert tone="danger">{formError}</Alert>}
            <FormField label="Full Name">
              <Input
                value={editUser.fullName ?? ''}
                onChange={(e) => setEditUser({ ...editUser, fullName: e.target.value })}
              />
            </FormField>
            <FormField label="Email">
              <Input
                type="email"
                value={editUser.email}
                onChange={(e) => setEditUser({ ...editUser, email: e.target.value })}
              />
            </FormField>
            <FormField label="Phone">
              <Input
                value={editUser.phone ?? ''}
                onChange={(e) => setEditUser({ ...editUser, phone: e.target.value })}
              />
            </FormField>
          </div>
        </Modal>
      )}

      {/* ROLES ASSIGNMENT MODAL */}
      {rolesUser && (
        <Modal
          open={Boolean(rolesUser)}
          onClose={() => setRolesUser(null)}
          title={`Assign Roles: ${rolesUser.username}`}
          footer={
            <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', width: '100%' }}>
              <Button variant="secondary" onClick={() => setRolesUser(null)}>
                Cancel
              </Button>
              <Button
                variant="primary"
                disabled={rolesMutation.isPending}
                onClick={() =>
                  rolesMutation.mutate({
                    id: rolesUser.id,
                    roleCodes: selectedRoles,
                  })
                }
              >
                {rolesMutation.isPending ? 'Saving...' : 'Save Role Grants'}
              </Button>
            </div>
          }
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              Select the system or custom roles assigned to <strong>{rolesUser.username}</strong>.
            </p>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.5rem', maxHeight: 250, overflowY: 'auto', padding: '0.5rem', border: '1px solid var(--border-color)', borderRadius: '6px' }}>
              {roles.map((r) => (
                <label key={r.code} style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.85rem', cursor: 'pointer' }}>
                  <input
                    type="checkbox"
                    checked={selectedRoles.includes(r.code)}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setSelectedRoles([...selectedRoles, r.code]);
                      } else {
                        setSelectedRoles(selectedRoles.filter((c) => c !== r.code));
                      }
                    }}
                  />
                  <span>
                    <strong>{r.name}</strong> <small style={{ color: 'var(--text-muted)' }}>({r.code})</small>
                  </span>
                </label>
              ))}
            </div>
          </div>
        </Modal>
      )}

      {/* CHANGE STATUS MODAL */}
      {statusUser && (
        <ConfirmDialog
          open={Boolean(statusUser)}
          onClose={() => setStatusUser(null)}
          onConfirm={() =>
            statusMutation.mutate({
              id: statusUser.id,
              status: targetStatus,
            })
          }
          title={`Set Account Status: ${statusUser.username}`}
          confirmLabel={targetStatus === 'ACTIVE' ? 'Activate Account' : targetStatus === 'LOCKED' ? 'Lock Account' : 'Disable Account'}
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <p>
              Current status: <strong>{statusUser.status}</strong>. Choose new status:
            </p>
            <Select value={targetStatus} onChange={(e) => setTargetStatus(e.target.value as typeof targetStatus)}>
              <option value="ACTIVE">ACTIVE (Unlocks and resets failed attempts)</option>
              <option value="LOCKED">LOCKED (Temporarily denies login and revokes active sessions)</option>
              <option value="DISABLED">DISABLED (Permanently disables user access)</option>
            </Select>
          </div>
        </ConfirmDialog>
      )}
    </div>
  );
}

// ==========================================
// 2. ROLES TAB
// ==========================================
function RolesTab() {
  const { hasPermission } = useAuth();
  const queryClient = useQueryClient();

  const [createOpen, setCreateOpen] = useState(false);
  const [createRoleForm, setCreateRoleForm] = useState({ code: '', name: '', description: '', permissionCodes: [] as string[] });
  const [roleError, setRoleError] = useState<string | null>(null);

  const [permsRole, setPermsRole] = useState<RoleItem | null>(null);
  const [selectedPerms, setSelectedPerms] = useState<string[]>([]);

  // Fetch Roles
  const { data: roles = [], isLoading } = useQuery({
    queryKey: ['admin-roles'],
    queryFn: administrationApi.listRoles,
  });

  // Fetch Permissions
  const { data: permissions = [] } = useQuery({
    queryKey: ['admin-permissions'],
    queryFn: administrationApi.listPermissions,
  });

  // Create Role Mutation
  const createRoleMutation = useMutation({
    mutationFn: administrationApi.createRole,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-roles'] });
      setCreateOpen(false);
      setCreateRoleForm({ code: '', name: '', description: '', permissionCodes: [] });
      setRoleError(null);
    },
    onError: (err: Error) => setRoleError(err.message),
  });

  // Update Permissions Mutation
  const permsMutation = useMutation({
    mutationFn: ({ code, perms }: { code: string; perms: string[] }) =>
      administrationApi.updateRolePermissions(code, perms),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-roles'] });
      setPermsRole(null);
    },
  });

  const columns = [
    { key: 'code', header: 'Role Code', render: (r: RoleItem) => <strong>{r.code}</strong> },
    { key: 'name', header: 'Role Name' },
    { key: 'description', header: 'Description', render: (r: RoleItem) => r.description || '—' },
    {
      key: 'isSystem',
      header: 'Type',
      render: (r: RoleItem) => (
        <Badge tone={r.isSystem ? 'neutral' : 'info'}>{r.isSystem ? 'System Role' : 'Custom Role'}</Badge>
      ),
    },
    {
      key: 'permissions',
      header: 'Permissions',
      render: (r: RoleItem) => (
        <span style={{ fontSize: '0.85rem' }}>
          <strong>{r.permissions.length}</strong> grants
        </span>
      ),
    },
    {
      key: 'actions',
      header: 'Actions',
      render: (r: RoleItem) => (
        <div>
          {hasPermission('RBAC:ROLE_PERMISSION_UPDATE') && (
            <Button
              size="sm"
              variant="secondary"
              onClick={() => {
                setPermsRole(r);
                setSelectedPerms(r.permissions);
              }}
            >
              Configure Permissions
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="admin-card-section">
      <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
        {hasPermission('RBAC:ROLE_CREATE') && (
          <Button variant="primary" onClick={() => { setCreateOpen(true); setRoleError(null); }}>
            + Create New Role
          </Button>
        )}
      </div>

      <DataTable
        columns={columns}
        rows={roles}
        rowKey={(r) => r.code}
        caption="System Roles Table"
        emptyMessage={isLoading ? 'Loading roles...' : 'No roles defined.'}
      />

      {/* CREATE ROLE MODAL */}
      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="Create Custom Role"
        footer={
          <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', width: '100%' }}>
            <Button variant="secondary" onClick={() => setCreateOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="primary"
              disabled={createRoleMutation.isPending || !createRoleForm.code || !createRoleForm.name}
              onClick={() => createRoleMutation.mutate(createRoleForm)}
            >
              {createRoleMutation.isPending ? 'Creating...' : 'Create Role'}
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
          {roleError && <Alert tone="danger">{roleError}</Alert>}
          <FormField label="Role Code" required hint="Uppercase letters, numbers, and underscores only">
            <Input
              value={createRoleForm.code}
              onChange={(e) => setCreateRoleForm({ ...createRoleForm, code: e.target.value.toUpperCase() })}
              placeholder="e.g. CAMPUS_DIRECTOR"
            />
          </FormField>
          <FormField label="Role Name" required>
            <Input
              value={createRoleForm.name}
              onChange={(e) => setCreateRoleForm({ ...createRoleForm, name: e.target.value })}
              placeholder="Campus Director"
            />
          </FormField>
          <FormField label="Description">
            <Input
              value={createRoleForm.description}
              onChange={(e) => setCreateRoleForm({ ...createRoleForm, description: e.target.value })}
              placeholder="Oversees campus facilities and daily operations"
            />
          </FormField>
        </div>
      </Modal>

      {/* CONFIGURE PERMISSIONS MODAL */}
      {permsRole && (
        <Modal
          size="lg"
          open={Boolean(permsRole)}
          onClose={() => setPermsRole(null)}
          title={`Configure Permissions for: ${permsRole.name} (${permsRole.code})`}
          footer={
            <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end', width: '100%' }}>
              <Button variant="secondary" onClick={() => setPermsRole(null)}>
                Cancel
              </Button>
              <Button
                variant="primary"
                disabled={permsMutation.isPending}
                onClick={() =>
                  permsMutation.mutate({
                    code: permsRole.code,
                    perms: selectedPerms,
                  })
                }
              >
                {permsMutation.isPending ? 'Saving...' : 'Apply Permissions'}
              </Button>
            </div>
          }
        >
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', maxHeight: '60vh', overflowY: 'auto' }}>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              Check the permissions granted to users with role <strong>{permsRole.code}</strong>.
            </p>
            {Object.entries(
              permissions.reduce((acc, p) => {
                const list = acc[p.module] ?? [];
                list.push(p);
                acc[p.module] = list;
                return acc;
              }, {} as Record<string, PermissionItem[]>)
            ).map(([moduleName, perms]) => (
              <div key={moduleName} style={{ border: '1px solid var(--border-color)', borderRadius: '8px', padding: '0.75rem' }}>
                <div style={{ fontWeight: 600, fontSize: '0.9rem', marginBottom: '0.5rem', display: 'flex', justifyContent: 'space-between' }}>
                  <span>{moduleName} Module</span>
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{perms.length} actions</span>
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.4rem' }}>
                  {perms.map((p) => (
                    <label key={p.code} style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.8rem', cursor: 'pointer' }}>
                      <input
                        type="checkbox"
                        checked={selectedPerms.includes(p.code)}
                        onChange={(e) => {
                          if (e.target.checked) setSelectedPerms([...selectedPerms, p.code]);
                          else setSelectedPerms(selectedPerms.filter((c) => c !== p.code));
                        }}
                      />
                      <span className="role-perm-chip role-perm-chip--active">{p.code}</span>
                    </label>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </Modal>
      )}
    </div>
  );
}

// ==========================================
// 3. PERMISSIONS TAB
// ==========================================
function PermissionsTab() {
  const { data: permissions = [], isLoading, error } = useQuery({
    queryKey: ['admin-permissions'],
    queryFn: administrationApi.listPermissions,
  });

  if (isLoading) return <Card>Loading system permissions catalog...</Card>;
  if (error) return <Alert tone="danger">Failed to load permissions catalog.</Alert>;

  // Group by module
  const grouped = permissions.reduce((acc, p) => {
    const list = acc[p.module] ?? [];
    list.push(p);
    acc[p.module] = list;
    return acc;
  }, {} as Record<string, PermissionItem[]>);

  return (
    <div className="admin-card-section">
      <p style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>
        The system enforces strict <code>MODULE:ACTION</code> permissions via backend authorization tokens.
      </p>
      <div className="perm-matrix-grid">
        {Object.entries(grouped).map(([mod, perms]) => (
          <div key={mod} className="perm-module-card">
            <div className="perm-module-title">
              <span>{mod}</span>
              <Badge tone="neutral">{perms.length}</Badge>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4rem' }}>
              {perms.map((p) => (
                <div key={p.code} className="perm-item-row">
                  <code>{p.code}</code>
                  <span>{p.description || 'No description provided'}</span>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// ==========================================
// 4. AUDIT LOGS TAB
// ==========================================
function AuditLogsTab() {
  const { hasPermission } = useAuth();
  const [actor, setActor] = useState('');
  const [entityType, setEntityType] = useState('');
  const [action, setAction] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [page, setPage] = useState(0);

  const { data: auditPage, isLoading, error } = useQuery({
    queryKey: ['admin-audit', actor, entityType, action, dateFrom, dateTo, page],
    queryFn: () =>
      administrationApi.queryAuditLogs({
        actor,
        entityType,
        action,
        dateFrom,
        dateTo,
        page,
        size: 15,
      }),
  });

  const columns = [
    { key: 'id', header: 'ID' },
    {
      key: 'createdAt',
      header: 'Timestamp',
      render: (log: AuditLogItem) => (
        <span style={{ fontSize: '0.8rem', whiteSpace: 'nowrap' }}>
          {new Date(log.createdAt).toLocaleString()}
        </span>
      ),
    },
    {
      key: 'actorUsername',
      header: 'Actor',
      render: (log: AuditLogItem) => (
        <div>
          <strong>{log.actorUsername || 'SYSTEM'}</strong>
          {log.ipAddress && <div style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>{log.ipAddress}</div>}
        </div>
      ),
    },
    {
      key: 'action',
      header: 'Action',
      render: (log: AuditLogItem) => <Badge tone="info">{log.action}</Badge>,
    },
    {
      key: 'entity',
      header: 'Entity / Target',
      render: (log: AuditLogItem) => (
        <div>
          <span>{log.entityType}</span>
          {log.entityId && <span style={{ marginLeft: '0.35rem', color: 'var(--text-muted)' }}>#{log.entityId}</span>}
        </div>
      ),
    },
    {
      key: 'traceId',
      header: 'Trace ID',
      render: (log: AuditLogItem) => (
        <span className="audit-trace-pill" title={log.traceId ?? undefined}>
          {log.traceId ?? '—'}
        </span>
      ),
    },
    {
      key: 'changes',
      header: 'Change Diff',
      render: (log: AuditLogItem) => (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2rem' }}>
          {log.oldValue && <div className="audit-json-box"><strong>Old:</strong> {log.oldValue}</div>}
          {log.newValue && <div className="audit-json-box"><strong>New:</strong> {log.newValue}</div>}
          {!log.oldValue && !log.newValue && <span style={{ color: 'var(--text-muted)' }}>—</span>}
        </div>
      ),
    },
  ];

  return (
    <div className="admin-card-section">
      <div className="admin-filter-row">
        <Input
          placeholder="Filter actor..."
          value={actor}
          onChange={(e) => { setActor(e.target.value); setPage(0); }}
          style={{ minWidth: 140 }}
        />
        <Select
          value={entityType}
          onChange={(e) => { setEntityType(e.target.value); setPage(0); }}
          style={{ minWidth: 140 }}
        >
          <option value="">All Entities</option>
          <option value="USER">USER</option>
          <option value="ROLE">ROLE</option>
          <option value="SETTING">SETTING</option>
          <option value="AUTH">AUTH</option>
        </Select>
        <Input
          placeholder="Filter action (e.g. USER_CREATED)..."
          value={action}
          onChange={(e) => { setAction(e.target.value); setPage(0); }}
          style={{ minWidth: 180 }}
        />
        <Input
          type="date"
          placeholder="From"
          value={dateFrom}
          onChange={(e) => { setDateFrom(e.target.value); setPage(0); }}
          style={{ minWidth: 140 }}
        />
        <Input
          type="date"
          placeholder="To"
          value={dateTo}
          onChange={(e) => { setDateTo(e.target.value); setPage(0); }}
          style={{ minWidth: 140 }}
        />
        <div style={{ marginLeft: 'auto' }}>
          {hasPermission('AUDIT:EXPORT') && (
            <Button
              variant="secondary"
              onClick={() => administrationApi.exportAuditLogs({ actor, entityType, action, dateFrom, dateTo })}
            >
              Export CSV
            </Button>
          )}
        </div>
      </div>

      {error ? (
        <Alert tone="danger">Failed to load audit logs.</Alert>
      ) : (
        <DataTable
          columns={columns}
          rows={auditPage?.content ?? []}
          rowKey={(l) => String(l.id)}
          caption="Audit Trails Table"
          emptyMessage={isLoading ? 'Loading audit records...' : 'No audit records match the filters.'}
        />
      )}

      {auditPage && auditPage.totalPages > 1 && (
        <Pagination
          page={page + 1}
          pageCount={auditPage.totalPages}
          onPageChange={(p) => setPage(p - 1)}
          itemLabel="page"
        />
      )}
    </div>
  );
}

// ==========================================
// 5. IMPORT & EXPORT TAB
// ==========================================
function ImportExportTab() {
  const { hasPermission } = useAuth();
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [importResult, setImportResult] = useState<ImportResult | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const sampleCsv = `username,email,password,full_name,phone,roles
teacher.jane,jane.doe@school.edu,Password123!,Jane Doe,+15550199,TEACHER
student.alex,alex.turner@school.edu,Password123!,Alex Turner,+15550198,STUDENT`;

  const handleDownloadSample = () => {
    const blob = new Blob([sampleCsv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'user_import_template.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
  };

  const handleUpload = async () => {
    if (!selectedFile) return;
    setIsUploading(true);
    setErrorMsg(null);
    try {
      const res = await administrationApi.importUsers(selectedFile);
      setImportResult(res);
      setSelectedFile(null);
    } catch (err: unknown) {
      setErrorMsg((err as Error).message);
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div className="admin-card-section">
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
        {/* IMPORT USERS */}
        <Card style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div>
            <h3>Bulk User CSV Import</h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              Upload a comma-separated values (.csv) file with column headers: <code>username, email, password, full_name, phone, roles</code>.
            </p>
          </div>

          <div
            className="import-dropzone"
            onClick={() => document.getElementById('csv-file-picker')?.click()}
          >
            <input
              id="csv-file-picker"
              type="file"
              accept=".csv"
              style={{ display: 'none' }}
              onChange={(e) => {
                if (e.target.files && e.target.files[0]) {
                  setSelectedFile(e.target.files[0]);
                  setImportResult(null);
                }
              }}
            />
            <div style={{ fontSize: '1.5rem', marginBottom: '0.5rem' }}>📄</div>
            <strong>{selectedFile ? selectedFile.name : 'Click to select CSV file'}</strong>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              {selectedFile ? `${(selectedFile.size / 1024).toFixed(1)} KB` : 'UTF-8 encoded CSV files up to 10MB'}
            </p>
          </div>

          <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'space-between', alignItems: 'center' }}>
            <Button size="sm" variant="ghost" onClick={handleDownloadSample}>
              Download CSV Template
            </Button>
            {hasPermission('USER:IMPORT') && (
              <Button
                variant="primary"
                disabled={!selectedFile || isUploading}
                onClick={handleUpload}
              >
                {isUploading ? 'Validating & Importing...' : 'Execute Import'}
              </Button>
            )}
          </div>

          {errorMsg && <Alert tone="danger">{errorMsg}</Alert>}
        </Card>

        {/* EXPORTS */}
        <Card style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div>
            <h3>Data Scope Exports</h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)' }}>
              Download system-wide records formatted for spreadsheets or compliance archiving.
            </p>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.75rem', border: '1px solid var(--border-color)', borderRadius: '8px' }}>
              <div>
                <strong>Users Directory</strong>
                <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>All accounts, roles, and status</p>
              </div>
              {hasPermission('USER:EXPORT') ? (
                <Button variant="secondary" onClick={() => administrationApi.exportUsers()}>
                  Export CSV
                </Button>
              ) : (
                <Badge tone="neutral">No permission</Badge>
              )}
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.75rem', border: '1px solid var(--border-color)', borderRadius: '8px' }}>
              <div>
                <strong>System Audit Logs</strong>
                <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Complete security event history</p>
              </div>
              {hasPermission('AUDIT:EXPORT') ? (
                <Button variant="secondary" onClick={() => administrationApi.exportAuditLogs()}>
                  Export CSV
                </Button>
              ) : (
                <Badge tone="neutral">No permission</Badge>
              )}
            </div>
          </div>
        </Card>
      </div>

      {/* IMPORT RESULT DIAGNOSTICS */}
      {importResult && (
        <Card style={{ marginTop: '1rem' }}>
          <h3>Import Execution Results</h3>
          <div className="import-result-summary">
            <div style={{ padding: '0.75rem', background: 'rgba(255,255,255,0.03)', borderRadius: '6px' }}>
              <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Total Records</span>
              <div style={{ fontSize: '1.25rem', fontWeight: 600 }}>{importResult.totalRows}</div>
            </div>
            <div style={{ padding: '0.75rem', background: 'rgba(34, 197, 94, 0.1)', borderRadius: '6px' }}>
              <span style={{ fontSize: '0.8rem', color: '#4ade80' }}>Successfully Imported</span>
              <div style={{ fontSize: '1.25rem', fontWeight: 600, color: '#4ade80' }}>{importResult.successCount}</div>
            </div>
            <div style={{ padding: '0.75rem', background: 'rgba(239, 68, 68, 0.1)', borderRadius: '6px' }}>
              <span style={{ fontSize: '0.8rem', color: '#f87171' }}>Failed Validations</span>
              <div style={{ fontSize: '1.25rem', fontWeight: 600, color: '#f87171' }}>{importResult.failureCount}</div>
            </div>
          </div>

          {importResult.errors.length > 0 && (
            <div style={{ marginTop: '1.25rem' }}>
              <h4 style={{ color: '#f87171', marginBottom: '0.5rem' }}>Row-Level Validation Failures</h4>
              <table className="error-diagnostic-table">
                <thead>
                  <tr>
                    <th>Row #</th>
                    <th>Record Identifier</th>
                    <th>Validation Diagnostic</th>
                  </tr>
                </thead>
                <tbody>
                  {importResult.errors.map((e, idx) => (
                    <tr key={idx}>
                      <td><strong>{e.rowNumber}</strong></td>
                      <td><code>{e.identifier}</code></td>
                      <td style={{ color: '#f87171' }}>{e.error}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}
    </div>
  );
}
