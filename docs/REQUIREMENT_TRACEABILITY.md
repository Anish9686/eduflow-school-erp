# ERP Portal Requirement Traceability

## Status key and inspection baseline

Assessment date: 2026-09-29. Source of functional requirements: `ERP_SRS_Extracted.txt` and the approval-ready SRS. The supplied project constitution adds implementation and verification constraints. The reference videos are UX evidence, not authorization to invent business rules.

- **Not implemented**: no application behavior or acceptance evidence exists in the inspected repository.
- **Foundation only**: configuration/schema/documentation/dependency groundwork exists, but the requirement's behavior and its acceptance evidence are absent.
- **Not verified**: some supporting configuration/schema exists, but no measurable test or operational evidence is present.

No functional requirement is complete. `V1__baseline_schema.sql` provides tables and some constraints for multiple domains, but there are no backend domain classes/APIs, React application, or executable tests. `springdoc` configuration is not an API. A schema comment is not workflow evidence.

## Functional requirements

| Requirement ID | SRS requirement | Target module | Current evidence | Status / acceptance evidence still required |
|---|---|---|---|---|
| REQ-AUTH-001 | Valid user authenticates and receives authenticated context | `identity` | JJWT dependency/config and identity tables only | Not implemented; login/context API and positive auth tests |
| REQ-AUTH-002 | Invalid credentials rejected without account enumeration | `identity` | None | Not implemented; generic 401 response tests for unknown user and wrong password |
| REQ-AUTH-003 | Missing/expired authentication is rejected | `platform/security` | Spring Security dependency only | Not implemented; protected endpoint returns 401 tests |
| REQ-AUTH-004 | Logout invalidates access according to the configured strategy | `identity` | `revoked_tokens` table and expiration index only | Not implemented; logout and subsequent protected-call test; token strategy documented |
| REQ-AUTHZ-001 | Protected API operations enforce authentication and permission | `platform/security` | RBAC schema and dependency only | Not implemented; positive/negative permission API tests |
| REQ-AUTHZ-002 | Record access is restricted by configured data scope | `platform/scope` + domain modules | No scope code or domain queries | Not implemented; in-scope/out-of-scope list/detail/update/export tests |
| REQ-AUTHZ-003 | Role/permission administration is restricted and audited | `identity`, `platform/audit` | RBAC and audit tables only | Not implemented; denied/allowed administration and audit tests |
| REQ-AUTHZ-004 | Unauthorized requests do not mutate data | `platform/security` + domain services | No API/service behavior | Not implemented; negative API request plus database no-mutation evidence |
| REQ-AUTHZ-005 | Exports use the same permission and scope as reads | `administration` + domain reports | No export endpoints | Not implemented; export permission/scope tests |
| REQ-USER-001 | Authorized admins create/update/deactivate/view users | `identity` | `users`, `roles`, and join tables only | Not implemented; user API, validation, authorization, lifecycle tests |
| REQ-STU-001 | Authorized user creates a student with mandatory fields | `student` | `students` table only | Not implemented; create API and missing-field 400 test |
| REQ-STU-002 | Admission/student identifier is unique | `student` | Unique constraint on `students.admission_no` | Foundation only; duplicate API returns documented conflict/validation response |
| REQ-STU-003 | Student can link guardians under configured rules | `student` | `guardians` and `student_guardians` tables | Foundation only; validated create/retrieve relationship API tests |
| REQ-STU-004 | Student list supports search/filter/sort/pagination | `student` | Status/admission-date indexes only | Not implemented; deterministic pagination and allow-listed filter/sort tests |
| REQ-STU-005 | Student detail is scope-controlled | `student`, `platform/scope` | No detail endpoint or scope code | Not implemented; cross-scope detail non-disclosure tests |
| REQ-STU-006 | Admission status follows valid transitions | `student` | Status enum only | Not implemented; transition matrix and invalid transition tests |
| REQ-STU-007 | Documents validate type/size and metadata | `student` | `student_documents` metadata table only | Not implemented; storage/access policy and invalid upload tests |
| REQ-STU-008 | Transfer/exit preserves history | `student` | Student status/exit columns and enrollment table | Foundation only; transition service/history preservation/regression tests |
| REQ-ACA-001 | Create academic sessions, classes, and sections | `academic` | Corresponding tables and some unique keys | Foundation only; CRUD validation/API and migration integration tests |
| REQ-ACA-002 | Assign subjects to classes/sections and teachers | `academic` | `section_subjects`, `subjects`, `staff` tables | Foundation only; assignment APIs and teacher-scope tests |
| REQ-ACA-003 | Timetable prevents configured conflicts | `academic` | `timetable_slots` with exact start-time unique keys | Foundation only; interval-overlap and conflict/concurrency tests; current keys do not prove overlap prevention |
| REQ-ACA-004 | Teachers create homework within assigned scope | `academic` | `homework` table only | Not implemented; assignment authorization and API tests |
| REQ-ACA-005 | Record syllabus progress by session/subject | `academic` | `syllabus_progress` table only | Not implemented; actor/time persistence and scope tests |
| REQ-ATT-001 | Assigned teacher/staff can mark attendance | `attendance` | Attendance tables and marker FK only | Not implemented; assigned-scope marking API tests |
| REQ-ATT-002 | Duplicate attendance is prevented/idempotently handled | `attendance` | Unique student/date/session key | Foundation only; documented duplicate contract and concurrent API tests |
| REQ-ATT-003 | Attendance correction is restricted and audited | `attendance` + `platform/audit` | Correction-history table only | Not implemented; permission, reason, old/new value, actor, timestamp tests |
| REQ-ATT-004 | Attendance supports date/class/section filters | `attendance` | Date/student/section-date indexes | Foundation only; scoped filter and date-boundary tests |
| REQ-ATT-005 | Parent/student sees only permitted history | `attendance`, `platform/scope` | No identity link/query enforcement | Not implemented; own/linked-record API scope tests |
| REQ-FIN-001 | Define fee structures by session/class/category | `finance` | V7 effective-date columns; overlapping active versions rejected; prior versions closed, not deleted | Service/API implementation; MySQL migration execution pending |
| REQ-FIN-002 | Generate student dues from applicable structure | `finance` | Enrollment validation, frequency-normalized billing periods, unique demand period, copied fee line snapshots | H2 integration tests cover monthly demand and duplicate generation |
| REQ-FIN-003 | Authorized cashier posts payment and receipt atomically | `finance` | `@Transactional` payment, receipt and demand balance mutation | H2 integration covers success and forced receipt-insert rollback; API guarded by `FEE:COLLECT` |
| REQ-FIN-004 | Reject invalid overpayment/amount except approved path | `finance` | `DECIMAL(12,2)`, positive validation, unique payment reference and locked outstanding balance | H2 integration covers invalid amount, overpayment, exact replay, conflicting reference and concurrent replay |
| REQ-FIN-005 | Refund/reversal is permission-controlled and audited | `finance`, shared `AuditService` | `FEE:REFUND`, immutable `finance_adjustments`, payment status update; no delete path | H2/API tests cover denial, audit, refund, reversal, retained receipt/payment, reconciliation |
| REQ-FIN-006 | Discount/concession follows approval workflow | `finance` | Pending/approved/rejected states, requester/approver audit fields and self-approval denial | H2 integration tests cover request, approval and self-approval prevention |
| REQ-FIN-007 | Record expenses with approval state | `finance` | DRAFT/PENDING/APPROVED/REJECTED, requester/approver evidence and submit/decision transitions | H2 integration tests cover submit, approval and self-approval prevention |
| REQ-FIN-008 | Finance exports honor role/date/data scope | `finance` | `FINANCE_REPORT:EXPORT`, inclusive date filter; requester-only discount/expense queues | H2/API tests cover denied cashier export and authorized date-filtered export; scope is single-institution as assumed by SRS |
| REQ-FIN-009 | Finance totals reconcile to transactions | `finance` | Reports read payments, adjustment ledger, approved expenses and demand balances; reconciliation compares demand paid to posted transactions | H2 integration covers ledger reconciliation after refund/reversal |
| REQ-OPS-001 | Record inventory items and stock movements | `operations.inventory` | Inventory and stock movement tables | Not implemented; transactional balance update/ledger reconciliation |
| REQ-OPS-002 | Reject negative stock unless explicitly configured | `operations.inventory` | Check on `current_stock` only | Foundation only; movement transaction/concurrency tests and approved policy |
| REQ-OPS-003 | Library supports catalog, issue/return, and fines | `operations.library` | Catalog and issue tables | Not implemented; availability, active issue, borrower, return/fine lifecycle tests |
| REQ-OPS-004 | Hostel allocation prevents double allocation | `operations.hostel` | Allocation unique key includes room, bed, status, session | Foundation only; capacity/concurrency/history tests; lifecycle invariant still needs service proof |
| REQ-OPS-005 | Support tickets have status, priority, assignee, and history | `operations.support` | Ticket and comment tables | Not implemented; state-transition/assignment/comment history APIs and tests |
| REQ-ADM-001 | Authorized admins manage roles and permissions | `identity` | RBAC schema only | Not implemented; protected role/permission APIs and access-change tests |
| REQ-ADM-002 | Role/permission changes create audit events | `identity`, `platform/audit` | Audit table only | Not implemented; event actor/target/action/time assertions |
| REQ-ADM-003 | Dashboards show role-appropriate KPIs | `administration` | No dashboard APIs or UI | Not implemented; KPI scope tests for each role |
| REQ-ADM-004 | Reports support required filters/date boundaries | `administration` + domain modules | No report code | Not implemented; deterministic filter/date-boundary API tests |
| REQ-ADM-005 | Exports are permission-controlled and scope-aware | `administration` + domain modules | No export code | Not implemented; export permission/data-scope tests |
| REQ-ADM-006 | Bulk imports validate rows and report failures without corruption | `administration` | No import endpoint/job/result model | Not implemented; row-error, atomicity/partial-policy, corruption-prevention tests |
| REQ-ADM-007 | Material business settings are audited/versioned where required | `administration` | No settings model/API | Not implemented; approved setting list and version/audit tests |
| REQ-ADM-008 | Audit logs filter by actor/entity/action/date | `administration`, `platform/audit` | `audit_logs` and indexes only | Not implemented; scoped filter API, authorization, date-boundary tests |

