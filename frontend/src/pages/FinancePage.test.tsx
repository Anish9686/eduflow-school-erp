import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider } from '../theme/ThemeProvider';
import { AppProviders } from '../app/AppProviders';
import { AppRoutes } from '../routes';

const cashier = { id: 4, username: 'cashier', fullName: 'Casey Cashier', roles: ['ACCOUNTANT'], permissions: ['FEE:COLLECT'] };
const feeDemand = { id: 77, studentId: 12, studentName: 'Jordan Student', feeStructureId: 2, sessionId: 1, billingPeriod: '2026-10-01', amountDue: '80.00', amountPaid: '20.00', discountAmount: '0.00', balance: '60.00', dueDate: '2026-10-10', status: 'PARTIAL' };
const payment = { id: 91, studentId: 12, demandId: 77, receiptNo: 'R-PAY-91', referenceNo: 'PAY-91', amount: '25.00', paymentMethod: 'CASH', paymentDate: '2026-10-06', status: 'POSTED', issuedAt: '2026-10-06T10:00:00' };
function response(data: unknown, status = 200) { return { ok: status >= 200 && status < 300, status, json: async () => data, text: async () => String(data) } as Response; }
function renderRoutes() { return render(<ThemeProvider><AppProviders><AppRoutes /></AppProviders></ThemeProvider>); }

describe('Finance workflows', () => {
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
  beforeEach(() => {
    window.sessionStorage.clear();
    window.sessionStorage.setItem('erp-portal-access-token', 'finance-test-token');
    window.sessionStorage.setItem('erp-portal-access-expires', String(Date.now() + 60_000));
    window.history.replaceState({}, '', '/app/finance');
  });

  it('requires confirmation before posting and opens the issued receipt', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response(cashier);
      if (url.endsWith('/finance/options')) return response({ sessions: [], classes: [], categories: [], students: [] });
      if (url.endsWith('/finance/structures')) return response([]);
      if (url.endsWith('/finance/demands')) return response([feeDemand]);
      if (url.includes('/finance/payments?')) return response([]);
      if (url.endsWith('/finance/payments') && init?.method === 'POST') return response(payment, 201);
      if (url.endsWith('/finance/receipts/91')) return response(payment);
      return response([], 200);
    });
    vi.stubGlobal('fetch', fetchMock);
    renderRoutes();

    expect(await screen.findByRole('heading', { name: 'Collect payment' })).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText(/Outstanding demand/), '77');
    await userEvent.type(screen.getByLabelText(/Amount/), '25.00');
    await userEvent.type(screen.getByLabelText(/Payment reference/), 'PAY-91');
    await userEvent.click(screen.getByRole('button', { name: 'Post payment & issue receipt' }));
    expect(screen.getByRole('dialog', { name: 'Confirm payment' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith('/finance/payments') && init?.method === 'POST')).toBe(false);
    await userEvent.click(screen.getByRole('button', { name: 'Confirm operation' }));

    expect(await screen.findByRole('heading', { name: 'R-PAY-91' })).toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/finance/payments'), expect.objectContaining({ method: 'POST' })));
    expect(screen.getByText('₹25.00')).toBeInTheDocument();
  });

  it('blocks the Finance route without a Finance permission', async () => {
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => String(input).endsWith('/api/v1/me') ? response({ ...cashier, permissions: [] }) : response([])));
    renderRoutes();
    expect(await screen.findByRole('heading', { name: 'You don’t have access to this area' })).toBeInTheDocument();
  });

  it('allows export-only users to export without showing report data', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response({ ...cashier, permissions: ['FINANCE_REPORT:EXPORT'] });
      if (url.endsWith('/finance/options')) return response({ sessions: [], classes: [], categories: [], students: [] });
      return response([]);
    });
    vi.stubGlobal('fetch', fetchMock);
    renderRoutes();
    expect(await screen.findByRole('button', { name: 'Export payments CSV' })).toBeInTheDocument();
    expect(screen.queryByText('Collections')).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Reconciliation' })).not.toBeInTheDocument();
  });

  it('shows configured fee categories when creating a fee structure', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response({ ...cashier, permissions: ['FEE:MANAGE'] });
      if (url.endsWith('/finance/options')) return response({
        sessions: [{ id: 1, label: '2026-2027', sessionId: null, classId: null }],
        classes: [{ id: 2, label: 'Class 1', sessionId: null, classId: null }],
        categories: [{ id: 3, label: 'Tuition', sessionId: null, classId: null }],
        students: [],
      });
      if (url.endsWith('/finance/structures')) return response([]);
      return response([]);
    });
    vi.stubGlobal('fetch', fetchMock);
    renderRoutes();

    const category = await screen.findByLabelText(/Fee category/);
    expect(category).toBeEnabled();
    expect(screen.getByRole('option', { name: 'Tuition' })).toHaveValue('3');
  });

  it('explains when fee categories are not configured', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input);
      if (url.endsWith('/api/v1/me')) return response({ ...cashier, permissions: ['FEE:MANAGE'] });
      if (url.endsWith('/finance/options')) return response({ sessions: [], classes: [], categories: [], students: [] });
      return response([]);
    });
    vi.stubGlobal('fetch', fetchMock);
    renderRoutes();

    expect(await screen.findByLabelText(/Fee category/)).toBeDisabled();
    expect(screen.getByText('No fee categories are configured. Contact an administrator.')).toBeInTheDocument();
  });
});