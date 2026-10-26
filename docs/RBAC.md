# EduFlow ERP Portal — Role-Based Access Control (RBAC) Specification

**Document Version:** 1.0.0-RELEASE  
**Evaluation Baseline:** Flyway Migrations `V3`, `V8`, `V10`, `V11`, `V12`, `V13`, `V14`  
**Security Model:** Role $\rightarrow$ Permissions $\rightarrow$ Data Scope $\rightarrow$ Method Authorization  

---

## 1. Overview & Authorization Model

EduFlow enforces a strict, hierarchical **Role-Based Access Control (RBAC)** architecture combined with server-side **Data Scoping**. Security evaluation follows an authoritative chain:

```
[User Request]
       │
       ▼
[Bearer Token Authentication] ──(Invalid/Expired)──► [401 Unauthorized]
       │
       ▼
[Role & Permission Evaluation (@PreAuthorize)] ──(Lacks Permission)──► [403 Forbidden]
       │
       ▼
[Data Scope Evaluation (Repository Query)] ──(Out-of-Scope)──► [403 Forbidden / Scoped Empty]
       │
       ▼
[Transactional Service Execution]
```

### Core Security Rules:
1. **Deny by Default:** If an endpoint does not explicitly grant access to an authority, access is strictly denied.
2. **Authoritative Backend Boundary:** Frontend hiding of buttons, menus, and routes is a usability feature. The Spring Security filter chain and `@PreAuthorize` method annotations are the sole authoritative security boundary.
3. **Data Scope Restriction:** Having permission to view a resource (e.g., `STUDENT:READ`) does not grant institution-wide visibility. The user's role and database assignments dictate which records can be accessed.
4. **Separation of Duties (SoD):** High-risk financial operations enforce segregation: cashiers cannot approve fee discounts, and requesters cannot self-approve their own discounts or expenses.

---

## 2. Standard System Roles

The platform provides **12 pre-configured system roles**:

| Role Code | Role Name | System Scope & Responsibilities |
| :--- | :--- | :--- |
| `SUPER_ADMIN` | Super Administrator | Institution-wide security, user management, RBAC configuration, audit logs, and system settings. |
| `ADMIN` | School Administrator | Operational school administration, class schedules, admissions, and academic structure. |
| `MANAGEMENT` | Executive Management | Executive oversight, cross-department KPI dashboards, fee discount approvals, and expense approvals. |
| `TEACHER` | Academic Teacher | Assigned class sections, attendance marking, homework assignments, and exam mark entry. |
| `ACCOUNTANT` | Financial Accountant | Student fee demand generation, payment collection, receipt issuance, expense tracking, and ledger reports. |
| `HR` | Staff Administrator | Staff master directory, teacher assignments, and staff attendance. |
| `LIBRARIAN` | Librarian | Book cataloging, circulation loans, return processing, and fine tracking. |
| `RECEPTION` | Admission Receptionist | Applicant enquiries, admission document collection, and student registrations. |
| `INVENTORY_MANAGER` | Inventory Manager | Inventory item master, supplier management, and stock issue/receipt movements. |
| `SUPPORT_AGENT` | Support Agent | Triage, assignment, and resolution of operational support tickets. |
| `PARENT` | Parent / Guardian | Scoped access strictly to linked children's academic, attendance, and fee status. |
| `STUDENT` | Enrolled Student | Scoped access strictly to personal attendance, homework, exam marks, and bus pass. |

---

## 3. Permission Naming Convention

Permissions adhere to a standardized `MODULE:ACTION` format:
- **`MODULE`:** Domain entity or functional area (`USER`, `ROLE`, `STUDENT`, `ADMISSION`, `ACADEMIC`, `ATTENDANCE`, `FEE`, `FEE_DISCOUNT`, `EXPENSE`, `INVENTORY`, `LIBRARY`, `HOSTEL`, `TRANSPORT`, `EXAM`, `SUPPORT`, `REPORT`, `SETTING`, `AUDIT`).
- **`ACTION`:** Granular operation (`READ`, `CREATE`, `UPDATE`, `DELETE`, `MANAGE`, `MARK`, `CORRECT`, `COLLECT`, `REFUND`, `APPROVE`, `EXPORT`, `IMPORT`).

