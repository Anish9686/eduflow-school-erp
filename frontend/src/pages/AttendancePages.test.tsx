import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider } from '../theme/ThemeProvider';
import { AppProviders } from '../app/AppProviders';
import { AppRoutes } from '../routes';

const attendanceManager = {
  id: 10,
  username: 'attendance-admin',
  fullName: 'Attendance Admin',
  roles: ['ADMIN'],
  permissions: [
    'ATTENDANCE:READ',
    'ATTENDANCE:MARK',
    'ATTENDANCE:CORRECT',
    'ATTENDANCE:MANAGE',
    'STAFF_ATTENDANCE:READ',
    'STAFF_ATTENDANCE:MARK',
    'STAFF_ATTENDANCE:CORRECT',
    'STAFF_ATTENDANCE:MANAGE',
  ],
};

function response(data: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => data,
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

describe('attendance module routes and screens', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  beforeEach(() => {
    window.sessionStorage.clear();
    window.sessionStorage.setItem('erp-portal-access-token', 'attendance-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    window.history.replaceState({}, '', '/app/attendance');
  });

  it('renders attendance workspace and allows marking students', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(attendanceManager);
      if (url.includes('/api/v1/attendance/options/sessions'))
        return response([{ id: 1, name: '2026–2027' }]);
      if (url.includes('/api/v1/attendance/options/classes'))
        return response([{ id: 2, name: 'Grade 1' }]);
      if (url.includes('/api/v1/attendance/options/sections'))
        return response([{ id: 3, classId: 2, name: 'Section A' }]);
      if (url.includes('/api/v1/attendance/roster'))
        return response([
          {
            studentId: 50,
            admissionNo: 'ADM-001',
            studentName: 'Alice Walker',
            status: null,
            remarks: null,
            attendanceId: null,
            corrected: false,
          },
        ]);
      if (url.includes('/api/v1/attendance/students/mark') && init?.method === 'POST')
        return response(
          {
            created: 1,
            alreadyMarked: 0,
            records: [
              {
                id: 101,
                studentId: 50,
                admissionNo: 'ADM-001',
                studentName: 'Alice Walker',
                sessionId: 1,
                classId: 2,
                className: 'Grade 1',
                sectionId: 3,
                sectionName: 'Section A',
                attendanceDate: '2026-10-06',
                status: 'PRESENT',
                markedBy: 10,
                markerName: 'Attendance Admin',
                markedAt: '2026-10-06T09:00:00',
                remarks: null,
                corrected: false,
              },
            ],
          },
          201
        );
      return response([], 200);
    });

    vi.stubGlobal('fetch', fetchMock);
    renderRoutes();

    expect(await screen.findByRole('heading', { name: 'Attendance workspace' })).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /Mark Attendance/ })).toBeInTheDocument();

    // Select class
    const classSelect = await screen.findByLabelText(/^Class/);
    await userEvent.selectOptions(classSelect, '2');

    // Select section
    const sectionSelect = await screen.findByLabelText(/^Section/);
    await userEvent.selectOptions(sectionSelect, '3');

    // Roster student should appear
    expect(await screen.findByText('Alice Walker')).toBeInTheDocument();

    // Click Submit Attendance
    const submitBtn = screen.getByRole('button', { name: 'Submit Attendance' });
    await userEvent.click(submitBtn);

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/attendance/students/mark'),
        expect.objectContaining({ method: 'POST' })
      )
    );
  });

  it('renders attendance summary and metrics breakdown', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(attendanceManager);
      if (url.includes('/api/v1/attendance/options/sessions'))
        return response([{ id: 1, name: '2026–2027' }]);
      if (url.includes('/api/v1/attendance/options/classes'))
        return response([{ id: 2, name: 'Grade 1' }]);
      if (url.includes('/api/v1/attendance/summary'))
        return response({
          total: 50,
          present: 45,
          absent: 3,
          late: 2,
          halfDay: 0,
          excused: 0,
          holiday: 0,
        });
      return response([], 200);
    });

    vi.stubGlobal('fetch', fetchMock);
    renderRoutes();

    const summaryTab = await screen.findByRole('button', { name: /Attendance Summary/ });
    await userEvent.click(summaryTab);

    expect(await screen.findByText('Overall Attendance Ratio')).toBeInTheDocument();
    expect((await screen.findAllByText('90%')).length).toBeGreaterThan(0);
    expect(await screen.findByText('Present Rate')).toBeInTheDocument();
  });

  it('blocks attendance access if user has no attendance permissions', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) =>
        String(input).endsWith('/api/v1/me') ? response({ ...attendanceManager, permissions: [] }) : response([])
      )
    );

    renderRoutes();
    expect(await screen.findByRole('heading', { name: 'You don’t have access to this area' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/app/forbidden');
  });
});