## Architecture and security requirements

| Requirement ID | SRS requirement | Target module | Current evidence | Status / acceptance evidence still required |
|---|---|---|---|---|
| REQ-ARCH-001 | Frontend communicates through versioned REST; no direct DB access | Frontend platform + API | No frontend or API | Not implemented; browser/API contract verification |
| REQ-ARCH-002 | Business rules reside in services/domain logic, not only UI/controllers | Domain application services | No domain code | Not implemented; code review plus domain tests |
| REQ-ARCH-003 | Schema changes are migration-controlled and reproducible | Platform persistence | Flyway dependencies/config and one V1 migration; Hibernate `validate` | Foundation only; clean MySQL migration/replay and validation evidence |
| REQ-ARCH-004 | Critical writes are transactional and deterministic | Domain application services | No services/transaction boundaries | Not implemented; rollback/idempotency/concurrency tests |
| REQ-ARCH-005 | Common API errors follow the approved error contract | `platform/api`, `platform/exception` | No API/exception handler | Not implemented; schema and HTTP status/field/path/trace tests |
| REQ-SEC-001 | Passwords are adaptively hashed; never plaintext/logged | `identity`, `platform/observability` | `password_hash` column comment; no password code | Not implemented; hash verification and log-redaction evidence |
| REQ-SEC-002 | Protected APIs enforce authentication and authorization server-side | `platform/security` | Security dependency only | Not implemented; API positive/negative authz tests |
| REQ-SEC-003 | Production traffic uses TLS | Deployment/platform | No deployment/proxy configuration | Not implemented; deployment configuration review |
| REQ-SEC-004 | Input validation and safe errors prevent leakage | API/domain validation | Validation dependency only | Not implemented; malformed-input and error-disclosure tests |
| REQ-SEC-005 | Sensitive actions create audit events | `platform/audit` + domain modules | Audit table only | Not implemented; sensitive-action audit tests |
| REQ-SEC-006 | Secrets are environment-managed, not source-controlled | Deployment/platform configuration | `DB_PASSWORD` and `APP_JWT_SECRET` placeholders; datasource has empty-password fallback | Foundation only; fail-closed production profile, secret scan, and deploy review |
| REQ-SEC-007 | Exports/bulk operations obey scope and permission | `administration`, `platform/scope` | No import/export code | Not implemented; row-scope and permission tests |

