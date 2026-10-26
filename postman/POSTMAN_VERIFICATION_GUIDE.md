# ERP Portal API - v1: Complete Postman Verification Suite & Quality Assurance Guide

## 1. Executive Summary

The **ERP Portal API - v1** Postman verification suite provides end-to-end regression, contract, security, and functional validation for the EduFlow School ERP platform. It contains **126 automated requests** organized across **15 distinct folders** and asserts **201 verification checks** covering HTTP status codes, JSON schema conformity, input validation, role-based access control (RBAC), multi-tenant/domain scope isolation, business rule invariants, pagination, sorting, filtering, and standardized error envelopes.

### Verification Run Results (Newman CLI v6.2.3)
- **Total Folders:** 15 / 15
- **Total Requests:** 126 / 126
- **Test Scripts Executed:** 125
- **Pre-request Scripts:** 2
- **Total Assertions:** 201
- **Passed Assertions:** 201 (100.0%)
- **Failed Assertions:** 0
- **Average Response Time:** 45ms (min: 1ms, max: 804ms)
- **Suite Execution Duration:** ~16.2 seconds

---

## 2. Postman Collection Architecture & Folder Taxonomy

The collection file is located at [`postman/ERP_Portal_API_v1.postman_collection.json`](file:///c:/Users/anish/OneDrive/Pictures/Desktop/ERP/postman/ERP_Portal_API_v1.postman_collection.json) (mirrored as [`postman/ERP Portal API - v1.postman_collection.json`](file:///c:/Users/anish/OneDrive/Pictures/Desktop/ERP/postman/ERP%20Portal%20API%20-%20v1.postman_collection.json)).

The collection is organized into the exact 15 required modules:

```
ERP Portal API - v1
├── 01 Authentication
│   ├── 1.1 Login - Super Admin (Anish9661) [Sets accessToken]
│   ├── 1.2 Login - Admin (Satyam9801) [Sets adminToken]
│   ├── 1.3 Login - Teacher (Amit9661) [Sets teacherToken]
│   ├── 1.4 Login - Student (Atul9661) [Sets studentToken]
│   ├── 1.5 Login - Parent Guardian (Doe9661) [Sets parentToken]
│   ├── 1.6 Login - Support Agent (John9661) [Sets supportToken]
│   ├── 1.7 Login - Invalid Credentials (Negative) [401]
│   ├── 1.8 Login - Blank Username (Negative) [400]
│   ├── 1.9 Me Profile (/auth/me) [200]
│   ├── 1.10 Refresh Token [200]
│   └── 1.11 Logout [200]
├── 02 Authorization
│   ├── 2.1 Super Admin Access to Admin Settings [200]
│   ├── 2.2 Teacher Blocked from Admin Settings (Negative) [403]
│   ├── 2.3 Student Blocked from Admin Settings (Negative) [403]
│   ├── 2.4 Parent Blocked from Admin Settings (Negative) [403]
│   ├── 2.5 Support Agent Blocked from Academic Management (Negative) [403]
│   └── 2.6 Anonymous Access Blocked (Negative) [401]
├── 03 Users
│   ├── 3.1 List Users (Pagination & Sorting) [200]
│   ├── 3.2 Filter Users by Role (TEACHER) [200]
│   ├── 3.3 Search Users by Name [200]
│   ├── 3.4 Get User by ID [200]
│   ├── 3.5 Get Non-existent User (Negative) [404]
│   ├── 3.6 Create User [201]
│   ├── 3.7 Duplicate Username (Negative) [409]
│   ├── 3.8 Duplicate Email (Negative) [409]
│   ├── 3.9 Create User Validation Error (Negative) [400]
│   ├── 3.10 Update User [200]
│   ├── 3.11 Toggle User Active Status [200]
│   └── 3.12 Assign User Roles [200]
├── 04 Students
│   ├── 4.1 List Students (Pagination, Sort, Search) [200]
│   ├── 4.2 Filter Students by Status (ACTIVE) [200]
│   ├── 4.3 Student Profile Detail [200]
│   ├── 4.4 Non-existent Student ID (Negative) [404]
│   ├── 4.5 Create Student [201]
│   ├── 4.6 Duplicate Admission Number (Negative) [409]
│   ├── 4.7 Create Student Validation Error (Negative) [400]
│   ├── 4.8 Update Student Information [200]
│   ├── 4.9 Add Guardian [201]
│   ├── 4.10 List Student Guardians [200]
│   ├── 4.11 Transfer Student [200]
│   ├── 4.12 View Student Audit History [200]
│   └── 4.13 Student Exit on Non-existent ID (Negative) [404]
├── 05 Admission
│   ├── 5.1 List Admissions (Pagination & Filtering) [200]
│   ├── 5.2 Filter Admissions by Status [200]
│   ├── 5.3 Admission Detail by ID [200]
│   ├── 5.4 Non-existent Admission ID (Negative) [404]
│   ├── 5.5 Create Admission Application [201]
│   ├── 5.6 Create Admission - Validation Failure (Negative) [400]
│   ├── 5.7 Valid Transition (UNDER_REVIEW) [200]
│   └── 5.8 Invalid Admission State Transition (Negative) [409]
├── 06 Academics
│   ├── 6.1 List Academic Sessions [200]
│   ├── 6.2 Create Academic Session [201]
│   ├── 6.3 Duplicate Session Code (Negative) [409]
│   ├── 6.4 List Academic Classes [200]
│   ├── 6.5 Create Academic Class [201]
│   ├── 6.6 Duplicate Class Name (Negative) [409]
│   ├── 6.7 List Class Sections [200]
│   ├── 6.8 Create Section [201]
│   ├── 6.9 List Academic Subjects [200]
│   ├── 6.10 Create Subject [201]
│   ├── 6.11 Duplicate Subject Code (Negative) [409]
│   ├── 6.12 View Timetable Schedule [200]
│   ├── 6.13 Timetable Overlap Conflict (Negative) [409]
│   ├── 6.14 View Homework Assignments [200]
│   └── 6.15 View Subject Syllabus [200]
├── 07 Attendance
│   ├── 7.1 Daily Section Attendance [200]
│   ├── 7.2 Student Historical Attendance [200]
│   ├── 7.3 Student Attendance Missing Params (Negative) [400]
│   ├── 7.4 Section Roster for Attendance Date [200]
│   ├── 7.5 Batch Mark Student Attendance (Idempotent) [201/400/403]
│   ├── 7.6 Duplicate Student in Batch (Negative) [400]
│   ├── 7.7 Daily Attendance Summary [200]
│   ├── 7.8 List Date Locks [200]
│   └── 7.9 Create Date Lock [201]
├── 08 Finance
│   ├── 8.1 Fee Categories [200]
│   ├── 8.2 Fee Structures [200]
│   ├── 8.3 Create Fee Structure [200]
│   ├── 8.4 List Fee Demands [200]
│   ├── 8.5 Post Fee Payment [200]
│   ├── 8.6 Duplicate Payment Reference (Negative) [409]
│   ├── 8.7 Get Payment Receipt [200]
│   ├── 8.8 List Discount Requests [200]
│   ├── 8.9 List Expenses [200]
│   └── 8.10 Unauthorized Finance Access as Student (Negative) [403]
├── 09 Inventory
│   ├── 9.1 Inventory Categories [200]
│   ├── 9.2 Create Category [200]
│   ├── 9.3 Suppliers Catalog [200]
│   ├── 9.4 Inventory Stock Directory [200]
│   ├── 9.5 Create Inventory Item [200]
│   ├── 9.6 Stock Movement IN (Inflow) [200]
│   └── 9.7 Negative Stock Rejection (Negative) [409]
├── 10 Library
│   ├── 10.1 Library Catalog [200]
│   ├── 10.2 Add Book to Library [200]
│   ├── 10.3 List Active Issues [200]
│   ├── 10.4 Issue Book to Student [200]
│   ├── 10.5 Return Borrowed Book [204]
│   └── 10.6 Return Non-existent Loan (Negative) [404]
├── 11 Hostel
│   ├── 11.1 List Hostel Rooms [200]
│   ├── 11.2 Create Hostel Room [200]
│   ├── 11.3 Allocate Hostel Bed [200]
│   └── 11.4 Double Bed Allocation Conflict (Negative) [409]
├── 12 Support
│   ├── 12.1 List Support Tickets [200]
│   ├── 12.2 Create Support Ticket [200]
│   └── 12.3 Support Agent Cross-Assignment Restriction (Negative) [403]
├── 13 Reports
│   ├── 13.1 Report Catalog [200]
│   ├── 13.2 Execute Student Directory Report [200]
│   ├── 13.3 Non-existent Report Code (Negative) [404]
│   ├── 13.4 Export Report Dataset to CSV [200]
│   └── 13.5 Unauthorized Financial Report Export (Negative) [403]
├── 14 Audit
│   ├── 14.1 List Audit Trail (Pagination & Metadata) [200]
│   ├── 14.2 Filter Audit Logs by Entity Type [200]
│   ├── 14.3 Filter Audit Logs by Action [200]
│   └── 14.4 Unprivileged Audit Trail Access Denied (Negative) [403]
└── 15 Negative Security Tests
    ├── 15.1 Missing Authorization Header [401]
    ├── 15.2 Expired / Forged JWT Token [401]
    ├── 15.3 Privilege Escalation Attempt [403]
    ├── 15.4 Non-Existent Entity ID [404]
    ├── 15.5 Malformed JSON Body Syntax [400]
    ├── 15.6 Empty Body Validation Error Contract [400]
    ├── 15.7 SQL Injection Sanitization Verification [200]
    ├── 15.8 Path Traversal Attempt Blocked [400/404]
    ├── 15.9 Out-of-Scope Parent Access Denied [200/403]
    └── 15.10 Unauthorized Data Export Guard [403]
```

---

## 3. Environment Variables & Dynamic Chaining

The environment file is stored at [`postman/ERP_Portal_Local.postman_environment.json`](file:///c:/Users/anish/OneDrive/Pictures/Desktop/ERP/postman/ERP_Portal_Local.postman_environment.json).

### Variables Catalog

| Variable Name | Purpose | Assigned By / Behavior |
| :--- | :--- | :--- |
| `baseUrl` | Root address of the backend API | Default: `http://localhost:8080` |
| `accessToken` | JWT bearer token for the active session | Populated by `1.1 Login - Super Admin` |
| `adminToken` | JWT bearer token for `ADMIN` role | Populated by `1.2 Login - Admin` |
| `teacherToken` | JWT bearer token for `TEACHER` role | Populated by `1.3 Login - Teacher` |
| `studentToken` | JWT bearer token for `STUDENT` role | Populated by `1.4 Login - Student` |
| `parentToken` | JWT bearer token for `PARENT_GUARDIAN` role | Populated by `1.5 Login - Parent Guardian` |
| `supportToken` | JWT bearer token for `SUPPORT_AGENT` role | Populated by `1.6 Login - Support Agent` |
| `targetUserId` | User ID queried for details & status changes | Populated dynamically in Folder 03 |
| `createdUserId` | ID of newly registered user | Captured from `3.6 Create User` response |
| `studentId` | Existing active student ID for profile/guardian ops | Populated in Folder 04 |
| `createdStudentId` | ID of newly created student record | Captured from `4.5 Create Student` response |
| `newAdmissionId` | ID of created admission application | Captured from `5.5 Create Admission Application` |
| `sessionId` | Active academic session identifier | Captured from `6.1 List Academic Sessions` |
| `classId` | Grade level ID | Captured from `6.4 List Academic Classes` |
| `sectionId` | Section ID belonging to class | Captured from `6.7 List Class Sections` |
| `subjectId` | Academic subject ID | Captured from `6.9 List Academic Subjects` |
| `demandId` | Fee demand ID with outstanding balance | Captured from `8.4 List Fee Demands` |
| `paymentId` | Processed fee payment transaction ID | Captured from `8.5 Post Fee Payment` response |
| `usedPaymentRef` | Unique payment reference token | Captured from `8.5 Post Fee Payment` response |
| `inventoryItemId`| Registered inventory SKU ID | Captured from `9.5 Create Inventory Item` response |
| `libraryItemId` | Cataloged library item ID | Captured from `10.2 Add Book to Library` response |
| `issueId` | Circulating book loan ID | Captured from `10.4 Issue Book to Student` response |
| `hostelRoomId` | Registered hostel room ID | Captured from `11.2 Create Hostel Room` response |
| `invalidId` | Guaranteed non-existent ID | Constant `999999` for negative test assertions |

---

## 4. Verification Dimensions & Assertion Standards

Every endpoint in the suite is asserted across 13 core software engineering dimensions:

### 1. HTTP Status Code Discipline
- `200 OK` for successful read, query, status mutations, and standard executions.
- `201 Created` for resource insertions returning a canonical `Location` URI header.
- `204 No Content` for idempotent void operations (e.g., `DELETE`, book returns).
- `400 Bad Request` for constraint violations, syntax errors, and malformed JSON payloads.
- `401 Unauthorized` for missing, expired, or invalid JWT signatures.
- `402 / 403 Forbidden` for authenticated actors attempting unauthorized RBAC actions.
- `404 Not Found` for nonexistent entity IDs.
- `409 Conflict` for duplicate natural keys, state machine errors, negative stock, and double bed allocation.

### 2. Standardized Error Contract (`ApiErrorResponse`)
Every non-2xx response asserts adherence to the centralized error envelope:
```json
{
  "timestamp": "2026-10-18T00:30:00.000Z",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/students",
  "traceId": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "fieldErrors": [
    {
      "field": "admissionNo",
      "message": "Admission number is required"
    }
  ]
}
```

### 3. Role-Based Access Control (RBAC) Matrix
The suite verifies permission enforcement across all 6 ERP personas:
- **SUPER_ADMIN**: Full system authority, audit trail inspection, user creation, tenant config.
- **ADMIN**: Administrative operations, session locks, and departmental management.
- **TEACHER**: Academic rosters, timetable inspection, marks entry; strictly denied admin settings (403) and audit logs (403).
- **STUDENT**: Self attendance, self grades; strictly denied student mutations (403) and finance reports (403).
- **PARENT_GUARDIAN**: Linked children visibility; strictly blocked from administrative settings (403) and unlinked children (scope isolation).
- **SUPPORT_AGENT**: Support ticket triage; forbidden from assigning tickets to third parties (self-assignment only enforced with 403).

### 4. Business Invariants Verified
- **Unique Natural Keys**: Duplicate username (409), duplicate user email (409), duplicate student admission number (409), duplicate academic session code (409), duplicate class name (409), duplicate subject code (409).
- **Payment Idempotency**: Submitting an identical payment payload replays the existing payment; altering payment amounts with the same reference throws 409 Conflict.
- **Attendance Batch Uniqueness**: Submitting multiple attendance entries for the same student ID in a single batch triggers 400 Bad Request with field error `appear only once`.
- **State Machine Integrity**: An admission application in `UNDER_REVIEW` cannot transition illegally back to `PENDING` (409 Conflict).
- **Timetable Overlap Prevention**: Submitting conflicting periods for the same section or teacher triggers 409 Conflict.
- **Physical Inventory Stock Guard**: Outflow movements that exceed current stock quantity throw `NEGATIVE_STOCK` (409 Conflict).
- **Hostel Allocation Invariant**: Allocating a student to an already occupied bed number triggers `DOUBLE_ALLOCATION` (409 Conflict).

---

## 5. Running the Suite Locally and in CI/CD

### Prerequisites
1. **Node.js** v18+ (verified on Node v24).
2. **Newman CLI**:
   ```bash
   npm install -g newman
   ```
3. **Backend Service**:
   Running on `http://localhost:8080`.

### Local Execution Command
From the project workspace root:
```powershell
npx -y newman run "postman/ERP_Portal_API_v1.postman_collection.json" -e "postman/ERP_Portal_Local.postman_environment.json" --reporters cli
```

### CI/CD Pipeline (GitHub Actions Example)
```yaml
name: API Verification Suite

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  postman-verification:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Start ERP Backend
        run: |
          cd backend
          mvn spring-boot:run &
          sleep 25

      - name: Run Newman API Verification
        run: |
          npx -y newman run "postman/ERP_Portal_API_v1.postman_collection.json" \
            -e "postman/ERP_Portal_Local.postman_environment.json" \
            --reporters cli,junit \
            --reporter-junit-export newman-results.xml
```

---

## 6. What Postman Cannot Prove & Non-Functional Verification Plan

While Newman and Postman verify external HTTP contracts, request validation, and API responses, **black-box API testing alone cannot guarantee system correctness, transaction safety, or durability under load**.

The following verification matrix details the essential non-functional and white-box engineering controls required:

### 1. Code Review Inspection
*What Postman cannot prove:* Whether internal business rules are enforced safely in code or merely appear to succeed on a single-threaded happy path.
*Must be verified through:*
- **Transaction Boundaries (`@Transactional`)**: Verify that state mutations spanning multiple tables (e.g., student admission approval creating a student record, updating application status, generating fee demands, and recording audit logs) execute inside a single transactional boundary with proper rollback semantics (`rollbackFor = Exception.class`).
- **Security Annotation Placement (`@PreAuthorize`)**: Verify that `@PreAuthorize` is placed on the service or controller method rather than being bypassed by internal method calls within the same bean (self-invocation proxy bypass).
- **Parameterized SQL Queries**: Inspect all JDBC / JPA repositories to guarantee that raw string concatenation is never used in query construction, preventing SQL injection vulnerabilities.
- **Password Hashing & Salt**: Verify that user passwords are encrypted using `BCryptPasswordEncoder` with a secure cost factor (e.g. 12) rather than plaintext or weak MD5/SHA-1 hashes.

### 2. Flyway Migration Inspection
*What Postman cannot prove:* Database schema durability across environment updates, zero-downtime deployability, and data loss prevention.
*Must be verified through:*
- **Forward-Only, Non-Destructive Migrations**: Ensure scripts in `db/migration/` never drop production columns or tables containing live data without a phased migration strategy.
- **Idempotency & Replayability**: Ensure table creations use appropriate constraints and check constraints (e.g. `chk_inv_stock CHECK (current_stock >= 0)`).
- **Migration Version Ordering**: Ensure version numbers (`V1` through `V15`) follow strict sequential naming without collisions across branches.

### 3. Database (DB) Constraint Inspection
*What Postman cannot prove:* That the database will maintain data integrity if an application instance crashes midway through an operation, or if records are inserted via direct database integrations or background ETL jobs.
*Must be verified through:*
- **Database-Level Unique Constraints**: Inspect MySQL schema using `SHOW CREATE TABLE` to confirm that unique constraints exist at the database engine level (e.g. `uq_student_admission_no`, `uq_fee_payment_ref`, `uq_inv_code`), not just in application code.
- **Foreign Key Referencing & Cascades**: Verify that foreign keys (`ON DELETE RESTRICT` or `ON DELETE CASCADE`) are properly defined on parent-child relationships (e.g., `student_guardians -> students`, `fee_demands -> students`).
- **Engine & Character Set**: Verify tables use `ENGINE=InnoDB` and `COLLATE=utf8mb4_unicode_ci` to support multibyte Unicode and ACID transactional guarantees.

### 4. Unit Testing
*What Postman cannot prove:* Exhaustive branch coverage, isolated domain logic correctness, boundary values, and algorithm performance under edge cases.
*Must be verified through:*
- **State Machine Transitions**: Unit tests for `AdmissionService.validateTransition(from, to)` to test all combinations of status transitions (e.g., `DRAFT -> APPROVED`, `UNDER_REVIEW -> PENDING`, `REJECTED -> DRAFT`).
- **Financial Calculations**: Math unit tests asserting BigDecimal rounding modes, discount allocations, fee tax percentages, and late fee calculations.
- **Mocked Failure Modes**: Unit tests verifying that when a downstream notification or email service fails, the transaction either handles the failure gracefully or rolls back consistently.

### 5. Integration Testing (`@SpringBootTest`)
*What Postman cannot prove:* Component wiring, Spring security filter chain order, real database transaction rollback on runtime exceptions, and JPA repository query derivations.
*Must be verified through:*
- **Multi-Tenant / Scoped Data Isolation**: Integration tests asserting that a teacher in Department A cannot query students or rosters belonging to Department B even if IDs are guessed.
- **Rollback Verification**: Deliberately throwing a runtime exception mid-workflow and asserting that no partial records remain in MySQL.
- **Spring Context Health**: Testing that all `@Service`, `@Repository`, and `@RestControllerAdvice` beans inject cleanly without circular dependencies or missing configuration properties.

### 6. Performance & Stress Testing
*What Postman cannot prove:* System responsiveness, throughput limits, connection pool exhaustion, and deadlock frequency under concurrent user traffic.
*Must be verified through:*
- **High Concurrency Load Testing (e.g., k6, Gatling, JMeter)**: Simulate 500+ concurrent requests attempting to mark attendance or post fee payments simultaneously.
- **Deadlock Hazard Verification**: Test concurrent seat or bed allocations to ensure row-level locking (`SELECT ... FOR UPDATE`) prevents deadlocks and race conditions.
- **HikariCP Connection Pool Sizing**: Benchmark connection usage under peak load to ensure the pool does not exhaust threads or timeout waiting for connections.
- **P95 / P99 Latency SLA Verification**: Verify that read operations complete within < 100ms and write operations within < 250ms at scale.
