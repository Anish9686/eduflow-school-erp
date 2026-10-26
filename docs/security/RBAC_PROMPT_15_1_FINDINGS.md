# RBAC Prompt 15.1 — pre-change findings

**Audit date:** 2026-10-16  
**Scope:** Role grants, frontend visibility/routes, backend authorization and scope, dashboards, Settings.  
**Method:** Static inspection of current source, migration seed data, and the SRS extract. The live MySQL role-permission rows and user assignments were not queried; this report distinguishes code/migration facts from runtime state.

## Confirmed findings

| ID | Role / area | Expected | Current behavior and root cause | Required correction | Regression evidence |
|---|---|---|---|---|---|
| RBAC-P151-01 | Standard roles / provisioning | Existing standard roles receive the documented baseline grants | V3 explicitly says non-super-admin roles intentionally start with no business permissions; V5–V9 only add catalog grants to `SUPER_ADMIN`. A clean install leaves the other 11 system roles without grants, so expected menu/API access cannot work until separately configured. Live grants remain unknown. | Add an additive, idempotent baseline grant migration only for existing system role codes and canonical permissions, retaining all current grants. Avoid broad security-administration grants. | Migration test/inspection plus API authorization tests for permitted and denied role actions. |
| RBAC-P151-02 | Reports route/menu | Reports visible and directly routable only for an effective reports permission | `/app/reports` is unguarded and Reports has no permission in the sidebar; `/api/v1/reports/{code}` applies report-specific permissions, so UI/API disagree and the route can show a failing/empty page. | Guard route and menu with the canonical report permissions used by the report catalog. | Route tests: no grant denied; allowed report grant navigates. Backend endpoint tests retain enforcement. |
| RBAC-P151-03 | TEACHER / student scope | Assigned students only | `StudentRepository.addScope` handles only `STUDENT` and `PARENT_GUARDIAN`. A teacher with `STUDENT:READ` can list and fetch arbitrary student records. | Apply active teacher assignment scope to list and detail, and thereby to service operations that use `findStudent`; preserve student/guardian scope. | Repository/service tests for assigned, unassigned, and direct-ID reads. |
| RBAC-P151-04 | Operations UI / all roles | Only granted operational areas/actions should be shown and fetched | Route is allowed by any one of `INVENTORY:READ`, `LIBRARY:READ`, `HOSTEL:READ`, `SUPPORT:READ`; `OperationsPage` fetches all four areas and exposes creation controls without checking each manage grant. The APIs remain separately protected, causing avoidable 403s and displaying controls the actor cannot use. | Gate each tab/query/action by its exact effective permission; keep backend checks authoritative. | Frontend tests for single-module roles and read-only roles; API checks remain unchanged. |
| RBAC-P151-05 | SUPPORT_AGENT / support tickets | Assigned ticket scope | `GET /api/v1/operations/support/tickets` requires `SUPPORT:READ`, but `OperationsService.tickets()` returns the full ticket table without actor context or assignment predicate. | Pass the principal and restrict specialized support-agent reads to tickets assigned to that user; do not broaden scope for other roles. | Integration tests proving own assignment visible and another user's ticket absent. |
| RBAC-P151-06 | Dashboard / MANAGEMENT and accountant | Permission-appropriate oversight metrics | Student dashboard metric requires `STUDENT:READ`; attendance requires only `ATTENDANCE:MARK`; finance requires `FEE:COLLECT`. Thus read-only Management grants are excluded, and accountant manage/read grants may not satisfy the collection-only gate. Staff count is returned to administrator/management without a staff-read permission check. | Gate each metric on the permission needed for that metric and use the existing module read/manage grants; avoid role-only or fabricated values. | Dashboard service tests covering permission combinations and absence of unrelated metrics. |
| RBAC-P151-07 | Dashboard / specialist roles | Useful metrics only from supported, authorized data | Dashboard service has branches for SUPER_ADMIN/ADMIN/MANAGEMENT/ACCOUNTANT/RECEPTION_ADMISSION/TEACHER/STUDENT only. `STAFF_ADMIN`, `LIBRARIAN`, `INVENTORY_MANAGER`, `SUPPORT_AGENT`, and `PARENT_GUARDIAN` receive no role-specific metric branch. | Add only metrics supported by existing data/API and scope; represent no available records distinctly in the existing response/UI. | Dashboard service tests for data-backed specialist metrics and scoped parent/support metrics. |
| RBAC-P151-08 | Settings / category sensitivity | Management can view; accountant only limited access; Admin does not own platform security | API distinguishes `SETTINGS:READ` and `SETTINGS:UPDATE`, and UI hides Edit without update. However the service lists every category and permits any updater to change any key, including SECURITY settings. | Restrict SECURITY category visibility/update to the existing super-admin security authority; preserve normal read/update behavior for approved non-security settings. Keep MANAGEMENT read-only and Accountant limited via its explicit grants. | Service/controller tests for category visibility and attempted security updates. |
| RBAC-P151-09 | Administration / MANAGEMENT and ADMIN | View/manage permitted administration without granting platform security ownership | Navigation/route include RBAC read and user read/create/manage. Clean migrations assign these only to SUPER_ADMIN. `RBAC:ROLE_READ` exposes role definitions/grants; `USER:READ` exposes user directory. There are no live grants inspected. | Grant only the matrix's view/manage capabilities; keep role-permission assignment and user role assignment restricted to explicit RBAC authorities. | Role matrix/API tests, including denial of role-grant mutations. |

