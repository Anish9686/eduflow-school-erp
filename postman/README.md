# EduFlow ERP Portal — Postman Verification Suite

**Collection File:** [`ERP_Portal_API_v1.postman_collection.json`](ERP_Portal_API_v1.postman_collection.json)  
**Environment File:** [`ERP_Portal_Local.postman_environment.json`](ERP_Portal_Local.postman_environment.json)  
**Detailed Guide:** [`POSTMAN_VERIFICATION_GUIDE.md`](POSTMAN_VERIFICATION_GUIDE.md)  
**Test Coverage:** 126 Automated Requests | 201 Assertions | 15 Folders  
**Execution Pass Rate:** 100.0% (201/201 passed via Newman CLI v6.2.3)  

---

## 1. Overview

The EduFlow Postman verification suite provides end-to-end regression, contract, security, and functional validation for all REST endpoints across the ERP portal.

Every request contains automated test scripts verifying:
- **HTTP Status Codes:** `200 OK`, `201 Created`, `204 No Content`, `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `409 Conflict`.
- **Response Schemas:** Validates structured JSON response envelopes, pagination metadata (`page`, `size`, `totalElements`, `totalPages`), and field arrays.
- **Error Envelopes:** Enforces consistent error schema (`timestamp`, `status`, `code`, `message`, `path`, `fieldErrors`, `traceId`).
- **Dynamic Variable Chaining:** Requests capture generated resource IDs (student IDs, admission IDs, fee demand IDs, payment IDs, inventory SKUs, hostel rooms) and propagate them automatically across subsequent requests.
- **Negative Security Scenarios:** Validates that unauthenticated, expired, forged, or out-of-scope requests are rejected cleanly without data leakage.

---

## 2. Collection Structure & Folders

The collection is organized into 15 domain folders executed sequentially:

| Folder # | Folder Name | Requests | Key Verifications |
| :---: | :--- | :---: | :--- |
| **01** | **Authentication** | 11 | Multi-role login (Super Admin, Admin, Teacher, Student, Parent, Support Agent), token capture, generic 401 on bad credentials, `/auth/me` context, session refresh, and logout revocation. |
| **02** | **Authorization** | 6 | Positive Super Admin access; negative role-block checks where Teacher, Student, Parent, and Support Agent are rejected with HTTP 403; anonymous blocked with 401. |
| **03** | **Users** | 12 | User directory pagination, role filter, name search, user detail, 404 on missing ID, user creation (201), duplicate username/email rejection (409), validation errors (400), status toggle, and role assignment. |
| **04** | **Students** | 13 | Student list, status filter, profile detail, create student (201), duplicate admission number rejection (409), validation errors, guardian linking, student transfer, and audit history. |
| **05** | **Admission** | 8 | Application listing, status filter, detail by ID, application submission, validation failure, valid state transition (`UNDER_REVIEW`), and illegal transition rejection (409). |
| **06** | **Academics** | 15 | Sessions, classes, sections, subjects, teacher assignments, timetable view, timetable overlap conflict rejection (409), homework viewer, and syllabus tracker. |
| **07** | **Attendance** | 9 | Daily section roster, student history, missing params validation (400), batch attendance marking, duplicate student in batch rejection (400), daily summary, and date locking. |
| **08** | **Finance** | 10 | Fee categories, structures, demand dues, atomic payment posting (200), duplicate payment reference rejection (409), receipt viewing, discount request queue, and expense listing. |
| **09** | **Inventory** | 7 | Categories, suppliers, item catalog, stock in/out movements, and negative stock rejection (409). |
| **10** | **Library** | 6 | Catalog search, add book, active loans, issue book to student, return book (204), and return non-existent loan (404). |
| **11** | **Hostel** | 4 | Room listing, room creation, bed allocation, and double bed allocation conflict rejection (409). |
| **12** | **Support** | 3 | Ticket queue listing, ticket creation, and cross-assignment routing restrictions (403). |
| **13** | **Reports** | 5 | Report catalog, student directory report execution, 404 on missing report code, CSV export streaming, and unauthorized financial export rejection (403). |
| **14** | **Audit** | 4 | Audit trail pagination, filter by entity type, filter by action, and unprivileged audit access denial (403). |
| **15** | **Negative Security** | 10 | Missing auth header (401), forged token (401), privilege escalation (403), non-existent entity (404), malformed JSON (400), empty body (400), SQL injection sanitization, path traversal blocked, out-of-scope parent access denied, and unauthorized export guard. |

---

## 3. Running with Newman CLI

### Prerequisites
Install Newman globally or execute via `npx`:
```bash
npm install -g newman
```

### Execution Command
With the backend running on `http://localhost:8080`:

```bash
# Execute from the project root:
npx newman run "postman/ERP_Portal_API_v1.postman_collection.json" \
  -e "postman/ERP_Portal_Local.postman_environment.json" \
  --reporters cli
```

### Useful CLI Options
- **HTML Reporter:**
  ```bash
  npx newman run "postman/ERP_Portal_API_v1.postman_collection.json" \
    -e "postman/ERP_Portal_Local.postman_environment.json" \
    --reporters cli,htmlextra --reporter-htmlextra-export postman/newman-report.html
  ```
- **Bail on First Failure:**
  ```bash
  npx newman run "postman/ERP_Portal_API_v1.postman_collection.json" \
    -e "postman/ERP_Portal_Local.postman_environment.json" --bail
  ```
- **Run a Specific Folder:**
  ```bash
  npx newman run "postman/ERP_Portal_API_v1.postman_collection.json" \
    -e "postman/ERP_Portal_Local.postman_environment.json" \
    --folder "08 Finance"
  ```

---

## 4. Running Interactively in Postman App

1. Launch Postman.
2. Click **Import** in the top navigation bar.
3. Select both files:
   - `postman/ERP_Portal_API_v1.postman_collection.json`
   - `postman/ERP_Portal_Local.postman_environment.json`
4. In the environment selector dropdown (top right), select **"ERP Portal - Local Environment"**.
5. Select the **"ERP Portal API - v1"** collection.
6. Click **Run Collection**.
7. Ensure all 15 folders are checked, keep "Run collection in order" selected, and click **Run ERP Portal API - v1**.

---

## 5. Security & Verification Boundaries

Postman is an essential tool for black-box API contract and workflow verification. However, maintainers should note:
- **What Postman Proves:** HTTP response status, authorization enforcement, payload serialization, error schema conformity, pagination, query parameters, and observable API state changes.
- **What Requires Code & DB Inspection:** Internal Java class architecture, database indexes, Hibernate validation modes, atomic transaction rollback under DB hardware failure, and query execution plans. These are covered by our JUnit 5 / Spring Boot Test suite (`backend/src/test/java`).