## Non-functional requirements

| Requirement ID | SRS target | Target module/evidence | Current evidence | Status / acceptance evidence still required |
|---|---|---|---|---|
| NFR-PERF-001 | API p95 ≤500 ms, p99 ≤1.5 s at baseline load | API/load engineering | Some indexes/config only | Not verified; define deployment profile and record load report |
| NFR-PERF-002 | List endpoints p95 ≤800 ms with indexed filters/pagination | API/persistence | Some indexes, no endpoints | Not verified; representative data/query plans/load report |
| NFR-PERF-003 | Exports ≤100,000 rows within 60 s or async | Reporting/export | No export | Not implemented; measured export or asynchronous job evidence |
| NFR-CAP-001 | 5,000 concurrent authenticated sessions; 500 active API users | Deployment/load engineering | No deployment or load evidence | Not verified; load test on stated profile |
| NFR-CAP-002 | 100,000 students; 1,000 staff; five years attendance/finance dataset | Data/performance engineering | Schema tables only | Not verified; representative dataset and capacity report |
| NFR-AVAIL-001 | Monthly availability ≥99.5% excluding approved maintenance | Operations | No monitoring/SLA evidence | Not verified; deployment SLO and monitoring evidence |
| NFR-REC-001 | Standard DB backup RPO ≤24 hours | Operations/DBA | No backup job or runbook | Not implemented; encrypted backup schedule and restore validation |
| NFR-REC-002 | Restore RTO ≤4 hours | Operations/DBA | No restore drill | Not implemented; timed recovery drill and recorded checks |
| NFR-SEC-001 | Critical/high vulnerabilities resolved or risk-accepted before production | Security/release | No scan evidence | Not verified; dependency/app scan and signed disposition |
| NFR-UX-001 | Primary workflows usable at 1280×720 and responsive to 360px | Frontend | Frontend directory empty | Not implemented; browser/device viewport evidence |
| NFR-OBS-001 | Timestamp, level, service, trace ID logged; secrets/PII excluded | `platform/observability` | Logging pattern includes MDC trace placeholder; app logger DEBUG | Foundation only; trace propagation and redaction tests/review |
| NFR-DATA-001 | Critical finance/audit records preserve transactions and audit evidence | Finance + audit + persistence | Finance/audit tables only | Foundation only; transaction/integrity/audit integration tests |

