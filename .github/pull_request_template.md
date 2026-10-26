## Pull Request Description

### Summary of Changes
<!-- Provide a clear, concise summary of the changes made and the problem being solved. -->

### Related Issue
<!-- Closes #[issue_number] or Fixes #[issue_number] -->
Fixes #

---

## Type of Change
<!-- Select all that apply by putting an 'x' in the box. -->
- [ ] **Bug fix** (non-breaking change resolving a defect)
- [ ] **New feature** (non-breaking change introducing new capability)
- [ ] **Breaking change** (fix or feature modifying existing API contract or schema)
- [ ] **Refactoring** (code structure improvement without behavioral changes)
- [ ] **Documentation update** (enhancements to README, guides, or specifications)
- [ ] **Test addition or enhancement** (automated unit, integration, or contract tests)

---

## Affected Modules
<!-- Select all affected domain modules. -->
- [ ] **Identity & RBAC** (`/auth`, `/users`, `/roles`)
- [ ] **Student Lifecycle & Admissions** (`/students`, `/admissions`, `/guardians`)
- [ ] **Academics & Timetabling** (`/academics`, `/timetable`, `/homework`, `/syllabus`)
- [ ] **Attendance** (`/attendance`, `/date-locks`)
- [ ] **Finance & Dues** (`/fees`, `/payments`, `/expenses`, `/discounts`)
- [ ] **Operations** (`/inventory`, `/library`, `/hostel`, `/support`)
- [ ] **Transport** (`/transport`)
- [ ] **Examinations** (`/exams`)
- [ ] **Administration, Reports & Audit** (`/admin`, `/reports`, `/audit`)
- [ ] **Platform / Security / Infrastructure** (`Docker`, `Flyway`, `Nginx`, `CI`)

---

## Quality & Testing Verification

Please verify that all applicable automated test gates pass cleanly:

### Automated Test Gates
- [ ] **Backend Tests:** `mvn test` executed in `backend/` (**305 tests passing, 0 failures**).
- [ ] **Frontend Tests:** `npm test` executed in `frontend/` (**101 tests passing, 0 failures**).
- [ ] **Frontend Production Build:** `npm run build` executed in `frontend/` (TypeScript compilation succeeded in 0 errors).
- [ ] **API Verification:** Postman/Newman tests verified for modified endpoints.

### Manual Verification
<!-- Describe manual testing steps performed to verify the changes. -->

---

## Architectural Checklist

- [ ] **Layer Separation:** Controllers consume and return typed DTOs/records. No JPA entities are exposed directly to API responses.
- [ ] **Security Enforcement:** All protected endpoints declare appropriate `@PreAuthorize("hasAuthority('MODULE:ACTION')")` guards.
- [ ] **Data Scoping:** Queries enforce teacher assignment, parent linkage, or tenant bounds.
- [ ] **Database Integrity:** Any schema change is implemented as a new, forward-only Flyway migration under `backend/src/main/resources/db/migration/`. No existing migrations were modified.
- [ ] **Injection Prevention:** Dynamic queries use parameterized bindings. Sort parameters are checked against an allow-list.
- [ ] **Logging & Privacy:** Passwords, tokens, and unnecessary PII are excluded from logs. Trace ID propagation is maintained.
- [ ] **UI Aesthetics:** If frontend changes were made, verified that both **Light** and **Dark** modes display with high visual contrast and responsive design works at 360px and 1280px+.
