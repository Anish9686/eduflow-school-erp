# RBAC role access matrix — Prompt 15.1

This matrix describes the additive baseline in `V10__srs_role_permission_baseline.sql` and the API/UI scope checks verified in source. It is **not a report of current live database grants**; the live MySQL database was not queried and the migration has not been applied in this environment.

| Role | Baseline access | Scope / restrictions |
|---|---|---|
| SUPER_ADMIN | Full existing permission catalog; admission workflow | Existing security-administration behavior retained. |
| ADMIN | User create/read/update/manage; role read and non-Super-Admin user-role assignment; operational read/manage grants; audit read; settings read/update; admission and student workflow | Security-category settings are Super Admin only. Admin cannot grant/revoke SUPER_ADMIN and cannot change role permission grants. |
| MANAGEMENT | User and role read; broad module read; discount/expense approval; report export; audit/settings read; admission read | No write or security administration grants are seeded. |
| TEACHER | Student read; timetable/homework/syllabus read and limited update/create; attendance read/mark; library read; report export | Student list/detail are restricted to active teacher assignments. Attendance dashboard counts assigned classes only. |
| ACCOUNTANT | Student/academic/attendance read; finance read/manage/collect/approve; finance reports; inventory/library read; settings read | Settings service restricts accountant to GENERAL category. |
| STAFF_ADMIN | Staff attendance read/mark/correct/manage | Staff records/attendance functionality remains limited to implemented APIs. |
| LIBRARIAN | Library read/manage/issue/return | Catalog and issue records use implemented library APIs. |
| RECEPTION_ADMISSION | Admission read/create/update | Student queries are restricted to records linked to an admission; no general student grants. |
| INVENTORY_MANAGER | Inventory read/manage | Inventory APIs remain authoritative. |
| SUPPORT_AGENT | Support read/manage | Ticket list is restricted to tickets assigned to the actor; ticket creation is self-assigned. |
| PARENT_GUARDIAN | Student, timetable, homework, syllabus, attendance, fee read; limited report export | Student records and dashboard metrics are linked-child scoped. |
| STUDENT | Own student, timetable, homework, syllabus, attendance, fee read; limited report export | Student records and dashboard metrics are own-record scoped. |

## Authorization conventions

- Permission codes use `MODULE:ACTION`; the backend is authoritative and denies protected requests without the required authority.
- `/app/reports` and its navigation require a report data-read grant; export-only authority does not expose report data pages.
- Each Operations tab, query and create action is gated by its own module grant in the UI; each API retains server-side authorization.
- Admission workflow permissions are distinct from general student permissions.
- SECURITY-category settings are visible/updateable only by `SUPER_ADMIN`. Accountant settings are limited to `GENERAL`.
- ADMIN may assign operational roles to users, matching the SRS “Users/Roles — Manage” matrix. The backend blocks ADMIN from granting or removing `SUPER_ADMIN`. Role-permission changes, which can grant any authority, remain restricted to the existing Super Admin security permission. MANAGEMENT has read-only access.
- The schema contains no institution/branch tenant key. Institution-versus-branch isolation cannot be implemented or asserted without changing data model/business rules.

## Verification limits

H2 integration tests verify representative service/API decisions and record scoping. They do not prove that MySQL has applied V10. Migration syntax and grant codes were source-reviewed only; production/live grants, branch isolation, and routes outside implemented APIs require deployment/schema/runtime verification.
