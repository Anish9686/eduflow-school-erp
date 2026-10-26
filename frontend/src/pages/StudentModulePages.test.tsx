import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider } from '../theme/ThemeProvider';
import { AppProviders } from '../app/AppProviders';
import { AppRoutes } from '../routes';

const user = { id: 8, username: 'admissions', fullName: 'Admissions User', roles: ['ADMIN'], permissions: ['STUDENT:READ', 'STUDENT:CREATE', 'STUDENT:UPDATE', 'ADMISSION:READ', 'ADMISSION:CREATE', 'ADMISSION:UPDATE'] };
const page = { content: [{ id: 3, admissionNo: 'ST-003', firstName: 'Mira', lastName: 'Rao', status: 'ACTIVE', admissionDate: '2026-06-10', createdAt: '2026-06-10T10:00:00' }], page: 0, size: 20, totalElements: 1, totalPages: 1, sortBy: 'createdAt', direction: 'DESC' };
function jsonResponse(body: unknown, status = 200) { return { ok: status >= 200 && status < 300, status, json: async () => body } as Response; }
function renderApp() { return render(<ThemeProvider><AppProviders><AppRoutes /></AppProviders></ThemeProvider>); }

describe('student and admission pages', () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
  beforeEach(() => {
    window.sessionStorage.clear(); window.localStorage.clear();
    window.sessionStorage.setItem('erp-portal-access-token', 'test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    window.history.replaceState({}, '', '/app/students');
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return jsonResponse(user);
      if (url.includes('/api/v1/students?')) return jsonResponse(page);
      if (url.endsWith('/api/v1/dashboard')) return jsonResponse({ metrics: [], recentActivity: [] });
      return jsonResponse(null, 204);
    }));
  });

  it('loads the student directory and navigates to create student', async () => {
    renderApp();
    expect(await screen.findByRole('link', { name: 'ST-003' })).toBeInTheDocument();
    expect(screen.getByText('Mira Rao')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('link', { name: /add student/i }));
    expect(await screen.findByRole('heading', { name: 'Add student' })).toBeInTheDocument();
    expect(screen.getAllByRole('textbox')[0]).toBeRequired();
    expect(window.location.pathname).toBe('/app/students/new');
  });

  it('submits a new student profile to the API and opens its detail route', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return jsonResponse(user);
      if (url.endsWith('/api/v1/students') && init?.method === 'POST') return jsonResponse({ id: 42, admissionNo: 'ST-042', firstName: 'Tara', lastName: 'Das', status: 'ACTIVE' }, 201);
      if (url.endsWith('/api/v1/students/42')) return jsonResponse({ id: 42, admissionNo: 'ST-042', firstName: 'Tara', lastName: 'Das', status: 'ACTIVE', guardians: [] });
      if (url.endsWith('/api/v1/students/42/guardians') || url.endsWith('/api/v1/students/42/documents') || url.endsWith('/api/v1/students/42/history')) return jsonResponse([]);
      return jsonResponse({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, sortBy: 'createdAt', direction: 'DESC' });
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/app/students/new');
    renderApp();

    await userEvent.type(await screen.findByLabelText(/Admission number/), 'ST-042');
    await userEvent.type(screen.getByLabelText(/First name/), 'Tara');
    await userEvent.type(screen.getByLabelText(/Last name/), 'Das');
    await userEvent.click(screen.getByRole('button', { name: 'Create student' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/v1/students'), expect.objectContaining({ method: 'POST' })));
    expect(await screen.findByRole('heading', { name: 'Tara Das' })).toBeInTheDocument();
    expect(window.location.pathname).toBe('/app/students/42');
  });

  it('sends search and status filters to the student list endpoint', async () => {
    const fetchMock = vi.mocked(fetch);
    renderApp();
    await screen.findByRole('link', { name: 'ST-003' });
    await userEvent.type(screen.getByRole('textbox', { name: 'Search students' }), 'Mira');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Filter by status' }), 'ACTIVE');
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('status=ACTIVE'), expect.any(Object)));
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('search=Mira'), expect.any(Object));
  });

  it('shows duplicate admission identifiers beside their fields', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return jsonResponse(user);
      if (url.endsWith('/api/v1/admissions') && init?.method === 'POST') {
        return jsonResponse({
          message: 'A record with the same identifier already exists',
          fieldErrors: [{ field: 'admissionNo', message: 'is already in use' }],
        }, 409);
      }
      return jsonResponse(null, 204);
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/app/admissions/new');
    renderApp();

    await userEvent.type(await screen.findByLabelText(/Application number/), 'APP-100');
    await userEvent.type(screen.getByLabelText(/Student admission number/), 'ST-100');
    await userEvent.type(screen.getByLabelText(/First name/), 'Taylor');
    await userEvent.click(screen.getByRole('button', { name: 'Create draft' }));

    expect(await screen.findByText('admissionNo: is already in use')).toBeInTheDocument();
    expect(screen.getByText('is already in use')).toBeInTheDocument();
  });

  it('renders guardian relationship options and limits phone number to 10 digits', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return jsonResponse(user);
      if (url.endsWith('/api/v1/students/42')) {
        return jsonResponse({
          id: 42,
          admissionNo: 'ST-042',
          firstName: 'Tara',
          lastName: 'Das',
          status: 'ACTIVE',
          guardians: []
        });
      }
      if (url.endsWith('/api/v1/students/42/documents') || url.endsWith('/api/v1/students/42/history')) return jsonResponse([]);
      if (url.endsWith('/api/v1/students/42/guardians') && init?.method === 'POST') {
        return jsonResponse([{
          id: 1,
          name: 'Raj Das',
          relation: 'Father',
          phone: '+91 9876543210',
          email: null,
          address: null,
          occupation: null,
          primary: true
        }]);
      }
      return jsonResponse(null, 204);
    });
    vi.stubGlobal('fetch', fetchMock);
    window.history.replaceState({}, '', '/app/students/42');
    renderApp();

    expect(await screen.findByRole('heading', { name: 'Tara Das' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add guardian' }));

    // Check relationship dropdown options
    const relationSelect = screen.getByRole('combobox', { name: 'Relationship' });
    expect(relationSelect).toBeInTheDocument();
    expect(within(relationSelect).getByRole('option', { name: 'Father' })).toBeInTheDocument();
    expect(within(relationSelect).getByRole('option', { name: 'Mother' })).toBeInTheDocument();
    expect(within(relationSelect).getByRole('option', { name: 'Guardian' })).toBeInTheDocument();
    expect(within(relationSelect).getByRole('option', { name: 'Other' })).toBeInTheDocument();

    // Select relationship
    await userEvent.selectOptions(relationSelect, 'Father');

    // Fill name
    await userEvent.type(screen.getByLabelText(/Name/), 'Raj Das');

    // Test phone number 10-digit limit and leading 0 stripping
    const phoneInput = screen.getByPlaceholderText('98765 43210');
    expect(phoneInput).toHaveAttribute('maxLength', '10');

    // Type 12 digits with leading 0: 098765432100 -> only 10 digits (9876543210) should be accepted
    await userEvent.type(phoneInput, '098765432100');
    expect(phoneInput).toHaveValue('9876543210');

    // Submit guardian
    await userEvent.click(screen.getByRole('button', { name: 'Save guardian' }));

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('/api/v1/students/42/guardians'),
        expect.objectContaining({
          method: 'POST',
          body: expect.stringContaining('"relation":"Father"')
        })
      );
    });
  });

  it('renders My Children view without search filters for parent guardian', async () => {
    const parentUser = {
      id: 13,
      username: 'Doe9661',
      fullName: 'John Doe',
      roles: ['PARENT_GUARDIAN'],
      permissions: ['STUDENT:READ']
    };
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return jsonResponse(parentUser);
      if (url.includes('/api/v1/students?')) return jsonResponse(page);
      return jsonResponse(null, 204);
    }));
    renderApp();

    expect(await screen.findByRole('heading', { name: 'My Children' })).toBeInTheDocument();
    expect(await screen.findByText('Mira Rao')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: /search/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: /status/i })).not.toBeInTheDocument();
  });
});
