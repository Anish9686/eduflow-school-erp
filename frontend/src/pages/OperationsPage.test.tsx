import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AppProviders } from '../app/AppProviders';
import { ThemeProvider } from '../theme/ThemeProvider';
import { AppRoutes } from '../routes';

const operationsUser = {
  id: 17,
  username: 'operations-manager',
  fullName: 'Operations Manager',
  roles: ['OPERATIONS'],
  permissions: ['INVENTORY:READ', 'INVENTORY:MANAGE', 'LIBRARY:READ', 'HOSTEL:READ', 'SUPPORT:READ'],
};

function response(body: unknown, status = 200) {
  return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

function renderApp() {
  return render(<ThemeProvider><AppProviders><AppRoutes /></AppProviders></ThemeProvider>);
}

describe('Operations workflows', () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  beforeEach(() => {
    window.sessionStorage.clear();
    window.sessionStorage.setItem('erp-portal-access-token', 'operations-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    window.history.replaceState({}, '', '/app/operations');
  });

  it('submits a valid inventory item and surfaces server field errors on conflict', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(operationsUser);
      if (url.endsWith('/operations/inventory/items') && init?.method === 'POST') {
        return response({ status: 409, message: 'Inventory code already exists', fieldErrors: [{ field: 'code', message: 'is already in use' }] }, 409);
      }
      return response([]);
    });
    vi.stubGlobal('fetch', fetchMock);
    renderApp();

    await userEvent.click(await screen.findByRole('button', { name: 'Add item' }));
    await userEvent.type(screen.getByLabelText(/Name/), 'Lab microscope');
    await userEvent.type(screen.getByLabelText(/Code/), 'LAB-01');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(/Inventory code already exists.*code: is already in use/)).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/api/v1/operations/inventory/items'), expect.objectContaining({ method: 'POST' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('loads and exposes only the operational area allowed by read/manage grants', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response({ ...operationsUser, permissions: ['INVENTORY:READ'] });
      return response([]);
    });
    vi.stubGlobal('fetch', fetchMock);
    renderApp();

    expect(await screen.findByRole('heading', { name: 'Inventory' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Inventory' })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Library' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add item' })).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/operations/inventory/items'), expect.anything());
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining('/operations/library/items'), expect.anything());
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining('/operations/hostel/rooms'), expect.anything());
    expect(fetchMock).not.toHaveBeenCalledWith(expect.stringContaining('/operations/support/tickets'), expect.anything());
  });

});
