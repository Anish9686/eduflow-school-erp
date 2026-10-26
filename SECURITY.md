# Security Policy — EduFlow ERP Portal

The EduFlow engineering team treats security, privacy, and data protection as foundational pillars of the platform. This document outlines our supported versions, vulnerability disclosure procedures, and built-in defense-in-depth mechanisms.

---

## 1. Supported Versions

Security updates, patches, and hotfixes are actively provided for the following releases:

| Version | Release Date | Support Level | Security Maintenance |
| :--- | :--- | :--- | :--- |
| **1.0.x** | October 2026 | **Current Active Release** | Supported (Patches & Fixes) |
| **< 1.0.0** | Pre-release | Deprecated | Not Supported |

---

## 2. Reporting a Vulnerability

We appreciate the responsible disclosure of security vulnerabilities by security researchers, developers, and users.

> **CRITICAL NOTICE:** **Please do NOT report suspected security vulnerabilities through public GitHub issues, discussions, or pull requests.**

### How to Submit a Report
If you discover a potential vulnerability in the EduFlow platform:
1. **GitHub Private Vulnerability Reporting:** Submit a report via the **Security** tab of the GitHub repository by clicking **"Report a vulnerability"**.
2. **Alternative Security Contact:** Email our security response team at **`security@eduflow-erp.org`** with:
   - Vulnerability title and type (e.g., IDOR, SQL Injection, Privilege Escalation, CSRF).
   - Detailed step-by-step reproduction instructions or a minimal Proof of Concept (PoC).
   - Affected component(s), endpoint(s), or role(s).
   - Assessment of potential impact.

### Response Timetable
- **Acknowledgment:** Within **48 hours** of receiving your report.
- **Initial Assessment:** Within **5 business days**, confirming whether the issue is reproducible and determining its severity rating (CVSS).
- **Remediation & Patch:** A patch will be prioritized and developed based on severity (Critical: within 7 days; High: within 14 days).
- **Public Disclosure:** Coordinated disclosure will occur once a validated patch has been released.

---

## 3. Built-in Security Architecture & Controls

EduFlow incorporates enterprise-grade defense-in-depth security controls:

### 3.1 Authentication & Password Security (`REQ-SEC-001`, `REQ-AUTH`)
- **Adaptive Password Hashing:** Passwords are never stored in plaintext. They are hashed using **BCrypt with a work factor of 12**, providing robust resistance to offline dictionary and brute-force attacks.
- **Token Security:** Authenticated session tokens are opaque, high-entropy tokens. To prevent token harvesting in the event of database compromise, active session tokens are stored exclusively as **SHA-256 hashes** in the `auth_sessions` table.
- **Session Revocation:** On logout or password change, sessions are explicitly invalidated in the database. Expired tokens are rejected at the security filter boundary.
- **Account Enumeration Prevention:** Authentication failure endpoints return a generic `401 Unauthorized` message (`"Invalid username or password"`) for both unknown usernames and invalid passwords.

### 3.2 Authorization & Scope Enforcement (`REQ-AUTHZ`, `REQ-SEC-002`)
- **Authoritative Server Enforcement:** Frontend route protection provides usability, but backend security is authoritative. All REST controller methods are guarded with `@PreAuthorize("hasAuthority('MODULE:ACTION')")`.
- **Relational Data Scope:** Server-side repository queries restrict access to the caller's authorized scope (e.g., teachers are bound to assigned class sections via `teacher_assignments`; parents can only query linked children via `student_guardians`).
- **Separation of Duties:** Financial discounts and operational expense workflows enforce separation of duties; requesters cannot self-approve their own discount or expense requests.

### 3.3 Input Validation & Injection Prevention (`REQ-SEC-004`)
- **SQL Injection Defense:** All database interactions use Spring Data JPA or parameterized `JdbcTemplate` queries. Unvalidated input is never concatenated into SQL statements.
- **Sort Column Allow-Listing:** Sort parameters on pagination endpoints are checked against an explicit allow-list of table columns via `PageableValidator` and `SqlSanitizer` to eliminate injection through `ORDER BY` clauses.
- **Path Traversal Protection:** Student document uploads (`/api/v1/students/{id}/documents`) validate file types against an allow-list (PDF, PNG, JPEG) and enforce file system bounds checks (`!path.startsWith(storageRoot)`) on download to prevent arbitrary file reading.
- **Spreadsheet Formula Injection:** All exported CSV reports sanitize text cells containing dangerous formula trigger characters (`=`, `+`, `-`, `@`, `\t`, `\r`) via `CsvSafety.java`.

### 3.4 Observability & Data Leakage Prevention (`NFR-OBS-001`, `REQ-ARCH-005`)
- **Safe Error Contracts:** In production (`application-prod.properties`), internal exception stack traces, database schema details, and framework internals are suppressed. Only standard, structured error envelopes are returned.
- **PII & Secret Log Redaction:** Passwords, tokens, authorization headers, and confidential financial identifiers are stripped from application log output.
- **Correlation IDs:** Every request is assigned an `X-Correlation-ID` header, propagated across SLF4J MDC logs for security event correlation.

---

## 4. Production Hardening Checklist

When deploying EduFlow to staging or production environments:
1. **Mandatory TLS Termination:** Ensure production traffic runs over TLS 1.3 (terminated at Nginx or a cloud load balancer). Enable HTTP Strict Transport Security (HSTS) with `max-age=31536000; includeSubDomains; preload`.
2. **Environment Secret Management:** Never hardcode passwords or keys in source control. Inject database credentials (`DB_PASSWORD`) and session settings via cloud secret managers (AWS Secrets Manager, HashiCorp Vault, or Kubernetes Secrets).
3. **Hibernate DDL Setting:** Always ensure `spring.jpa.hibernate.ddl-auto=validate` in production to prevent unintended schema mutations.
4. **Seed Account Rotation:** Immediately change all default test account passwords (`Anish9661`, `Satyam9801`, `Amit9661`, etc.) seeded in the database.
5. **CORS Restrictions:** Configure `CORS_ALLOWED_ORIGINS` to include only trusted institution domain names; never use wildcard (`*`) origins in production.
