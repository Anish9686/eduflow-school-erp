import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider } from '../theme/ThemeProvider';
import { AppRoutes } from '../routes';
import { AppProviders } from '../app/AppProviders';

const adminUser = { id: 7, username: 'admin', fullName: 'Avery Admin', roles: ['ADMIN'], permissions: ['STUDENT:READ', 'STUDENT:CREATE', 'ATTENDANCE:MARK', 'FEE:COLLECT', 'REPORT:EXPORT', 'RBAC:ROLE_READ'] };
function jsonResponse(body: unknown, status = 200) { return { ok: status >= 200 && status < 300, status, json: async () => body } as Response; }
function renderApp() { return render(<ThemeProvider><AppProviders><AppRoutes /></AppProviders></ThemeProvider>); }

describe('authenticated application shell', () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
  beforeEach(() => {
    window.sessionStorage.clear(); window.localStorage.clear(); window.history.replaceState({}, '', '/');
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/auth/login')) return jsonResponse({ accessToken: 'opaque-test-token', expiresAt: new Date(Date.now() + 60_000).toISOString() });
      if (url.endsWith('/api/v1/me')) return jsonResponse(adminUser);
      if (url.endsWith('/api/v1/dashboard')) return jsonResponse({ metrics: [], recentActivity: [] });
      if (url.includes('/api/v1/students?')) return jsonResponse({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, sortBy: 'createdAt', direction: 'DESC' });
      return jsonResponse(null, 204);
    }));
  });

  it('sends unauthenticated routes to sign in and returns to the requested page', async () => {
    renderApp();
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/login');
    await userEvent.type(screen.getByRole('textbox', { name: /username/i }), 'admin');
    await userEvent.type(screen.getByLabelText(/password/i), 'sample-password');
    await userEvent.click(screen.getByRole('button', { name: /sign in/i }));
    expect(await screen.findByRole('heading', { name: /good (morning|afternoon|evening|night), avery!/i })).toBeInTheDocument();
    const campusBackground = document.querySelector('.campus-background');
    expect(campusBackground).toHaveAttribute('aria-hidden', 'true');
    expect(campusBackground?.querySelector('img.campus-background-image')).toBeInTheDocument();
    expect(await screen.findByText('No dashboard data available')).toBeInTheDocument();
    expect(window.location.pathname).toBe('/app/dashboard');
  });

  it('shows a retryable dashboard error when its API request fails', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'opaque-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return jsonResponse(adminUser);
      if (url.endsWith('/api/v1/dashboard')) return jsonResponse({}, 500);
      return jsonResponse(null, 204);
    }));
    renderApp();
    expect(await screen.findByText('Dashboard unavailable')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
  });

  it('shows navigation according to permissions and persists collapsed state', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'opaque-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    renderApp();
    expect(await screen.findByRole('navigation', { name: 'Workspace' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /students/i })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Collapse sidebar' }));
    expect(screen.getByRole('button', { name: 'Expand sidebar' })).toBeInTheDocument();
    await waitFor(() => expect(window.localStorage.getItem('erp-sidebar-collapsed')).toBe('true'));
    expect(screen.queryByRole('button', { name: /switch to (light|dark) mode/i })).not.toBeInTheDocument();
  });

  it('shows the Operations workspace only when an Operations permission is present', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'opaque-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return jsonResponse({ ...adminUser, roles: ['SUPER_ADMIN'], permissions: ['INVENTORY:READ'] });
      if (url.endsWith('/api/v1/dashboard')) return jsonResponse({ metrics: [], recentActivity: [] });
      return jsonResponse([], 200);
    }));
    renderApp();

    await userEvent.click(await screen.findByRole('link', { name: /operations/i }));

    expect(await screen.findByRole('heading', { name: 'Operations workspace' })).toBeInTheDocument();
    expect(screen.getByRole('tablist', { name: 'Operations navigation' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Inventory' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.queryByRole('tab', { name: 'Library' })).not.toBeInTheDocument();
  });

  it('does not grant Operations navigation or direct route access based on role name alone', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'opaque-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return jsonResponse({ ...adminUser, roles: ['SUPER_ADMIN'], permissions: [] });
      if (url.endsWith('/api/v1/dashboard')) return jsonResponse({ metrics: [], recentActivity: [] });
      return jsonResponse([], 200);
    }));
    window.history.replaceState({}, '', '/app/operations');
    renderApp();

    expect(await screen.findByRole('heading', { name: 'You don’t have access to this area' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /operations/i })).not.toBeInTheDocument();
  });

  it('restores a valid session after the app remounts', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'opaque-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    const first = renderApp();
    expect(await screen.findByRole('heading', { name: /good (morning|afternoon|evening|night), avery!/i })).toBeInTheDocument();
    first.unmount();
    renderApp();
    expect(await screen.findByRole('heading', { name: /good (morning|afternoon|evening|night), avery!/i })).toBeInTheDocument();
  });

  it('blocks a direct route when the user lacks its permission', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'opaque-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ ...adminUser, permissions: ['FEE:COLLECT'] })));
    window.history.replaceState({}, '', '/app/students');
    renderApp();
    expect(await screen.findByRole('heading', { name: 'You don’t have access to this area' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/app/forbidden');
    expect(screen.queryByRole('link', { name: 'Students' })).not.toBeInTheDocument();
  });

  it('hides and blocks Reports without any report data-read permission', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'opaque-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({ ...adminUser, permissions: ['REPORT:EXPORT'] })));
    window.history.replaceState({}, '', '/app/reports');
    renderApp();

    expect(await screen.findByRole('heading', { name: 'You don’t have access to this area' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /reports/i })).not.toBeInTheDocument();
  });

  it('opens responsive navigation and closes it after navigation', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'opaque-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    renderApp();
    await screen.findByRole('navigation', { name: 'Workspace' });
    await userEvent.click(screen.getByRole('button', { name: 'Open navigation' }));
    expect(screen.getByRole('complementary', { name: 'Main navigation' })).toHaveClass('is-mobile-open');
    await userEvent.click(screen.getByRole('link', { name: 'Students' }));
    expect(screen.getByRole('complementary', { name: 'Main navigation' })).not.toHaveClass('is-mobile-open');
  });

  it('renders all expected operational modules for ADMIN user Satyam Kumar Singh', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'opaque-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    const adminPerms = [
      'STUDENT:READ', 'STUDENT:CREATE', 'STUDENT:UPDATE', 'ADMISSION:READ', 'ADMISSION:CREATE', 'ADMISSION:UPDATE',
      'ACADEMIC:READ', 'ACADEMIC:MANAGE', 'TIMETABLE:READ', 'TIMETABLE:MANAGE', 'HOMEWORK:READ', 'HOMEWORK:CREATE',
      'SYLLABUS:READ', 'ATTENDANCE:READ', 'ATTENDANCE:MARK', 'STAFF_ATTENDANCE:READ',
      'FEE:READ', 'FEE:MANAGE', 'FEE:COLLECT', 'FEE:REFUND', 'FEE_DISCOUNT:APPROVE', 'EXPENSE:MANAGE',
      'FINANCE_REPORT:READ', 'FINANCE_REPORT:EXPORT', 'INVENTORY:READ', 'INVENTORY:MANAGE',
      'LIBRARY:READ', 'LIBRARY:MANAGE', 'REPORT:EXPORT', 'USER:READ', 'RBAC:ROLE_READ', 'SETTINGS:READ', 'SETTINGS:UPDATE'
    ];
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({
      id: 2, username: 'Satyam9801', fullName: 'Satyam Kumar Singh', roles: ['ADMIN'], permissions: adminPerms
    })));
    renderApp();

    expect(await screen.findByRole('navigation', { name: 'Workspace' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /dashboard/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /students/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /admission/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /academics/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /attendance/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /finance/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /operations/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /administration/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /reports/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /settings/i })).toBeInTheDocument();
  });

  it('renders all expected oversight modules for MANAGEMENT user Ankit9661', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'opaque-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    const mgmtPerms = [
      'USER:READ', 'RBAC:ROLE_READ', 'STUDENT:READ', 'ADMISSION:READ',
      'ACADEMIC:READ', 'TIMETABLE:READ', 'HOMEWORK:READ', 'SYLLABUS:READ',
      'ATTENDANCE:READ', 'STAFF_ATTENDANCE:READ', 'FEE:READ', 'FEE_DISCOUNT:APPROVE',
      'EXPENSE:APPROVE', 'FINANCE_REPORT:READ', 'FINANCE_REPORT:EXPORT',
      'INVENTORY:READ', 'LIBRARY:READ', 'REPORT:EXPORT', 'AUDIT:READ', 'SETTINGS:READ'
    ];
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({
      id: 4, username: 'Ankit9661', fullName: '', roles: ['MANAGEMENT'], permissions: mgmtPerms
    })));
    renderApp();

    expect(await screen.findByRole('navigation', { name: 'Workspace' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /dashboard/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /students/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /admission/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /academics/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /attendance/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /finance/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /operations/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /administration/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /reports/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /settings/i })).toBeInTheDocument();
  });

  it('restricts sidebar modules for TEACHER to assigned responsibilities', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'opaque-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    const teacherPerms = [
      'STUDENT:READ', 'ACADEMIC:READ', 'TIMETABLE:READ', 'HOMEWORK:READ', 'HOMEWORK:CREATE',
      'SYLLABUS:READ', 'SYLLABUS:UPDATE', 'ATTENDANCE:READ', 'ATTENDANCE:MARK',
      'LIBRARY:READ', 'REPORT:EXPORT', 'SETTINGS:READ'
    ];
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({
      id: 5, username: 'Amit9661', fullName: 'Amit', roles: ['TEACHER'], permissions: teacherPerms
    })));
    renderApp();

    expect(await screen.findByRole('navigation', { name: 'Workspace' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /dashboard/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /students/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /academics/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /attendance/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /operations/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /reports/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /settings/i })).toBeInTheDocument();

    // Denied modules for TEACHER (no admission, finance, or administration permissions)
    expect(screen.queryByRole('link', { name: /admission/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /finance/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /administration/i })).not.toBeInTheDocument();
  });

  it('renders global broadcast announcement banner and topbar notification badge', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'opaque-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    const testSettings = [
      {
        id: 1,
        key: 'system.notification_banner',
        value: 'Campus will be closed this Friday for maintenance.',
        category: 'GENERAL',
        description: 'Global school announcement banner',
        version: 1,
        updatedAt: '2026-10-16T10:00:00Z',
      },
    ];
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return jsonResponse({ ...adminUser, roles: ['SUPER_ADMIN'], permissions: ['SETTINGS:READ', 'SETTINGS:UPDATE'] });
      if (url.endsWith('/api/v1/dashboard')) return jsonResponse({ metrics: [], recentActivity: [] });
      if (url.endsWith('/api/v1/admin/settings')) return jsonResponse(testSettings);
      return jsonResponse(null, 204);
    }));
    renderApp();

    expect(await screen.findByRole('region', { name: 'School Announcement' })).toBeInTheDocument();
    expect(screen.getByText('Campus will be closed this Friday for maintenance.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /edit notice/i })).toBeInTheDocument();

    // Notification bell in topbar
    const bell = screen.getByRole('button', { name: /notifications, 1 announcement/i });
    expect(bell).toBeInTheDocument();
    await userEvent.click(bell);

    expect(screen.getByRole('dialog', { name: 'Notifications' })).toBeInTheDocument();
    expect(screen.getByText('1 announcement')).toBeInTheDocument();
    expect(screen.getByText('BROADCAST')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /edit broadcast notice in settings/i })).toBeInTheDocument();

    // Dismiss banner
    await userEvent.click(screen.getByRole('button', { name: 'Dismiss announcement' }));
    expect(screen.queryByRole('region', { name: 'School Announcement' })).not.toBeInTheDocument();
  });

  it('hides Students directory page and navigation from student users and blocks route', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'opaque-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    const studentUser = {
      id: 9, username: 'student1', fullName: 'Sam Student', roles: ['STUDENT'],
      permissions: ['STUDENT:READ', 'TIMETABLE:READ', 'HOMEWORK:READ', 'SYLLABUS:READ', 'ATTENDANCE:READ', 'SETTINGS:READ']
    };
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return jsonResponse(studentUser);
      if (url.endsWith('/api/v1/dashboard')) return jsonResponse({ metrics: [], recentActivity: [] });
      return jsonResponse([], 200);
    }));
    window.history.replaceState({}, '', '/app/students');
    renderApp();

    expect(await screen.findByRole('heading', { name: 'You don’t have access to this area' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /students/i })).not.toBeInTheDocument();
  });

  it('hides Attendance page and navigation from accountant users and blocks route', async () => {
    window.sessionStorage.setItem('erp-portal-access-token', 'opaque-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    const accountantUser = {
      id: 11, username: 'cashier', fullName: 'Casey Cashier', roles: ['ACCOUNTANT'],
      permissions: ['STUDENT:READ', 'FEE:READ', 'FEE:MANAGE', 'FEE:COLLECT', 'FINANCE_REPORT:READ']
    };
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return jsonResponse(accountantUser);
      if (url.endsWith('/api/v1/dashboard')) return jsonResponse({ metrics: [], recentActivity: [] });
      return jsonResponse([], 200);
    }));
    window.history.replaceState({}, '', '/app/attendance');
    renderApp();

    expect(await screen.findByRole('heading', { name: 'You don’t have access to this area' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /attendance/i })).not.toBeInTheDocument();
  });
});