---

## 4. Comprehensive Role-to-Permission Mapping Matrix

*Verified directly against the baseline Flyway migration sequence (`V3`, `V8`, `V10`, `V11`, `V12`, `V13`, `V14`):*

| Permission Code | Super Admin | Admin | Management | Teacher | Accountant | Parent | Student |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| **Identity & Users** | | | | | | | |
| `USER:READ` | ✓ | ✓ | ✓ | — | — | — | — |
| `USER:CREATE` | ✓ | ✓ | — | — | — | — | — |
| `USER:UPDATE` | ✓ | ✓ | — | — | — | — | — |
| `USER:MANAGE` | ✓ | ✓ | — | — | — | — | — |
| `USER:IMPORT` | ✓ | ✓ | — | — | — | — | — |
| `USER:EXPORT` | ✓ | ✓ | — | — | — | — | — |
| `ROLE:READ` | ✓ | ✓ | ✓ | — | — | — | — |
| `ROLE:MANAGE` | ✓ | — | — | — | — | — | — |
| **Students & Admissions** | | | | | | | |
| `STUDENT:READ` | ✓ | ✓ | ✓ | ✓ *(Assigned)* | ✓ | ✓ *(Linked)* | ✓ *(Own)* |
| `STUDENT:CREATE` | ✓ | ✓ | — | — | — | — | — |
| `STUDENT:UPDATE` | ✓ | ✓ | — | — | — | — | — |
| `STUDENT:MANAGE` | ✓ | ✓ | — | — | — | — | — |
| `ADMISSION:READ` | ✓ | ✓ | ✓ | — | — | — | — |
| `ADMISSION:CREATE` | ✓ | ✓ | — | — | — | — | — |
| `ADMISSION:MANAGE` | ✓ | ✓ | — | — | — | — | — |
| **Academics & Timetable** | | | | | | | |
| `ACADEMIC:READ` | ✓ | ✓ | ✓ | ✓ *(Assigned)* | — | ✓ *(Linked)* | ✓ *(Own)* |
| `ACADEMIC:MANAGE` | ✓ | ✓ | — | — | — | — | — |
| `HOMEWORK:READ` | ✓ | ✓ | ✓ | ✓ *(Assigned)* | — | ✓ *(Linked)* | ✓ *(Own)* |
| `HOMEWORK:CREATE` | ✓ | ✓ | — | ✓ *(Assigned)* | — | — | — |
| **Attendance** | | | | | | | |
| `ATTENDANCE:READ` | ✓ | ✓ | ✓ | ✓ *(Assigned)* | — | ✓ *(Linked)* | ✓ *(Own)* |
| `ATTENDANCE:MARK` | ✓ | ✓ | — | ✓ *(Assigned)* | — | — | — |
| `ATTENDANCE:CORRECT` | ✓ | ✓ | — | — | — | — | — |
| `ATTENDANCE:LOCK` | ✓ | ✓ | — | — | — | — | — |
| **Finance & Billing** | | | | | | | |
| `FEE:READ` | ✓ | ✓ | ✓ | — | ✓ | ✓ *(Linked)* | ✓ *(Own)* |
| `FEE:MANAGE` | ✓ | ✓ | — | — | ✓ | — | — |
| `FEE:COLLECT` | ✓ | ✓ | — | — | ✓ | — | — |
| `FEE:REFUND` | ✓ | ✓ | — | — | — *(Denied)* | — | — |
| `FEE_DISCOUNT:REQUEST` | ✓ | ✓ | — | — | ✓ | — | — |
| `FEE_DISCOUNT:APPROVE` | ✓ | ✓ | ✓ | — | — *(No SoD)* | — | — |
| `EXPENSE:READ` | ✓ | ✓ | ✓ | — | ✓ | — | — |
| `EXPENSE:MANAGE` | ✓ | ✓ | — | — | ✓ | — | — |
| `EXPENSE:APPROVE` | ✓ | ✓ | ✓ | — | — *(No SoD)* | — | — |
| `FINANCE_REPORT:EXPORT` | ✓ | ✓ | ✓ | — | ✓ | — | — |
| **Operations & Fleet** | | | | | | | |
| `INVENTORY:READ` | ✓ | ✓ | ✓ | — | ✓ | — | — |
| `INVENTORY:MANAGE` | ✓ | ✓ | — | — | — | — | — |
| `LIBRARY:READ` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ *(Own)* |
| `LIBRARY:MANAGE` | ✓ | ✓ | — | — | — | — | — |
| `HOSTEL:READ` | ✓ | ✓ | ✓ | — | — | ✓ *(Linked)* | ✓ *(Own)* |
| `HOSTEL:MANAGE` | ✓ | ✓ | — | — | — | — | — |
| `TRANSPORT:READ` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ *(Linked)* | ✓ *(Own)* |
| `TRANSPORT:MANAGE` | ✓ | ✓ | — | — | — | — | — |
| `SUPPORT:READ` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| `SUPPORT:MANAGE` | ✓ | ✓ | — | — | — | — | — |
| **Examinations & Marks** | | | | | | | |
| `EXAM:READ` | ✓ | ✓ | ✓ | ✓ *(Assigned)* | — | ✓ *(Linked)* | ✓ *(Own)* |
| `EXAM:MANAGE` | ✓ | ✓ | — | — | — | — | — |
| `EXAM:MARK_ENTRY` | ✓ | ✓ | — | ✓ *(Assigned)* | — | — | — |
| **Admin, Reports & Audit** | | | | | | | |
| `REPORT:READ` | ✓ | ✓ | ✓ | ✓ *(Limited)*| ✓ *(Finance)*| — | — |
| `REPORT:EXPORT` | ✓ | ✓ | ✓ | — | ✓ *(Finance)*| — | — |
| `SETTING:READ` | ✓ | ✓ | ✓ | — | — | — | — |
| `SETTING:MANAGE` | ✓ | ✓ | — | — | — | — | — |
| `AUDIT:READ` | ✓ | ✓ | ✓ | — | — | — | — |

