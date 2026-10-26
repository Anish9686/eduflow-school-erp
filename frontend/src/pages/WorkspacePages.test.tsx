import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider } from '../theme/ThemeProvider';
import { AppProviders } from '../app/AppProviders';
import { AuthProvider } from '../auth/AuthProvider';
import { DashboardPage, extractFirstName, greetingForLocalTime } from './WorkspacePages';

function jsonResponse(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

function renderDashboard(user: unknown) {
  window.sessionStorage.clear();
  window.sessionStorage.setItem('erp-portal-access-token', 'test-token');
  window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input);
    if (url.endsWith('/api/v1/me')) return jsonResponse(user);
    if (url.endsWith('/api/v1/dashboard')) return jsonResponse({ metrics: [], recentActivity: [] });
    return jsonResponse(null, 204);
  }));

  return render(
    <ThemeProvider>
      <AppProviders>
        <AuthProvider>
          <MemoryRouter>
            <DashboardPage />
          </MemoryRouter>
        </AuthProvider>
      </AppProviders>
    </ThemeProvider>
  );
}

describe('greeting and name utilities', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('determines greeting according to time of day including morning, afternoon, evening, and night', () => {
    // 08:00 AM -> Morning
    const morning = new Date(2026, 9, 17, 8, 30);
    expect(greetingForLocalTime(morning)).toBe('Good morning');

    // 01:30 PM (13:30) -> Afternoon
    const afternoon = new Date(2026, 9, 17, 13, 30);
    expect(greetingForLocalTime(afternoon)).toBe('Good afternoon');

    // 07:15 PM (19:15) -> Evening
    const evening = new Date(2026, 9, 17, 19, 15);
    expect(greetingForLocalTime(evening)).toBe('Good evening');

    // 11:30 PM (23:30) -> Night
    const lateNight = new Date(2026, 9, 17, 23, 30);
    expect(greetingForLocalTime(lateNight)).toBe('Good night');

    // 02:00 AM (02:00) -> Night
    const earlyHours = new Date(2026, 9, 17, 2, 0);
    expect(greetingForLocalTime(earlyHours)).toBe('Good night');
  });

  it('extracts first name from full name or falls back to cleaned username or role', () => {
    expect(extractFirstName({ fullName: 'Anish Mishra', username: 'anish' })).toBe('Anish');
    expect(extractFirstName({ fullName: 'Super Admin', username: 'admin' })).toBe('Super');
    expect(extractFirstName({ fullName: '', username: 'teacher.sharma' })).toBe('Teacher');
    expect(extractFirstName({ fullName: null, username: 'john_doe@school.edu' })).toBe('John');
    expect(extractFirstName({ fullName: '', username: '', roles: ['ACCOUNTANT'] })).toBe('Accountant');
    expect(extractFirstName(null)).toBe('');
  });

  it('renders personalized time greeting with first name and exclamation mark on Dashboard', async () => {
    renderDashboard({
      id: 1,
      username: 'teacher.verma',
      fullName: '',
      roles: ['TEACHER'],
      permissions: ['DASHBOARD:READ'],
    });

    // Should greet Teacher with !
    const expectedGreeting = greetingForLocalTime();
    expect(await screen.findByRole('heading', { name: `${expectedGreeting}, Teacher!` })).toBeInTheDocument();
  });

  it('renders full name user greeting on Dashboard', async () => {
    renderDashboard({
      id: 2,
      username: 'anish',
      fullName: 'Anish Mishra',
      roles: ['SUPER_ADMIN'],
      permissions: ['DASHBOARD:READ'],
    });

    const expectedGreeting = greetingForLocalTime();
    expect(await screen.findByRole('heading', { name: `${expectedGreeting}, Anish!` })).toBeInTheDocument();
  });
});
