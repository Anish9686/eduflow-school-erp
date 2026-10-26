import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppProviders } from '../app/AppProviders';
import { ThemeProvider } from '../theme/ThemeProvider';
import { AppRoutes } from '../routes';

function response(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

function renderApp() {
  return render(
    <ThemeProvider>
      <AppProviders>
        <AppRoutes />
      </AppProviders>
    </ThemeProvider>
  );
}

const operationalUser = {
  id: 1,
  username: 'transport-admin',
  fullName: 'Transport Admin',
  roles: ['ADMIN'],
  permissions: [
    'INVENTORY:READ',
    'TRANSPORT:READ',
    'TRANSPORT:CREATE',
    'TRANSPORT:UPDATE',
    'TRANSPORT:DELETE',
    'TRANSPORT:ASSIGN',
    'TRANSPORT:MANAGE_ROUTES',
    'TRANSPORT:MANAGE_STAFF',
    'TRANSPORT:VIEW_REPORTS',
    'TRANSPORT:VIEW_HISTORY',
  ],
};

const studentUser = {
  id: 14,
  username: 'Atul9661',
  fullName: 'Atul Kumar',
  roles: ['STUDENT'],
  permissions: ['TRANSPORT:READ'],
};

const parentUser = {
  id: 13,
  username: 'Doe9661',
  fullName: 'John Doe Sr.',
  roles: ['PARENT_GUARDIAN'],
  permissions: ['TRANSPORT:READ'],
};

const teacherUser = {
  id: 20,
  username: 'Amit9661',
  fullName: 'Amit Verma',
  roles: ['TEACHER'],
  permissions: ['ACADEMIC:READ', 'HOMEWORK:READ'],
};

const sampleOverview = {
  totalActiveBuses: 5,
  totalActiveRoutes: 3,
  totalAssignedStudents: 42,
  totalCapacity: 150,
  totalAvailableSeats: 108,
  fullBusesCount: 1,
  busesWithoutDriver: 1,
  busesWithoutConductor: 2,
  routesWithoutBus: 0,
  recentHistory: [],
};

const sampleBuses = [
  {
    id: 101,
    busName: 'Yellow Express 01',
    registrationNo: 'DL01-AB-1234',
    capacity: 35,
    activeOccupancy: 20,
    availableSeats: 15,
    assignedRouteId: 10,
    assignedRouteName: 'North Campus Express',
    driverStaffId: 5,
    driverName: 'Ramesh Singh',
    conductorStaffId: 6,
    conductorName: 'Suresh Kumar',
    status: 'ACTIVE',
  },
];

const sampleStudentAssignment = {
  assigned: true,
  assignmentId: 501,
  studentId: 1,
  studentName: 'Atul Kumar',
  admissionNo: 'ADM-2026-001',
  busName: 'Yellow Express 01',
  registrationNo: 'DL01-AB-1234',
  routeName: 'North Campus Express',
  pickupStopName: 'Sector 14 Circle',
  pickupTime: '07:30',
  dropStopName: 'School Main Gate',
  dropTime: '14:30',
  driverName: 'Ramesh Singh',
  conductorName: 'Suresh Kumar',
  status: 'ACTIVE',
  routeStops: [],
};

const sampleParentChildrenAssignments = [
  {
    childId: 1,
    childName: 'Aarav Sharma',
    admissionNo: 'ADM-001',
    assigned: true,
    assignmentId: 501,
    busName: 'School Bus 03',
    registrationNo: 'RJ14 XX 1234',
    routeName: 'Vaishali Nagar Route',
    pickupStopName: 'Sector 4 Main Gate',
    pickupTime: '07:25',
    dropStopName: 'Sector 4 Main Gate',
    dropTime: '14:45',
    driverName: 'Rajesh Kumar',
    conductorName: 'Sunita Devi',
    status: 'ACTIVE',
  },
  {
    childId: 2,
    childName: 'Diya Sharma',
    admissionNo: 'ADM-002',
    assigned: true,
    assignmentId: 502,
    busName: 'School Bus 07',
    registrationNo: 'RJ14 YY 5678',
    routeName: 'Civil Lines Route',
    pickupStopName: 'Civil Lines Cross',
    pickupTime: '07:40',
    dropStopName: 'Civil Lines Cross',
    dropTime: '14:15',
    driverName: 'Manoj Singh',
    conductorName: null,
    status: 'ACTIVE',
  },
];

describe('Transport Module Frontend Integration Tests', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  beforeEach(() => {
    window.sessionStorage.clear();
    window.sessionStorage.setItem('erp-portal-access-token', 'transport-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
  });

  it('renders Transport tab in Operations for authorized operational users', async () => {
    window.history.replaceState({}, '', '/app/operations?tab=transport');

    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(operationalUser);
      if (url.includes('/api/v1/transport/overview')) return response(sampleOverview);
      if (url.includes('/api/v1/transport/buses')) return response(sampleBuses);
      return response([]);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderApp();

    expect(await screen.findByRole('tab', { name: /Transport/ })).toBeInTheDocument();
    expect(await screen.findByText('Active Buses')).toBeInTheDocument();
    expect(await screen.findByText('Active Routes')).toBeInTheDocument();
    expect(await screen.findByText('Available Seats')).toBeInTheDocument();
    expect(await screen.findByText('108')).toBeInTheDocument();
  });

  it('does NOT render Operations or Transport for unauthorized role (TEACHER)', async () => {
    window.history.replaceState({}, '', '/app/operations');

    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(teacherUser);
      return response([]);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderApp();

    // Teacher has no operations permissions -> redirects to forbidden
    expect(await screen.findByText(/Access restricted/i)).toBeInTheDocument();
  });

  it('renders student-specific transport assignment view for STUDENT role', async () => {
    window.history.replaceState({}, '', '/app/transport');

    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(studentUser);
      if (url.includes('/api/v1/transport/my-assignment')) return response(sampleStudentAssignment);
      return response([]);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderApp();

    expect(await screen.findByText('STUDENT TRANSPORT')).toBeInTheDocument();
    expect(await screen.findByText('My Transport Assignment')).toBeInTheDocument();
    expect(await screen.findByText('Yellow Express 01')).toBeInTheDocument();
    expect(await screen.findByText('DL01-AB-1234')).toBeInTheDocument();
    expect(await screen.findByText('North Campus Express')).toBeInTheDocument();
    expect(await screen.findByText('Sector 14 Circle')).toBeInTheDocument();
    expect(await screen.findByText('Ramesh Singh')).toBeInTheDocument();

    // Verify operational buttons are NOT rendered for students
    expect(screen.queryByRole('button', { name: /Add Bus/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Create Route/i })).not.toBeInTheDocument();
  });

  it('renders parent view with multiple linked children distinguished by bus', async () => {
    window.history.replaceState({}, '', '/app/transport');

    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(parentUser);
      if (url.includes('/api/v1/transport/my-children')) return response(sampleParentChildrenAssignments);
      return response([]);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderApp();

    expect(await screen.findByText('PARENT PORTAL')).toBeInTheDocument();
    expect(await screen.findByText("Children's Transport")).toBeInTheDocument();

    // Aarav's bus
    expect(await screen.findByText('Aarav Sharma')).toBeInTheDocument();
    expect(await screen.findByText('School Bus 03')).toBeInTheDocument();
    expect(await screen.findByText('RJ14 XX 1234')).toBeInTheDocument();
    expect(await screen.findByText('Vaishali Nagar Route')).toBeInTheDocument();

    // Diya's bus
    expect(await screen.findByText('Diya Sharma')).toBeInTheDocument();
    expect(await screen.findByText('School Bus 07')).toBeInTheDocument();
    expect(await screen.findByText('RJ14 YY 5678')).toBeInTheDocument();
    expect(await screen.findByText('Civil Lines Route')).toBeInTheDocument();

    // Parent cannot see management buttons
    expect(screen.queryByRole('button', { name: /Assign Student/i })).not.toBeInTheDocument();
  });

  it('displays empty state when student has no transport assignment', async () => {
    window.history.replaceState({}, '', '/app/transport');

    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(studentUser);
      if (url.includes('/api/v1/transport/my-assignment')) {
        return response({ assigned: false, message: 'No bus or route is currently assigned to you.' });
      }
      return response(null);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderApp();

    expect(await screen.findByText(/No transport allocated/i)).toBeInTheDocument();
    expect(await screen.findByText(/No bus or route is currently assigned to you/i)).toBeInTheDocument();
  });

  it('switches between operational tabs (Buses, Routes, Assignments) seamlessly', async () => {
    window.history.replaceState({}, '', '/app/operations?tab=transport');

    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(operationalUser);
      if (url.includes('/api/v1/transport/overview')) return response(sampleOverview);
      if (url.includes('/api/v1/transport/buses')) return response(sampleBuses);
      if (url.includes('/api/v1/transport/routes')) {
        return response([
          {
            id: 10,
            routeName: 'North Campus Express',
            description: 'Morning and evening route',
            assignedBusName: 'Yellow Express 01',
            stopsCount: 4,
            status: 'ACTIVE',
          },
        ]);
      }
      return response([]);
    });
    vi.stubGlobal('fetch', fetchMock);

    renderApp();

    // Initially in Overview
    expect(await screen.findByText('Active Buses')).toBeInTheDocument();

    // Click Buses tab
    const busesTab = await screen.findByRole('button', { name: 'Buses' });
    await userEvent.click(busesTab);
    expect(await screen.findByText('Yellow Express 01')).toBeInTheDocument();
    expect(await screen.findByText('DL01-AB-1234')).toBeInTheDocument();

    // Click Routes tab
    const routesTab = await screen.findByRole('button', { name: 'Routes' });
    await userEvent.click(routesTab);
    expect(await screen.findByText('North Campus Express')).toBeInTheDocument();
  });
});
