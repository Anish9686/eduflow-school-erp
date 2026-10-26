import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider } from '../theme/ThemeProvider';
import { AppProviders } from '../app/AppProviders';
import { AppRoutes } from '../routes';

const superAdmin = {
  id: 1,
  username: 'super-admin',
  fullName: 'Super Administrator',
  roles: ['SUPER_ADMIN'],
  permissions: [
    'USER:READ',
    'USER:CREATE',
    'USER:UPDATE',
    'USER:MANAGE',
    'USER:IMPORT',
    'USER:EXPORT',
    'RBAC:ROLE_READ',
    'RBAC:ROLE_CREATE',
    'RBAC:ROLE_UPDATE',
    'RBAC:USER_ROLE_UPDATE',
    'RBAC:ROLE_PERMISSION_UPDATE',
    'AUDIT:READ',
    'AUDIT:EXPORT',
    'SETTINGS:READ',
    'SETTINGS:UPDATE',
  ],
};

const mockUser = {
  id: 10,
  username: 'test.teacher',
  email: 'teacher@school.edu',
  fullName: 'Test Teacher',
  phone: '+15550100',
  status: 'ACTIVE',
  failedAttempts: 0,
  lastLoginAt: '2026-10-14T10:00:00Z',
  createdAt: '2026-10-14T08:00:00Z',
  updatedAt: '2026-10-14T08:00:00Z',
  roles: ['TEACHER'],
};

const mockRole = {
  id: 1,
  code: 'TEACHER',
  name: 'Teacher',
  description: 'Teaching staff',
  isSystem: true,
  createdAt: '2026-10-14T08:00:00Z',
  updatedAt: '2026-10-14T08:00:00Z',
  permissions: ['ATTENDANCE:MARK', 'STUDENT:READ'],
};

const mockSetting = {
  key: 'institution.name',
  value: 'Acme International School',
  category: 'INSTITUTION',
  description: 'Official school name',
  updatedBy: 1,
  updatedAt: '2026-10-14T08:00:00Z',
  version: 1,
};

function response(data: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => data,
    text: async () => (typeof data === 'string' ? data : JSON.stringify(data)),
    blob: async () => new Blob([typeof data === 'string' ? data : JSON.stringify(data)]),
  } as Response;
}

function renderRoutes() {
  return render(
    <ThemeProvider>
      <AppProviders>
        <AppRoutes />
      </AppProviders>
    </ThemeProvider>
  );
}

describe('Administration Workflows', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  beforeEach(() => {
    window.sessionStorage.clear();
    window.sessionStorage.setItem('erp-portal-access-token', 'admin-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    window.history.replaceState({}, '', '/app/administration');
  });

  it('renders Administration page and lists users', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(superAdmin);
      if (url.includes('/api/v1/admin/users')) {
        return response({
          content: [mockUser],
          page: 0,
          size: 10,
          totalElements: 1,
          totalPages: 1,
        });
      }
      if (url.endsWith('/api/v1/admin/roles')) return response([mockRole]);
      if (url.endsWith('/api/v1/admin/permissions')) return response([]);
      if (url.endsWith('/api/v1/admin/settings')) return response([mockSetting]);
      if (url.includes('/api/v1/admin/audit-logs')) {
        return response({ content: [], page: 0, size: 15, totalElements: 0, totalPages: 0 });
      }
      return response([], 200);
    });

    vi.stubGlobal('fetch', fetchMock);
    renderRoutes();

    expect(await screen.findByRole('heading', { name: 'Administration' })).toBeInTheDocument();
    expect(await screen.findByText('test.teacher')).toBeInTheDocument();
    expect(screen.getByText('teacher@school.edu')).toBeInTheDocument();
  });

  it('keeps the Add User form open and focused while typing', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(superAdmin);
      if (url.includes('/api/v1/admin/users')) {
        return response({ content: [mockUser], page: 0, size: 10, totalElements: 1, totalPages: 1 });
      }
      if (url.endsWith('/api/v1/admin/roles')) return response([mockRole]);
      if (url.endsWith('/api/v1/admin/permissions')) return response([]);
      if (url.endsWith('/api/v1/admin/settings')) return response([mockSetting]);
      if (url.includes('/api/v1/admin/audit-logs')) {
        return response({ content: [], page: 0, size: 15, totalElements: 0, totalPages: 0 });
      }
      return response([], 200);
    });

    vi.stubGlobal('fetch', fetchMock);
    renderRoutes();
    const user = userEvent.setup();

    await user.click(await screen.findByRole('button', { name: '+ Add User' }));
    const username = screen.getByRole('textbox', { name: /username/i });
    await user.type(username, 'new.member');

    expect(username).toHaveValue('new.member');
    expect(screen.getByRole('dialog', { name: 'Create New User Account' })).toBeInTheDocument();
  });

  it('switches to Roles tab and displays roles', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(superAdmin);
      if (url.includes('/api/v1/admin/users')) {
        return response({ content: [mockUser], page: 0, size: 10, totalElements: 1, totalPages: 1 });
      }
      if (url.endsWith('/api/v1/admin/roles')) return response([mockRole]);
      if (url.endsWith('/api/v1/admin/permissions')) return response([]);
      if (url.endsWith('/api/v1/admin/settings')) return response([mockSetting]);
      if (url.includes('/api/v1/admin/audit-logs')) {
        return response({ content: [], page: 0, size: 15, totalElements: 0, totalPages: 0 });
      }
      return response([], 200);
    });

    vi.stubGlobal('fetch', fetchMock);
    renderRoutes();

    expect(await screen.findByRole('heading', { name: 'Administration' })).toBeInTheDocument();

    const rolesTab = screen.getByRole('tab', { name: 'Roles' });
    await userEvent.click(rolesTab);

    expect(await screen.findByText('TEACHER')).toBeInTheDocument();
    expect(screen.getByText('Teaching staff')).toBeInTheDocument();
  });

  it('blocks Administration route when user lacks administration permissions', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input).endsWith('/api/v1/me')
          ? response({ ...superAdmin, permissions: ['STUDENT:READ'] })
          : response([])
      )
    );

    renderRoutes();
    expect(await screen.findByRole('heading', { name: 'You don’t have access to this area' })).toBeInTheDocument();
  });
});
