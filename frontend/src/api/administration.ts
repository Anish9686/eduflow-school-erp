import { apiFetch, apiGet, apiPost, apiPut, buildQueryString } from './client';

export type UserItem = {
  id: number;
  username: string;
  email: string;
  fullName: string | null;
  phone: string | null;
  status: 'ACTIVE' | 'LOCKED' | 'DISABLED';
  failedAttempts: number;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
  roles: string[];
};

export type UserPageResponse = {
  content: UserItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type CreateUserPayload = {
  username: string;
  email: string;
  password: string;
  fullName?: string;
  phone?: string;
  roleCodes?: string[];
};

export type UpdateUserPayload = {
  email?: string;
  fullName?: string;
  phone?: string;
  password?: string;
};

export type RoleItem = {
  id: number;
  code: string;
  name: string;
  description: string | null;
  isSystem: boolean;
  createdAt: string;
  updatedAt: string;
  permissions: string[];
};

export type CreateRolePayload = {
  code: string;
  name: string;
  description?: string;
  permissionCodes?: string[];
};

export type UpdateRolePayload = {
  name: string;
  description?: string;
};

export type PermissionItem = {
  id: number;
  code: string;
  description: string | null;
  module: string;
};

export type AuditLogItem = {
  id: number;
  actorUserId: number | null;
  actorUsername: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  oldValue: string | null;
  newValue: string | null;
  ipAddress: string | null;
  traceId: string | null;
  createdAt: string;
};

export type AuditLogPageResponse = {
  content: AuditLogItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type SystemSetting = {
  key: string;
  value: string;
  category: string;
  description: string | null;
  updatedBy: number | null;
  updatedAt: string;
  version: number;
};

export type RowFailure = {
  rowNumber: number;
  identifier: string;
  error: string;
};

export type ImportResult = {
  totalRows: number;
  successCount: number;
  failureCount: number;
  errors: RowFailure[];
};

export const administrationApi = {
  // Users
  listUsers: (params?: { search?: string; status?: string; role?: string; page?: number; size?: number }) =>
    apiGet<UserPageResponse>('/api/v1/admin/users', params),
  getUser: (id: number) =>
    apiGet<UserItem>(`/api/v1/admin/users/${id}`),
  createUser: (payload: CreateUserPayload) =>
    apiPost<UserItem>('/api/v1/admin/users', payload),
  updateUser: (id: number, payload: UpdateUserPayload) =>
    apiPut<UserItem>(`/api/v1/admin/users/${id}`, payload),
  updateUserStatus: (id: number, status: 'ACTIVE' | 'LOCKED' | 'DISABLED') =>
    apiPut<UserItem>(`/api/v1/admin/users/${id}/status`, { status }),
  updateUserRoles: (id: number, roleCodes: string[]) =>
    apiPut<void>(`/api/v1/admin/users/${id}/roles`, { roleCodes }),

  // Roles & Permissions
  listRoles: () => apiGet<RoleItem[]>('/api/v1/admin/roles'),
  getRole: (code: string) => apiGet<RoleItem>(`/api/v1/admin/roles/${code}`),
  createRole: (payload: CreateRolePayload) => apiPost<RoleItem>('/api/v1/admin/roles', payload),
  updateRole: (code: string, payload: UpdateRolePayload) => apiPut<RoleItem>(`/api/v1/admin/roles/${code}`, payload),
  updateRolePermissions: (code: string, permissionCodes: string[]) =>
    apiPut<void>(`/api/v1/admin/roles/${code}/permissions`, { permissionCodes }),
  listPermissions: () => apiGet<PermissionItem[]>('/api/v1/admin/permissions'),

  // Audit Logs
  queryAuditLogs: (params?: { actor?: string; entityType?: string; action?: string; dateFrom?: string; dateTo?: string; page?: number; size?: number }) =>
    apiGet<AuditLogPageResponse>('/api/v1/admin/audit-logs', params),

  // Settings
  listSettings: () => apiGet<SystemSetting[]>('/api/v1/admin/settings'),
  updateSetting: (key: string, value: string) =>
    apiPut<SystemSetting>(`/api/v1/admin/settings/${encodeURIComponent(key)}`, { value }),

  // Import
  importUsers: async (fileOrCsv: File | string): Promise<ImportResult> => {
    const headers = new Headers();
    let body: BodyInit;
    if (typeof fileOrCsv === 'string') {
      headers.set('Content-Type', 'text/csv');
      body = fileOrCsv;
    } else {
      const fd = new FormData();
      fd.append('file', fileOrCsv);
      body = fd;
    }
    return apiPost<ImportResult>('/api/v1/admin/import/users', body, headers);
  },

  // Export
  exportUsers: async () => {
    const response = await apiFetch('/api/v1/admin/export/users');
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'users_export.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
  },

  exportAuditLogs: async (params?: { actor?: string; entityType?: string; action?: string; dateFrom?: string; dateTo?: string }) => {
    const query = buildQueryString(params);
    const response = await apiFetch(`/api/v1/admin/export/audit-logs${query}`);
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'audit_logs_export.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
  },
};