## Additional verified constraints and gaps

- Standard canonical role codes exist in V3, including all 12 roles in the SRS.
- Permission authorities use exact `MODULE:ACTION` strings; Spring adds `ROLE_` only to role authorities.
- Spring Security authenticates requests by default and method-level permission checks are enabled. A frontend grant does not replace backend enforcement.
- The inspected schema has no institution/branch tenant identifier. Institution/branch isolation cannot be asserted from the current model and must not be claimed as verified.
- Admission endpoints currently reuse `STUDENT:READ/CREATE/UPDATE`. This conflates applicant workflow permissions with student-record permissions; adding new admission permissions or APIs is not justified as a simple navigation repair and must be reported if no safe existing distinction can be made.
- Staff HR APIs/dashboard and parent library ownership features were not established as implemented in this pre-change pass. They must not be fabricated.
- The migration history intentionally made non-super-admin grants explicit. The latest user-provided SRS-aligned request changes the desired provisioning baseline; any correction must therefore be additive/idempotent and preserve existing assignments.

## Pre-change role mapping (migration baseline, not live database state)

| Role | Current migration grant state | SRS baseline intent |
|---|---|---|
| SUPER_ADMIN | Full catalog through V9 | Full |
| ADMIN | No seeded grants | Operational manage across approved modules; no unrestricted RBAC/security ownership |
| MANAGEMENT | No seeded grants | Read/oversight/approval and report access |
| TEACHER | No seeded grants | Assigned academic/student scope; attendance mark; library view; limited reports |
| ACCOUNTANT | No seeded grants | Student/academic/attendance view; finance manage; limited settings; no user/RBAC/audit |
| STAFF_ADMIN | No seeded grants | Staff and staff-attendance functionality only where implemented |
| LIBRARIAN | No seeded grants | Library work only |
| RECEPTION_ADMISSION | No seeded grants | Admission workflow and applicant scope only where safely representable |
| INVENTORY_MANAGER | No seeded grants | Inventory operations only |
| SUPPORT_AGENT | No seeded grants | Assigned support ticket scope only |
| PARENT_GUARDIAN | No seeded grants | Linked-child scope |
| STUDENT | No seeded grants | Own-record scope |

The following application edits begin only after this findings report was written.

## Remediation status

| Finding | Status | Evidence |
|---|---|---|
| RBAC-P151-01 | Addressed in source | Additive V10 baseline grants and separate ADMISSION permissions; existing assignments are not deleted or overwritten. Migration not applied to live MySQL in this verification. |
| RBAC-P151-02 | Addressed | Reports route and navigation now require a report data-read grant; export-only access does not expose the report page. |
| RBAC-P151-03 | Addressed | Repository scope includes active teacher assignments; H2 repository test covers assigned versus unassigned student. |
| RBAC-P151-04 | Addressed | Operations tabs, fetches and create controls use individual permissions; frontend tests cover an inventory-only reader. |
| RBAC-P151-05 | Addressed | Support agent ticket list and create assignment are actor-scoped; H2 service integration tests cover assignment and denial. |
| RBAC-P151-06 | Partially addressed | Dashboard metrics and recent audit activity are permission-gated and scoped for implemented roles. Institution/branch scope is not representable in the current schema; no tenant boundary is claimed. |
| RBAC-P151-07 | Addressed for implemented specialist data | Dashboard metrics use existing library, inventory, support, staff attendance, parent-linked, and student-owned records. Unimplemented HR/library ownership behavior is not invented. |
| RBAC-P151-08 | Addressed | Settings categories are service-scoped by actor; ADMIN cannot view SECURITY settings and accountant is limited to GENERAL. Administration integration tests cover these boundaries. |
| RBAC-P151-09 | Addressed in baseline | ADMIN receives user management, role read, and assignment of operational roles. Backend blocks ADMIN from granting or revoking SUPER_ADMIN. Role-permission mutation remains restricted to Super Admin. MANAGEMENT has read-only user/role access. Existing manual grants are preserved. |

## Verification status

Focused backend regression tests passed (including the ADMIN role-assignment boundary). Full backend suite passed after the final changes (102 tests). Full frontend suite passed (54 tests across 12 files) when run serially with the workspace temp directory; frontend production build passed. The baseline migration was reviewed in source only. No live MySQL migration, browser session, or production role rows were available to verify.
