# EduFlow RBAC Audit and Targeted Repair

**Audit date:** 2026-10-15  
**Scope:** Existing authentication, role/permission resolution, protected API methods, frontend route/menu guards, and data-scope implementation.  
**Repository baseline:** This workspace has no `.git` directory, so pre-existing edits could not be compared or attributed. Only the files listed under Changes Made were edited in this pass.

## Implementation map

| Layer | Existing source of truth / implementation |
|---|---|
| Role and permission persistence | MySQL `roles`, `permissions`, `user_roles`, and `role_permissions`; catalog/role seed migrations V3–V9 |
| Authenticated identity | Opaque bearer tokens; `auth_sessions` stores SHA-256 token digests, and `AuthStore.findActiveSession` joins active user status and reloads role/permission grants for each request |
| Backend authorities | `BearerAuthenticationFilter` emits `ROLE_<code>` and exact `MODULE:ACTION` permission authorities |
| Endpoint authorization | `SecurityConfiguration` requires authentication by default; method security is enabled; controller `@PreAuthorize` expressions check exact permissions |
| User/role administration | `UserManagementController`, `RoleManagementController`, `RbacAdminController`, `UserManagementService`, `RoleManagementService`, `RbacService` |
| Frontend identity | `AuthProvider` loads `/api/v1/me`; permission checks compare exact permission strings from that response |
| Frontend routes/menu | `frontend/src/routes/index.tsx` and `frontend/src/layouts/AppLayout.tsx` |
| Data scope inspected | `StudentRepository` and Finance read queries apply `students.user_id` for STUDENT and guardian relationship checks for PARENT_GUARDIAN. The schema has no institution/branch tenant key. |
| Tests inspected | `AuthenticationRbacIntegrationTest`, administration integration tests, `AppShell.test.tsx`, and module frontend tests |

## Findings

### RBAC-001 — User create/import could grant arbitrary roles

- **Severity:** HIGH
- **Affected roles:** Any authenticated role granted `USER:CREATE` or `USER:IMPORT` without `RBAC:USER_ROLE_UPDATE`.
- **Affected APIs:** `POST /api/v1/admin/users`; both CSV forms of `POST /api/v1/admin/import/users`.
- **Expected:** Creating/importing user records must not implicitly grant the separate role-assignment authority. Role assignments require the canonical RBAC permission.
- **Actual:** `CreateUserRequest.roleCodes` and CSV `roles` were accepted and persisted after checking only the respective create/import permission. A caller could assign `SUPER_ADMIN` to a newly created account.
- **Root cause:** Role assignment in `UserManagementService` and `ImportExportService` lacked an independent `RBAC:USER_ROLE_UPDATE` check.
- **Impact:** Privilege escalation to every permission granted to the assigned role.
- **Minimal repair:** Require `RBAC:USER_ROLE_UPDATE` when role codes are requested during create. In CSV import, reject the individual row when the role column requests a role but the actor lacks that permission. Role-less create/import remains possible with the relevant create/import grant.
- **Regression tests:** Backend integration tests cover both paths and assert the target account is not persisted; backend security integration suite passes.

### RBAC-002 — Operations frontend accepted role name as an access grant

- **Severity:** MEDIUM (frontend authorization inconsistency; backend endpoints remained protected)
- **Affected role/module:** SUPER_ADMIN / Operations.
- **Affected route:** `/app/operations` and Operations sidebar entry.
- **Expected:** Frontend route and navigation should reflect the same permission grants used by protected APIs; backend remains authoritative.
- **Actual:** The frontend allowed `SUPER_ADMIN` by role name even if `/api/v1/me` returned no Operations permission. API calls still required permissions, producing an inaccessible workspace.
- **Root cause:** The shared `PermissionSetPage` and navigation predicate supported an additional role bypass for Operations.
- **Impact:** UI showed an inaccessible protected module and disagreed with server authorization. No backend data exposure was found through this bypass.
- **Minimal repair:** Removed role-name authorization from the Operations menu and route. Access now requires one of its existing Operations read permissions.
- **Regression tests:** Frontend test verifies a SUPER_ADMIN without grants cannot see the menu or directly enter the route; existing permission-granted workflow test remains.

### RBAC-003 — Finance read paths did not apply student/guardian fee scope