## Cross-cutting implementation coverage

| Project/SRS constraint | Mapping | Current assessment |
|---|---|---|
| Dark/light theme, global toggle, persisted preference, system fallback | Frontend `shell`/styles | Not implemented; no frontend source |
| REST `/api/v1/...` | Backend platform API | No endpoints found |
| DTO/entity separation | Backend domain modules | No DTOs/entities found |
| 401 unauthenticated; 403 authenticated but unauthorized | Platform security/API | No security chain or API error mapping found |
| Server-side permission and data scope | Platform security/scope and domain services | Not implemented |
| No destructive schema reset; Flyway-only schema evolution | Persistence | One V1 exists; future changes should be additive and forward-only |
| Regression verification after module changes | Build/test pipeline | No test classes or pipeline configuration found |
| Original branding and no reference-video personal/proprietary identity | Frontend product design | No product UI exists; preserve during design |
| Privacy, consent, retention, access/correction/deletion, breach and vendor controls | Product policy + security + operations | Not implemented/documented in application; deployment jurisdiction and institution policy remain assumptions to resolve |

## Requirement completion rule

Change a row to complete only when code, migration (if applicable), authorization/scope, validation, API/UI behavior, and requirement-appropriate tests pass. Add paths to the implementation and test/report artifacts as evidence. For operational targets, attach measured deployment evidence; source code alone cannot establish availability, capacity, RPO/RTO, or performance.

See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for the recommended delivery sequence and phase exit criteria.
