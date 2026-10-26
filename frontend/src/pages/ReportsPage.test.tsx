import { render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppProviders } from '../app/AppProviders';
import { ReportsPage } from './ReportsPage';

function renderReports() { return render(<AppProviders><ReportsPage /></AppProviders>); }

describe('ReportsPage', () => {
  beforeEach(() => {
    window.sessionStorage.setItem('erp-portal-access-token', 'test-token');
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/reports')) return Response.json([{ code: 'student-directory', title: 'Student directory', description: 'Scoped students', filters: [{ key: 'search', label: 'Search', type: 'text', options: [] }], columns: [{ key: 'admissionNo', label: 'Admission no.' }], sortableFields: ['admissionNo'], formats: ['CSV'] }]);
      return Response.json({ code: 'student-directory', columns: [{ key: 'admissionNo', label: 'Admission no.' }], content: [{ admissionNo: 'A-001' }], page: 0, size: 25, totalElements: 1, totalPages: 1, sortBy: 'admissionNo', direction: 'ASC' });
    }));
  });
  afterEach(() => { vi.unstubAllGlobals(); window.sessionStorage.clear(); });

  it('loads the authorized report selector and renders report results', async () => {
    renderReports();
    await waitFor(() => expect(vi.mocked(fetch).mock.calls.length).toBeGreaterThanOrEqual(2));
    expect(vi.mocked(fetch).mock.calls.map(call => String(call[0]))).toEqual(expect.arrayContaining([expect.stringContaining('/student-directory?')]));
    expect(await screen.findByText('A-001')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Student directory' })).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/api/v1/reports'), expect.objectContaining({ headers: expect.any(Headers) }));
  });

  it('shows an error when report catalog cannot load', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => Response.json({ message: 'forbidden' }, { status: 403 })));
    renderReports();
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('permission'));
  });
});
