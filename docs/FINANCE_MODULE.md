# Finance Module

## Rules

- Fee structures are effective-dated versions by session, class, and category. An active version cannot overlap another version for the same combination. Creating a later version closes the prior open version without deleting it.
- Demand generation requires the student's enrollment in the structure's class and session. Monthly, quarterly, and annual billing periods normalize to the first day of the applicable period. One-time structures cannot generate repeat demands. Fee component amounts are copied to demand items at generation time.
- Payments require a positive amount with at most two decimal places. There is no overpayment approval path. A payment cannot exceed the locked outstanding balance.
- Payment reference reuse with the same demand, amount, method, and date returns the existing transaction. A reused reference with different transaction data conflicts. Payment, receipt, demand balance, and audit writes share one transaction.
- Receipt numbers are derived from the unique client payment reference. Receipts and payments have no Finance delete operation.
- Refunds and reversals apply the full original payment once. The original payment and receipt remain; `finance_adjustments` records the type, amount, reason, actor, and time. Refund and reversal require `FEE:REFUND`.
- Discount requests remain pending until a user with `FEE_DISCOUNT:APPROVE` decides them. Requesters cannot approve their own requests. Approval applies the discount to the demand balance.
- Expenses transition from DRAFT to PENDING to APPROVED or REJECTED. Only the requester submits a draft; requesters cannot approve their own expenses. Decisions and transitions are audited.
- Finance report date bounds are inclusive. Collections count posted payments; refunds are reported separately; expenses include approved records; outstanding amounts come from open fee demands. Reconciliation compares each demand's stored paid amount with its posted payment ledger.
- A user with only discount-request or expense-management grants sees only their own requests/expenses. Finance reports and payment exports are institution-wide, consistent with the current single-institution SRS assumption.

## API

Base path: `/api/v1/finance`

- `GET/POST /structures`
- `GET/POST /demands`
- `GET/POST /payments?from=YYYY-MM-DD&to=YYYY-MM-DD`
- `GET /receipts/{paymentId}`
- `POST /payments/{paymentId}/refund` and `POST /payments/{paymentId}/reverse`
- `GET/POST /discounts`, `POST /discounts/{id}/approve`, `POST /discounts/{id}/reject`
- `GET/POST /expenses`, `POST /expenses/{id}/submit`, `POST /expenses/{id}/approve`, `POST /expenses/{id}/reject`
- `GET /reports?from=YYYY-MM-DD&to=YYYY-MM-DD`, `GET /reconciliation`, `GET /export?from=YYYY-MM-DD&to=YYYY-MM-DD`

## Verification

`FinanceTransactionIntegrationTest` uses isolated H2 tables to exercise the transaction boundary, replay/concurrency, rollback, approvals, adjustments, API authorization, and export date filtering. `FinancePage.test.tsx` checks route permission and the confirmation-to-receipt flow. `Finance.postman_collection.json` provides environment-driven manual API scenarios.

MySQL migration execution and Postman collection execution require an isolated MySQL test database and role tokens. They were not run as part of the current local verification; the configured local MySQL listener was not treated as a disposable test target.