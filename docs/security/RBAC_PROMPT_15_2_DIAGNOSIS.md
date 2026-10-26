# Prompt 15.2 — RBAC root-cause diagnosis

## Findings from source and automated tests

The frontend does not compare role labels to `ADMIN` or `MANAGEMENT` when filtering the sidebar. `AuthProvider` consumes the `roles` and exact `MODULE:ACTION` permission codes returned by `/api/v1/me`; `AppLayout` shows a module only when one of that module's permissions is present. The backend reloads role and permission joins from the database for each authenticated request. Therefore role-name casing and stale authorities inside a bearer token are not the defect in this implementation.

Migration V3 seeds the standard role records but explicitly gives business grants only to `SUPER_ADMIN`. The additive V10 migration in this workspace now gives `ADMIN` and `MANAGEMENT` their SRS baseline grants. If the database used by the running application has not applied V10, `/me` will correctly return only the currently assigned grants; the sidebar will then hide all modules without a grant. Refreshing or logging in again cannot supply grants absent from the database. Apply the migration through the normal Flyway-enabled application startup after configuring the real database connection.

The shared `Staff` card is a separately permission-gated dashboard metric: `DashboardService` includes it for ADMIN, MANAGEMENT, or STAFF_ADMIN when the principal has `STAFF_ATTENDANCE:READ`, then counts active staff rows. A value of `0` is the actual query result, not a fallback for failed API calls. ADMIN and MANAGEMENT each receive that permission in V10. A principal with that grant but without the other dashboard grants will consequently see just this card.

The related fixes already in source include the V10 additive grant baseline, Settings category restrictions, permission-based sidebar/routes, Operations tab/query checks, and role-specific permission-gated dashboard metrics. Regression coverage verifies exact role/permission loading from DB assignments through `/me` and that grant changes are reflected on the next authenticated request using the existing session.

## Account verification limitation

The requested `Ankit9661` and `Satyam Kumar Singh` records were **not queried**. There is no `DB_URL`/`DB_USERNAME` configuration in this workspace, the backend API at `localhost:8080` is unavailable, and the Windows service manager is inaccessible in this execution environment. No user passwords, hashes, tokens, or production records were accessed. The read-only SQL in [RBAC_PROMPT_15_2_DIAGNOSTICS.sql](RBAC_PROMPT_15_2_DIAGNOSTICS.sql) checks account status, role codes, effective grants, duplicate roles, and Flyway V10 history without selecting secrets.

The live deployment's applied migration version, actual account assignments, data scope, and browser rendering remain **NOT VERIFIED**. Do not describe the migration as applied until the V10 row appears in `flyway_schema_history` for that database.
