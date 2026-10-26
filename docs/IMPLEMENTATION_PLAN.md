# ERP Portal Recommended Implementation Plan

## Planning basis

This is an analysis-only implementation sequence for the requirements in `ERP_SRS_Extracted.txt` and the supplied project constitution. It is ordered by security and data dependencies. It does not assert that work has been implemented, and does not add requirements to the SRS. Resolve unspecified workflow choices as documented assumptions and obtain domain approval where they affect financial, student, privacy, or historical data behavior.

The current repository has a Spring Boot entry point and a broad V1 schema only. No functional module is complete. Keep the current V1 migration immutable once shared/applied; use additive forward migrations for approved changes.

## Delivery sequence

| Phase | Scope and requirement IDs | Main outputs | Exit evidence |
|---|---|---|---|
| 0. Baseline and environment | REQ-ARCH-001..005, REQ-SEC-006, NFR-OBS-001 | Confirm Java 21 build runtime; documented environment profiles; safe secret/TLS configuration policy; MySQL migration baseline; API error/trace conventions; package conventions; test strategy. Resolve H2/Flyway mismatch. | Clean compile; clean MySQL migration from empty DB and validation of existing schema; test-profile smoke; no secrets in source; API contract/error tests. |
| 1. Identity and authorization platform | REQ-AUTH-001..004, REQ-AUTHZ-001..005, REQ-USER-001, REQ-ADM-001..002, REQ-SEC-001..005 | Authentication/current-user/logout, password lifecycle, user and role/permission administration, permission checks, data-scope primitives, audit writer, safe global errors. Define controlled initial-admin bootstrap and token invalidation strategy as explicit assumptions if not fixed by SRS. | Positive/negative auth tests; generic invalid-login response; missing/expired/revoked token is 401; authenticated denial is 403; no write on denial; password-hash checks; role changes audited; scoped query tests. |
| 2. Academic foundation and student lifecycle | REQ-ACA-001..002, REQ-STU-001..006, REQ-STU-008 | Sessions/classes/sections; student search/detail/create/update; guardian relationships; admission/status transitions; enrollments; history-preserving transfer/exit. Add explicit enquiry workflow only after confirming the SRS admission flow and data requirements. | Validation and uniqueness API tests; role/scope tests; transition tests; guardian/enrollment integration; migration replay and rollback/recovery review. |
| 3. Student documents | REQ-STU-007, REQ-SEC-004, REQ-SEC-006 | Document metadata API, content storage boundary, allowlisted type/size checks, authorization, access/download policy, retention hooks. Choose storage backend and retention rules as documented decisions; do not store executable files under web root. | Valid/invalid type and size tests; access-scope tests; metadata and cleanup evidence; PII/access review. |
| 4. Academics and timetable | REQ-ACA-003..005 | Teacher assignments, overlap-aware timetable validation, homework, syllabus progress and actor/time history. | Teacher/section assignment authorization tests; concurrent/conflicting timetable tests; session history tests; UI journey verification. |
| 5. Student and staff attendance | REQ-ATT-001..005 | Student/staff marking, duplicate/idempotency behavior, date and section filters, controlled correction history, own/linked history views. | Duplicate submission contract tests; assigned-scope tests; correction permission/audit tests; date boundary/timezone tests; historical-session regression tests. |
| 6. Finance | REQ-FIN-001..009, NFR-DATA-001 | Versioned fee structures, due generation, payment and receipt posting, refund/reversal, discount approval, expense approval, scoped reports/exports and reconciliation. Explicitly define payment allocation, partial/overpayment, refund, and segregation-of-duties rules wherever the SRS is not sufficiently specific. | Transaction rollback tests; idempotency/concurrency tests; reconciliation against source transactions; SoD tests; permissions and date/scope tests; export totals/row checks; finance audit evidence. |
| 7. Operations | REQ-OPS-001..005 | Inventory and stock movement; library catalog/issue/return/fine; hostel capacity/bed allocation; support ticket state/history and comments. | No-negative-stock and concurrent issue tests; stock-ledger reconciliation; library borrower/copy invariants; hostel double-allocation tests; ticket lifecycle authorization/history tests. |
| 8. Administration and reporting | REQ-ADM-002..008 | Dashboard KPIs by role, approved reports, exports, bulk imports with row errors, versioned/audited settings, audit filtering. Role/permission management begins in phase 1; this phase completes administration features. | KPI-scope tests; deterministic report/date tests; export scope tests; import row-level validation/no partial corruption tests; settings/audit tests; audit filter tests. |
| 9. Frontend foundation and workflows | NFR-UX-001 and UI requirements across all functional groups | React/TypeScript/Vite shell, original identity, responsive sidebar/header, routing, API client, query cache, forms and validation, reusable tables/dialogs, persistent theme with system fallback. Implement each feature UI alongside its backend phase after API contracts stabilize. | Type-check/build; route and API error states; keyboard/accessibility checks; responsive checks at 1280×720 and 360px; browser-console/network checks; theme persistence and system fallback tests. |
| 10. Production readiness and NFR evidence | NFR-PERF-001..003, NFR-CAP-001..002, NFR-AVAIL-001, NFR-REC-001..002, NFR-SEC-001, NFR-OBS-001, REQ-SEC-003, REQ-SEC-007 | Performance/load profile, monitoring and alerts, encrypted backups and restore runbook, TLS deployment, dependency/security scans, privacy/retention controls, incident and operational procedures. | Recorded performance/capacity report; 99.5% availability operational plan/evidence; restore drill meeting RPO/RTO; vulnerability disposition; traceable secret/PII log review; export/import scope tests. |