- **Severity:** HIGH
- **Affected roles:** STUDENT and PARENT_GUARDIAN when granted the corresponding Finance read/report permission.
- **Affected APIs/data:** Finance options, fee demands, payments, receipts, payment export, finance summary, reconciliation, discounts, and finance payment reports/exports.
- **Expected:** Fee/payment reads and exports expose only the student's own or guardian-linked records. Summary and reconciliation must use that same scope. Discount records must be limited to own/linked students for these roles.
- **Actual:** Several service queries used broad permission checks without applying student ownership or guardian linkage. Summary aggregates and reconciliation could disclose institution-wide fee totals; reports reused the unscoped payment query. The finance options endpoint also exposed all student names.
- **Root cause:** Finance read methods accepted no authenticated principal or did not include the student/guardian relationship in their SQL predicates.
- **Impact:** A learner or guardian with a read grant could view fee-related PII or data belonging to another student, or infer institution-wide financial totals.
- **Minimal repair:** Apply authenticated `students.user_id` and `student_guardians`/`guardians.user_id` filters across these reads. Scope summary inputs without changing its calculations; expense totals/counts are omitted for learner/guardian principals. Payment reports and exports reuse the scoped query.
- **Regression tests:** `FinanceTransactionIntegrationTest.studentFeeReadsAndExportsAreRestrictedToOwnRecords` verifies ownership filtering for demands, payments, exports, summary, discounts, and reconciliation. `AuthenticationRbacIntegrationTest` remains green.

### RBAC-004 — Clean migration grants do not encode the SRS role matrix

- **Severity:** MEDIUM (role provisioning/availability gap; not an authorization bypass)
- **Affected roles:** All standard roles other than SUPER_ADMIN.
- **Expected:** The supplied SRS matrix defines baseline module access for ADMIN, MANAGEMENT, TEACHER, ACCOUNTANT, and Parent/Student roles.
- **Actual:** The inspected migration chain seeds the permission catalogue and grants it to SUPER_ADMIN, but seeds no default permissions for other standard roles. They can therefore receive no access until an authorized operator assigns grants.
- **Impact:** A clean installation can appear to deny documented role capabilities. This is fail-closed, but does not match the supplied role matrix.
- **Disposition:** Documented only. Existing runtime grants were not queried, and changing default grants would create persistent privilege changes beyond the confirmed security defects. Provisioning should be reconciled against the approved matrix before release.

## Role and permission inventory

Migrations seed these roles: `SUPER_ADMIN`, `ADMIN`, `MANAGEMENT`, `TEACHER`, `ACCOUNTANT`, `STAFF_ADMIN`, `LIBRARIAN`, `RECEPTION_ADMISSION`, `INVENTORY_MANAGER`, `SUPPORT_AGENT`, `PARENT_GUARDIAN`, and `STUDENT`.

The permission catalogue uses `MODULE:ACTION` codes. The migration chain grants catalogue permissions to `SUPER_ADMIN` (including new module permissions in later migrations). Other system roles receive no default permissions in the inspected migration chain; grants are intended to be explicitly assigned through the audited RBAC administration APIs. Existing database contents were not queried in this task, so production/runtime grants may differ from clean-migration defaults.

| Role | Seeded default grants | Scope notes |
|---|---|---|
| SUPER_ADMIN | All permissions seeded through V9 | No branch dimension exists in the inspected schema |
| ADMIN | None in migration seed | Effective grants depend on stored role-permission rows |
| MANAGEMENT | None in migration seed | Effective grants depend on stored role-permission rows |
| TEACHER | None in migration seed | Teaching-assignment scope was not established by this audit |
| ACCOUNTANT | None in migration seed | Finance scope depends on stored grants; no branch dimension |
| STAFF_ADMIN | None in migration seed | Effective grants depend on stored role-permission rows |
| LIBRARIAN | None in migration seed | Effective grants depend on stored role-permission rows |
| RECEPTION_ADMISSION | None in migration seed | Effective grants depend on stored role-permission rows |
| INVENTORY_MANAGER | None in migration seed | Effective grants depend on stored role-permission rows |
| SUPPORT_AGENT | None in migration seed | Effective grants depend on stored role-permission rows |
| PARENT_GUARDIAN | None in migration seed | Student queries inspected use linked guardian records |
| STUDENT | None in migration seed | Student queries inspected use the authenticated user ID |

This table reports migration defaults, not a claim about the current live database. The audited code uses exact database permission codes; unknown permission codes are not treated as grants. Roles are resolved from stored codes without a database `ROLE_` prefix; Spring's role authority prefix is added only when building authorities.