---

## 5. Data Scoping Invariants

In addition to coarse-grained permissions, the application enforces row-level data scoping:

### 5.1 Teacher Scope
Teachers hold permissions like `STUDENT:READ`, `ATTENDANCE:MARK`, and `EXAM:MARK_ENTRY`, but can only exercise them against class sections assigned to them in `teacher_assignments`.
- **Query Mechanism:** Repository queries include:
  ```sql
  WHERE s.section_id IN (
      SELECT ta.section_id FROM teacher_assignments ta WHERE ta.teacher_id = :teacherStaffId
  )
  ```
- **Rejection Behavior:** Submitting an attendance batch or exam mark for a section outside the teacher's assignment returns `HTTP 403 Forbidden` or `HTTP 400 Bad Request`.

### 5.2 Parent & Student Scope
Parents and students have read-only access to their respective records:
- **Parent Scope:** Queries are scoped via `student_guardians.guardian_id` matching the authenticated user's linked guardian record.
- **Student Scope:** Queries are scoped strictly to the student's own `student_id`.

### 5.3 Separation of Duties (SoD)
To prevent internal fraud:
- **Discount Approval:** Cashiers can submit discount requests (`FEE_DISCOUNT:REQUEST`), but cannot approve them. Approvals (`FEE_DISCOUNT:APPROVE`) require `SUPER_ADMIN`, `ADMIN`, or `MANAGEMENT`. Even if a user holds both roles, the service blocks approval if `requester_id == approver_id`.
- **Fee Refunds:** Cashiers cannot process refunds or reversals. Only administrators with `FEE:REFUND` authority can issue refunds, which creates an immutable record in `finance_adjustments`.

---

## 6. Token Invalidation Strategy

When administrative changes occur:
1. **User Deactivation:** Setting `status = 'DISABLED'` in `users` causes `BearerAuthenticationFilter` to reject subsequent requests immediately with HTTP 401.
2. **Role or Permission Revocation:** Role and permission updates take effect upon the user's next login or session refresh (within 30 minutes under the standard token TTL).
3. **Session Invalidation:** Explicit logout or password resets revoke the session in `auth_sessions` by setting `revoked_at = NOW()`.