Frontend should be implemented incrementally with each module rather than delayed until every backend module is finished. Phase 9 defines its shared foundation; each backend phase should include its corresponding pages and end-to-end journey.

## Per-requirement delivery gate

Do not mark a requirement complete because its table, route, or screen exists. Link each requirement ID to:

1. Approved behavior and any explicit assumption.
2. API contract and server-side permission/data-scope rule, where applicable.
3. Validation and safe error response.
4. Migration evidence, where persistence changes.
5. Unit, integration, API, and selected end-to-end evidence appropriate to risk.
6. Regression evidence for affected functionality.
7. Documentation and operational evidence where required.

Critical financial/security writes need transaction/concurrency tests. Denied operations need proof that no database mutation occurred. Exports must reuse read authorization and data scope. All filters and sort fields must be allowlisted.

## Decisions to resolve without inventing requirements

The SRS leaves some operational details to implementation decisions. Record decisions before encoding them: initial-admin provisioning; access/refresh token storage and revocation; account lockout/password reset; document storage/retention; timetable overlap semantics and school calendar; attendance correction cutoff/holidays; payment allocation, receipt numbering, refunds and reconciliation; discount/expense separation of duties; inventory adjustment and negative-stock policy; fine calculation; hostel capacity/lifecycle; import file formats and partial-failure policy; report definitions; branch scope if ever enabled; PII retention, consent, deletion/legal hold; deployment backup encryption and monitoring. Use least-privilege and reversible behavior while awaiting approval; do not infer new business rules from the videos.

## Delivery controls

- Work on one bounded module at a time; do not expand into unrelated cleanup.
- Inspect current dependencies before changes. Add or upgrade dependencies only when the requirement needs them and document the reason.
- Use only additive Flyway migrations; never reset or drop production data.
- Run the affected backend/frontend build, relevant unit/integration/API tests, migration verification, and affected regression checks after each module.
- Update `REQUIREMENT_TRACEABILITY.md` with links to code/tests/reports and change a status only after its exit evidence passes.
- Do not advance to the next module when the current module has unresolved acceptance failures; record blocked evidence and the specific dependency.