## Data-scope and authorization review

- Protected REST endpoints use authentication by default and method-level permission checks. Admin APIs also have method-level authorization; the broad authenticated matcher does not replace their method checks.
- Exports have separate `USER:EXPORT` and `AUDIT:EXPORT` checks. User and audit reads use their respective read permissions.
- Student list/detail query methods inspected apply user ownership for STUDENT and guardian-link filtering for PARENT_GUARDIAN. Finance fee reads, options, summary, reconciliation, discounts, payment reports, and exports now apply equivalent own/linked-student scope.
- No institution/branch key was found in the schema files inspected, so cross-branch isolation cannot be asserted or tested for this single-workspace schema.
- Teacher assignment scope and per-role scope for Finance, Attendance, and Operations were not proven by this focused RBAC test run. Several endpoints are permission protected, but that alone does not establish row-level scope. Treat these paths as unverified, not passed.
- Authentication review found password hashes use BCrypt, tokens are opaque and stored by digest, expiry/revocation/status are checked, and logout revokes the token. Existing integration tests cover invalid/expired authentication, logout, disabled users, failed-login locking, and secret non-disclosure.
- No schema or migration changes were made. Existing users, roles, and role assignments were not reset or modified.

## Changes made

### Backend

- `backend/src/main/java/com/erp/portal/administration/UserManagementService.java`: require `RBAC:USER_ROLE_UPDATE` for role-bearing user creation.
- `backend/src/main/java/com/erp/portal/administration/ImportExportService.java`: return a row-level failure for role-bearing import rows when actor lacks `RBAC:USER_ROLE_UPDATE`.
- `backend/src/test/java/com/erp/portal/security/AuthenticationRbacIntegrationTest.java`: add regressions for both escalation paths and required fixture permissions.
- `backend/src/main/java/com/erp/portal/finance/FinanceService.java` and `FinanceController.java`: pass the authenticated principal to finance read paths and enforce own/linked-student scoping for fee data, summary, reconciliation, discounts, and exports; preserve existing finance calculations.
- `backend/src/main/java/com/erp/portal/reports/ReportingService.java`: build finance payment report rows from the scoped FinanceService query.
- `backend/src/test/java/com/erp/portal/finance/FinanceTransactionIntegrationTest.java` and `backend/src/test/resources/finance-test-schema.sql`: cover cross-student finance scoping and provide the ownership fixture column.

### Frontend

- `frontend/src/layouts/AppLayout.tsx`: remove role-name bypass from the Operations navigation grant predicate.
- `frontend/src/routes/index.tsx`: remove role-name bypass from Operations direct-route authorization.
- `frontend/src/layouts/AppShell.test.tsx`: verify denied navigation and direct route for SUPER_ADMIN without a module permission; keep allowed workflow coverage for a permission-bearing user.

## Verification

| Check | Result | Evidence / limitation |
|---|---|---|
| Focused backend integration tests | PASS | `mvn "-Dtest=FinanceTransactionIntegrationTest,AuthenticationRbacIntegrationTest" test`: 26 tests, 0 failures/errors/skips |
| Full backend test suite | PASS | `mvn test`: 96 tests, 0 failures/errors/skips |
| Frontend shell/RBAC tests | PASS | `npm test -- --run src/layouts/AppShell.test.tsx`: 8 tests passed |
| Frontend production build | PASS | `npm run build`: TypeScript compilation and Vite production build succeeded. Vite reports the existing main JS chunk is slightly above 500 kB. |
| Full frontend test suite | BLOCKED by test environment | Both default and single-worker runs fail to import multiple test files because Vitest tries to open missing files under the sandbox-managed Windows Temp path (`ENOENT`). This is not an assertion failure. The affected RBAC frontend test file was run separately and passes. |
| API/Postman/browser verification | Not run | No live server/Postman execution was performed |
| Database live-grant inventory | Not run | Avoided querying or changing runtime database data |

## Remaining risks and limitations

- Teacher/class assignment scope and branch/tenant isolation were not verified; the inspected schema does not define an institution/branch key.
- Role permissions in the running database may differ from clean migration defaults.
- The full backend suite ran, but this audit did not certify every endpoint in every module. The frontend suite could not run to completion because of sandbox Temp-file `ENOENT` errors; focused route/navigation tests for Operations passed separately.
- No schema changes were made; existing data was preserved by not running migrations or destructive SQL.
