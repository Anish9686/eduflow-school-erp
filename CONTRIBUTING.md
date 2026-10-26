# Contributing to EduFlow ERP Portal

Thank you for your interest in contributing to the EduFlow School ERP platform! We welcome contributions from developers, QA automation engineers, DevOps practitioners, and technical writers.

To maintain architectural integrity, code quality, and security standards, all contributors must follow the guidelines detailed below.

---

## 1. Code of Conduct

We are committed to providing a welcoming, inclusive, and harassment-free environment for everyone. Please be respectful, constructive, and professional in all interactions across issues, pull requests, and discussions.

---

## 2. Development Workflow

### 2.1 Branching Strategy
- **`main` / `master`:** The primary production-stable branch. All code must pass CI tests before merging.
- **Feature Branches:** Create your work branch from the latest `main` using standard naming conventions:
  - `feature/add-student-csv-import` — New features or functional enhancements
  - `bugfix/attendance-date-boundary` — Defect fixes
  - `refactor/performance-caching` — Code improvements without functional changes
  - `docs/update-rbac-matrix` — Documentation updates
  - `test/add-hostel-concurrency-test` — Test additions or improvements

### 2.2 Conventional Commit Messages
We follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:
```
<type>(<scope>): <short imperative summary>

[optional body explaining rationale]

[optional footer referencing issue]
```
**Examples:**
- `feat(finance): add automatic demand generation retry logic`
- `fix(attendance): prevent duplicate records on concurrent marking`
- `docs(readme): update local Docker Compose startup instructions`
- `test(security): add negative test for forged JWT session`

---

## 3. Coding Standards & Architectural Invariants

### 3.1 Backend Standards (Java 21 / Spring Boot 3)
1. **Layer Separation:**
   - Controllers handle HTTP routing, request/response DTO serialization, and `@Valid` validation.
   - Services (`@Service`) own all business rules, transaction boundaries (`@Transactional`), and entity transformations.
   - Repositories (`@Repository` / Spring Data JPA) handle data access.
   - **Never expose JPA entities directly to API controllers.** Always use typed Java records or DTOs.
2. **Authoritative Server Security:**
   - All protected controller endpoints must declare `@PreAuthorize("hasAuthority('MODULE:ACTION')")`.
   - Never rely on frontend UI controls to enforce access restrictions.
3. **Database Migrations:**
   - Never modify existing Flyway migration scripts (`V1` to `V16`).
   - All schema changes must be forward-only and additive, added as a new versioned script (e.g. `V17__description.sql`) under `backend/src/main/resources/db/migration/`.
   - Hibernate runs with `ddl-auto=validate` in production; do not rely on auto-DDL.
4. **Data Scope & SQL Injection:**
   - Always enforce tenant and user scoping in SQL queries (e.g., teachers restricted to assigned class sections).
   - Use parameterized queries; never concatenate unvalidated user inputs into SQL strings.
   - Sort parameters must be checked against an allow-list via `PageableValidator` and `SqlSanitizer`.
5. **Observability & Logging:**
   - Never log passwords, tokens, secret keys, or unnecessary PII.
   - Always propagate the SLF4J MDC `traceId` correlation header.

### 3.2 Frontend Standards (React 19 / TypeScript 5)
1. **Type Safety:**
   - Enable strict TypeScript checking. Avoid `any` types; define explicit interfaces or types.
   - Run `npm run typecheck` to ensure zero compilation warnings.
2. **Design System & Aesthetics:**
   - Use the established Vanilla CSS design token system (`--bg-primary`, `--text-primary`, `--primary-600`, etc.).
   - Ensure components look visually refined in both **Light** and **Dark** themes.
   - Maintain responsiveness across 360px (mobile), 768px (tablet), and 1280px+ (desktop).
3. **State Management & Caching:**
   - Use TanStack React Query for asynchronous server state.
   - Define explicit query keys and invalidation triggers on mutation.
4. **Forms:**
   - Use React Hook Form with Zod schema validation.
   - Surface server validation errors (`fieldErrors`) beside the relevant input fields.

---

## 4. Testing Requirements

A contribution will not be merged without corresponding test coverage:

- **Backend:**
  - New service logic requires unit tests (`Mockito` / JUnit 5).
  - Transactional, persistence, or security changes require integration tests (`@SpringBootTest`).
  - Run the full test suite before submitting:
    ```bash
    cd backend
    mvn test
    ```
- **Frontend:**
  - New components and route views require Vitest unit/integration tests in `frontend/src/`.
  - Run the frontend test suite:
    ```bash
    cd frontend
    npm test
    npm run build
    ```
- **API Contracts:**
  - If modifying an existing REST endpoint, update the Postman collection in `postman/` and verify that the Newman test suite executes cleanly:
    ```bash
    npx newman run "postman/ERP_Portal_API_v1.postman_collection.json" \
      -e "postman/ERP_Portal_Local.postman_environment.json"
    ```

---

## 5. Pull Request Process

1. **Keep Pull Requests Focused:** One PR should solve one specific issue or feature. Avoid bundling unrelated refactors.
2. **Fill Out the Template:** Complete all sections of the [Pull Request Template](.github/pull_request_template.md).
3. **Continuous Integration (CI):** Verify that the GitHub Actions CI workflow passes on your branch.
4. **Code Review:** Address any feedback from project maintainers promptly and constructively.

---

## 6. Reporting Issues & Requesting Features

- **Bug Reports:** Use our [Bug Report Template](.github/ISSUE_TEMPLATE/bug_report.md). Include steps to reproduce, expected vs actual behavior, and error logs if applicable.
- **Feature Requests:** Use our [Feature Request Template](.github/ISSUE_TEMPLATE/feature_request.md). Explain the educational use case, target user role, and proposed acceptance criteria.
- **Security Disclosures:** **Do not report security vulnerabilities via public GitHub issues.** Follow the procedure outlined in [SECURITY.md](SECURITY.md).
