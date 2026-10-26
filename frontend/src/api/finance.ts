import { apiFetch, apiGet, apiPost, apiDelete } from './client';

export type FinanceOption = { id: number; label: string; sessionId: number | null; classId: number | null };
export type FinanceOptions = { sessions: FinanceOption[]; classes: FinanceOption[]; categories: FinanceOption[]; students: FinanceOption[] };
export type FeeStructure = { id: number; sessionId: number; classId: number; className: string; categoryId: number; categoryName: string; amount: string; dueDate: string | null; effectiveFrom: string; effectiveTo: string | null; frequency: string; active: boolean };
export type FeeDemand = { id: number; studentId: number; studentName: string; feeStructureId: number; sessionId: number; billingPeriod: string | null; amountDue: string; amountPaid: string; discountAmount: string; balance: string; dueDate: string | null; status: string };
export type Payment = { id: number; studentId: number; demandId: number; receiptNo: string; referenceNo: string; amount: string; paymentMethod: string; paymentDate: string; status: string; issuedAt: string | null };
export type Discount = { id: number; studentId: number; demandId: number; amount: string; reason: string; requestedBy: number; approvalStatus: string; approvedBy: number | null; approvedAt: string | null; createdAt: string };
export type Expense = { id: number; category: string; description: string; amount: string; expenseDate: string; vendor: string | null; receiptUrl: string | null; approvalStatus: string; requestedBy: number; approvedBy: number | null; approvedAt: string | null; rejectionReason: string | null; createdAt: string };
export type FinanceSummary = { collections: string; refunds: string; expenses: string; outstanding: string; paymentCount: number; expenseCount: number };
export type Reconciliation = { recordType: string; recordId: number; recordedAmount: string; calculatedAmount: string; difference: string };

export const financeApi = {
  options: () => apiGet<FinanceOptions>('/api/v1/finance/options'),
  structures: () => apiGet<FeeStructure[]>('/api/v1/finance/structures'),
  createStructure: (body: unknown) => apiPost<FeeStructure>('/api/v1/finance/structures', body),
  demands: () => apiGet<FeeDemand[]>('/api/v1/finance/demands'),
  generateDemand: (body: unknown) => apiPost<FeeDemand>('/api/v1/finance/demands', body),
  deleteDemand: (id: number) => apiDelete<void>(`/api/v1/finance/demands/${id}`),
  payments: (from: string, to: string) => apiGet<Payment[]>('/api/v1/finance/payments', { from, to }),
  postPayment: (body: unknown) => apiPost<Payment>('/api/v1/finance/payments', body),
  receipt: (id: number) => apiGet<Payment>(`/api/v1/finance/receipts/${id}`),
  adjustPayment: (id: number, operation: 'refund' | 'reverse', reason: string) => apiPost<void>(`/api/v1/finance/payments/${id}/${operation}`, { reason }),
  discounts: () => apiGet<Discount[]>('/api/v1/finance/discounts'),
  requestDiscount: (body: unknown) => apiPost<{ id: number }>('/api/v1/finance/discounts', body),
  decideDiscount: (id: number, decision: 'approve' | 'reject', reason: string) => apiPost<void>(`/api/v1/finance/discounts/${id}/${decision}`, { reason }),
  expenses: () => apiGet<Expense[]>('/api/v1/finance/expenses'),
  createExpense: (body: unknown) => apiPost<{ id: number }>('/api/v1/finance/expenses', body),
  submitExpense: (id: number) => apiPost<void>(`/api/v1/finance/expenses/${id}/submit`),
  decideExpense: (id: number, decision: 'approve' | 'reject', reason: string) => apiPost<void>(`/api/v1/finance/expenses/${id}/${decision}`, { reason }),
  summary: (from: string, to: string) => apiGet<FinanceSummary>('/api/v1/finance/reports', { from, to }),
  reconciliation: () => apiGet<Reconciliation[]>('/api/v1/finance/reconciliation'),
  exportPayments: async (from: string, to: string) => {
    const response = await apiFetch(`/api/v1/finance/export?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`, {
      headers: { Accept: 'text/csv, text/plain, */*' }
    });
    return response.text();
  },
};
