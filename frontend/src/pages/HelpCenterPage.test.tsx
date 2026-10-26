import { cleanup, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppProviders } from '../app/AppProviders';
import { ThemeProvider } from '../theme/ThemeProvider';
import { AppRoutes } from '../routes';

const admin = { id: 1, username: 'admin', fullName: 'School Admin', roles: ['ADMIN'], permissions: ['STUDENT:READ', 'ACADEMIC:READ', 'ATTENDANCE:READ', 'FEE:READ', 'USER:READ', 'RBAC:ROLE_READ', 'AUDIT:READ', 'SETTINGS:READ'] };
function renderApp() { return render(<ThemeProvider><AppProviders><AppRoutes /></AppProviders></ThemeProvider>); }
function mockUser(user = admin) {
  window.sessionStorage.setItem('erp-portal-access-token', 'help-test-token');
  window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    const body = url.endsWith('/api/v1/me') ? user : url.endsWith('/api/v1/dashboard') ? { metrics: [], recentActivity: [] } : [];
    return { ok: true, status: 200, json: async () => body } as Response;
  }));
}

describe('Help Center', () => {
  beforeEach(() => { window.sessionStorage.clear(); window.history.replaceState({}, '', '/app/dashboard'); });
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  it('uses the same Help Center destination from sidebar and footer', async () => {
    mockUser();
    const user = userEvent.setup();
    renderApp();
    const sidebarLink = await screen.findByRole('link', { name: 'Need a hand? Visit the Help Center' });
    const footerLink = screen.getByRole('link', { name: 'Open Help Center' });
    expect(sidebarLink).toHaveAttribute('href', '/app/help');
    expect(footerLink).toHaveAttribute('href', '/app/help');
    await user.click(screen.getByRole('button', { name: 'Open navigation' }));
    expect(screen.getByRole('complementary', { name: 'Main navigation' })).toHaveClass('is-mobile-open');
    await user.click(sidebarLink);
    expect(await screen.findByRole('heading', { name: 'Help Center' })).toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: 'Main navigation' })).not.toHaveClass('is-mobile-open');
    await user.click(screen.getByRole('link', { name: 'Open Help Center' }));
    expect(window.location.pathname).toBe('/app/help');
  });

  it('shows local articles, filters their text, and explains an empty result', async () => {
    mockUser();
    window.history.replaceState({}, '', '/app/help');
    const user = userEvent.setup();
    renderApp();
    expect(await screen.findByText('The dashboard shows the current role-scoped metrics and recent activity provided to your account.')).toBeInTheDocument();
    const search = screen.getByRole('searchbox', { name: 'Search help articles' });
    await user.type(search, 'attendance');
    expect(screen.getByText('Access attendance')).toBeInTheDocument();
    expect(screen.queryByText('Navigate your workspace')).not.toBeInTheDocument();
    await user.clear(search);
    await user.type(search, 'no matching article words');
    expect(screen.getByText('No matching help articles')).toBeInTheDocument();
  });

  it('keeps the workspace label informational and gates related destinations by permission', async () => {
    mockUser({ ...admin, permissions: [] });
    window.history.replaceState({}, '', '/app/help');
    renderApp();
    const identity = await screen.findByLabelText('EduFlow school workspace');
    expect(identity).toHaveTextContent('EduFlow');
    expect(identity).toHaveTextContent('School workspace');
    expect(identity.closest('a,button')).toBeNull();
    const attendanceArticle = screen.getByText('Access attendance').closest('details');
    expect(attendanceArticle).not.toBeNull();
    expect(within(attendanceArticle as HTMLElement).queryByRole('link', { name: /open attendance/i })).not.toBeInTheDocument();
  });

  it('navigates from an available article to its existing module route', async () => {
    mockUser();
    window.history.replaceState({}, '', '/app/help');
    const user = userEvent.setup();
    renderApp();
    const academicArticle = await screen.findByText('Use the academic workspace');
    await user.click(academicArticle);
    await user.click(within(academicArticle.closest('details') as HTMLElement).getByRole('link', { name: /open sessions/i }));
    expect(await screen.findByRole('heading', { name: 'Academic sessions' })).toBeInTheDocument();
  });
});
