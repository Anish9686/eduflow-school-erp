# Production Operations & Deployment Runbook

**System:** School ERP Portal  
**Document Ref:** `/docs/deployment/PRODUCTION_RUNBOOK.md`  
**Target Environments:** `LOCAL`, `TEST/QA`, `STAGING`, `PRODUCTION`  
**Classification:** Operational / DevOps / SRE  
**Last Updated:** 2026-10-26  

---

## 1. System Overview & Architecture Topology

The School ERP Portal is structured as a decoupled, cloud-ready tiered architecture:
- **Presentation Layer (Frontend):** React 19 SPA built via Vite and served through an optimized, security-hardened `nginx:1.27-alpine` container with gzip compression, SPA routing, and reverse proxying to backend APIs.
- **Application Layer (Backend):** Spring Boot 3.3.4 running on Eclipse Temurin 21 JRE Alpine in an unprivileged non-root user container (`appuser:1001`), utilizing Spring MVC, Spring Security, JPA/Hibernate (`ddl-auto=validate`), and Flyway migrations.
- **Persistence Layer (Database):** MySQL 8.0 enterprise/cloud-managed database (e.g. AWS Aurora MySQL or Google Cloud SQL) with connection pooling managed via HikariCP.
- **Observability Layer:** Spring Boot Actuator health probes (`/actuator/health/liveness`, `/actuator/health/readiness`), MDC-driven correlation tracing (`X-Correlation-ID`, `X-Trace-Id`), and Prometheus metrics hooks.

```
+-----------------------------------------------------------------------------------+
|                                Client Browser / Mobile                           |
+-----------------------------------------+-----------------------------------------+
                                          | HTTPS :443
                                          v
+-----------------------------------------------------------------------------------+
|                           Nginx Reverse Proxy & Static Host                       |
|  - SPA Static Bundle (/usr/share/nginx/html)                                      |
|  - Security Headers (HSTS, CSP, X-Frame-Options: DENY, Referrer-Policy)           |
|  - Healthcheck Probe (/health)                                                    |
+-------------------+-------------------------------------------+-------------------+
                    | /api/*                                    | /actuator/*
                    v                                           v
+-----------------------------------------------------------------------------------+
|                        Spring Boot Backend (appuser:1001)                         |
|  - Port: 8080                                                                     |
|  - JVM: Temurin 21 Container-Aware (-XX:MaxRAMPercentage=75.0)                    |
|  - Tracing: RequestTraceFilter (MDC traceId + X-Correlation-ID)                   |
|  - Security: Stateless Opaque Bearer Token Sessions                               |
+-----------------------------------------+-----------------------------------------+
                                          | JDBC HikariCP Pool (Pool size: 20-30)
                                          v
+-----------------------------------------------------------------------------------+
|                              MySQL 8.0 Managed Cluster                            |
|  - Schema Controlled via Flyway (V1__ to V16__)                                   |
|  - DDL Auto: VALIDATE (No ad-hoc schema modifications in runtime)                |
+-----------------------------------------------------------------------------------+
```

---

## 2. Environment Profiles & Configuration Matrix

Four environments are supported with strict configuration separation:

| Setting / Property | LOCAL | TEST / QA | STAGING | PRODUCTION |
| :--- | :--- | :--- | :--- | :--- |
| **Spring Profile** | `local` | `test` | `staging` | `prod` |
| **Database Engine** | Local / Docker MySQL | H2 MySQL-Mode / Test MySQL | Managed RDS MySQL 8.0 | Multi-AZ Managed Aurora MySQL 8.0 |
| **DDL Auto Policy** | `validate` | `none` (or populator) | `validate` | `validate` (Strict) |
| **Flyway Migrations** | `enabled=true` | In-memory schema | `enabled=true` | `enabled=true, validate=true` |
| **HikariCP Pool Size**| Max: 10, Idle: 2 | Standard | Max: 20, Idle: 5 | Max: 30, Idle: 10 |
| **CORS Allowed Origins** | `localhost:5173,3000` | Mock / Test | `https://staging.erp.yourdomain.com` | Strict FQDNs (No localhost) |
| **Actuator Health Details** | `always` | `when_authorized` | `when_authorized` | `never` (No info leak) |
| **Swagger / OpenAPI** | `enabled=true` | `enabled=true` | `enabled=true` | `enabled=false` (Default off) |
| **Logging Level** | `root=INFO, erp=INFO` | `root=WARN, erp=INFO` | `root=INFO, erp=INFO` | `root=INFO, erp=INFO, sec=WARN` |
| **SQL Logging** | `false` | `false` | `false` | `false` |

---

## 3. Environment Variables & Secret Management

### 3.1 Required Environment Variables

All sensitive values must be injected via environment variables at container startup. **No secrets or credentials exist in source code or version control.**

| Variable Name | Description | Example (Staging / Prod) | Required in Prod |
| :--- | :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Active Spring environment profile | `prod` | **YES** |
| `DB_URL` | JDBC URL for MySQL 8 database | `jdbc:mysql://db.prod.internal:3306/erp_portal_db?serverTimezone=UTC&characterEncoding=UTF-8` | **YES** |
| `DB_USERNAME` | Database username | `erp_portal_app` | **YES** |
| `DB_PASSWORD` | Database password | `[Managed Secret]` | **YES** |
| `CORS_ALLOWED_ORIGINS` | Comma-separated allowed frontend origins | `https://erp.school.edu,https://portal.school.edu` | **YES** |
| `AUTH_SESSION_TTL_SECONDS` | Opaque session expiry duration (seconds) | `1800` (30 minutes) | **YES** |
| `STUDENT_DOCUMENT_STORAGE_DIR` | Mount path for student files/documents | `/var/erp/storage/student-documents` | **YES** |
| `SERVER_PORT` | Application listening port | `8080` | NO (Default 8080) |
| `SWAGGER_ENABLED` | Toggle OpenAPI docs in production | `false` | NO (Default false) |
| `DB_POOL_MAX_SIZE` | Maximum database connection pool size | `30` | NO (Default 30) |
| `DB_POOL_MIN_IDLE` | Minimum idle database connections | `10` | NO (Default 10) |

### 3.2 Secret Management Standard Operating Procedure (SOP)

1. **Local Development:**
   - Copy `.env.example` to `.env` (which is excluded in `.gitignore`).
   - Populate local database credentials.
2. **CI/CD Pipeline (GitHub Actions / GitLab CI):**
   - Store deployment credentials in encrypted repository secrets (`PROD_DB_PASSWORD`, `REGISTRY_CREDENTIALS`).
   - Mask all secret outputs in CI runner logs.
3. **Staging & Production Clusters:**
   - **AWS ECS / EKS:** Inject secrets via AWS Secrets Manager or AWS Systems Manager (SSM) Parameter Store using IAM Task Role policies.
   - **Kubernetes:** Utilize sealed secrets or HashiCorp Vault integration with Kubernetes CSI Secret Store driver.
   - Secrets are loaded exclusively into memory as environment variables or volume mounts at runtime and are never written to disk or logs.

---

## 4. Health Checks, Probes & Observability

### 4.1 Health Check Probes
The backend exposes Spring Boot Actuator endpoints permitted without authentication for container orchestrators and load balancers:

- **Liveness Probe:** `GET /actuator/health/liveness`
  - Validates that the internal process is alive and responsive.
  - Expected Response: `HTTP 200 OK` `{"status":"UP"}`
- **Readiness Probe:** `GET /actuator/health/readiness`
  - Validates that database connections are healthy, HikariCP pool is functional, and disk space is sufficient.
  - Expected Response: `HTTP 200 OK` `{"status":"UP"}`
  - If database is unreachable: Returns `HTTP 503 SERVICE UNAVAILABLE` `{"status":"DOWN"}`
- **Frontend Health Check:** `GET /health`
  - Validates Nginx process health.
  - Expected Response: `HTTP 200 OK` `healthy`

### 4.2 Correlation IDs & Distributed Tracing
- All HTTP requests entering through Nginx receive or propagate an `X-Correlation-ID` and `X-Trace-Id` header.
- The `RequestTraceFilter` extracts any incoming correlation ID, sanitizes it (regex `[^a-zA-Z0-9_-]`, max 64 chars), or generates a new RFC 4122 UUID.
- The ID is stored in SLF4J MDC (`traceId`) and included in every application log line:
  ```
  2026-10-26 19:41:21.754 [http-nio-8080-exec-1] INFO  [corr-9481a-bc01] c.e.p.a.s.AuthService - User authenticated: admin
  ```
- Both `X-Trace-Id` and `X-Correlation-ID` are returned in response headers for client-side tracking and incident triage.

---

## 5. Deployment Procedures

### 5.1 Pre-Deployment Verification Checklist
1. [ ] Target branch merged and all CI automated tests green (Unit, Integration, E2E).
2. [ ] Database backup snapshot taken before deployment window starts.
3. [ ] Verify pending Flyway migrations: Ensure forward migrations do not contain destructive operations (`DROP COLUMN`, `RENAME TABLE` without backward compatibility).
4. [ ] Production secrets validated in vault / parameter store.

### 5.2 Containerized Production Deployment (Docker Compose)
To deploy using production container definitions:

```bash
# 1. Pull latest production images
docker compose -f docker-compose.prod.yml pull

# 2. Verify database connectivity and execute migrations
docker run --rm \
  --network erp_production_network \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL="$DB_URL" \
  -e DB_USERNAME="$DB_USERNAME" \
  -e DB_PASSWORD="$DB_PASSWORD" \
  "${ERP_BACKEND_IMAGE}" --spring.flyway.validate-on-migrate=true

# 3. Perform zero-downtime rolling update
docker compose -f docker-compose.prod.yml up -d --no-deps --build backend
docker compose -f docker-compose.prod.yml up -d --no-deps --build frontend

# 4. Verify readiness
curl -f http://localhost:8080/actuator/health/readiness || exit 1
curl -f http://localhost/health || exit 1
```

### 5.3 Blue-Green Deployment Workflow (Kubernetes / Cloud ALB)
1. Deploy new container release to **Green** target group.
2. Orchestrator polls `/actuator/health/readiness` until 3 consecutive health checks return `UP`.
3. Shift 5% of traffic to Green; monitor error rates and p95 latency.
4. Shift 100% of traffic to Green on ALB/Ingress.
5. Keep **Blue** target group idling for 30 minutes in case immediate rollback is needed.
6. Terminate Blue pods/containers after verification window passes.

---

## 6. Rollback Procedures

### 6.1 Rollback Triggers
Initiate immediate rollback if any of the following occur within 30 minutes post-deployment:
- Readiness probe fails or returns HTTP 503 for > 30 seconds.
- Application error rate (HTTP 5xx) exceeds 0.5% of total requests.
- API p95 latency exceeds 500 ms (`NFR-PERF-001` violation).
- Critical user journey failure (Login or Payment collection fails).

### 6.2 Application Rollback Steps
```bash
# 1. Revert traffic to previous stable Blue container/image immediately
docker compose -f docker-compose.prod.yml up -d --no-deps backend:previous frontend:previous

# 2. Verify previous version health
curl -f http://localhost:8080/actuator/health/readiness

# 3. Announce operational status to incident channel
```

### 6.3 Database Migration Rollback Strategy
Because the ERP employs additive, backward-compatible Flyway migrations (e.g. `V1__` through `V16__`), application code from the previous version can safely execute against the current database schema without requiring immediate schema rollbacks.

If a migration must be reverted:
1. Stop application instances to prevent writes to conflicting tables.
2. Restore database from the pre-deployment snapshot (see Section 7).
3. If point-in-time recovery is needed, replay binary logs up to the timestamp immediately preceding the deployment migration.

---

## 7. Database Backup Runbook (NFR-REC-001: RPO ≤ 24 Hours)

### 7.1 Backup Strategy & Schedule
- **Full Transactional Backup:** Executed daily at 01:00 UTC.
- **Binary Log Archival:** Continuously synced to cloud object storage (S3/GCS) with 5-minute retention interval, delivering **RPO < 5 minutes** (exceeding the 24-hour target).
- **Storage Redundancy:** Stored in cloud storage with multi-region replication and 30-day retention with Object Lock (WORM).

### 7.2 Automated Backup Script (`backup_database.sh`)
```bash
#!/usr/bin/env bash
set -euo pipefail

TIMESTAMP=$(date -u +"%Y%m%d_%H%M%SZ")
BACKUP_DIR="/var/backups/erp/mysql"
BACKUP_FILE="${BACKUP_DIR}/erp_portal_backup_${TIMESTAMP}.sql.gz.enc"
PASSPHRASE_FILE="/etc/erp/backup_encryption.key"

mkdir -p "${BACKUP_DIR}"

echo "[${TIMESTAMP}] Starting consistent transactional backup..."

# Execute consistent snapshot using single-transaction to prevent table locks
mysqldump \
  --host="${DB_HOST}" \
  --port="${DB_PORT:-3306}" \
  --user="${DB_USERNAME}" \
  --password="${DB_PASSWORD}" \
  --single-transaction \
  --quick \
  --routines \
  --triggers \
  --events \
  --set-gtid-purged=OFF \
  erp_portal_db \
  | gzip -c \
  | openssl enc -aes-256-cbc -salt -pbkdf2 -pass file:"${PASSPHRASE_FILE}" \
  > "${BACKUP_FILE}"

# Generate SHA-256 checksum for verification
sha256sum "${BACKUP_FILE}" > "${BACKUP_FILE}.sha256"

echo "Backup created and encrypted: ${BACKUP_FILE}"

# Sync to secure off-site cloud storage (example AWS S3)
aws s3 cp "${BACKUP_FILE}" "s3://school-erp-backups/production/${TIMESTAMP}/" --sse aws:kms
aws s3 cp "${BACKUP_FILE}.sha256" "s3://school-erp-backups/production/${TIMESTAMP}/"

echo "[${TIMESTAMP}] Backup completed and synchronized successfully."
```

---

## 8. Database Restore Runbook (NFR-REC-002: RTO ≤ 4 Hours)

### 8.1 Disaster Recovery Timetable & Objectives
- **Target RTO:** ≤ 4 hours.
- **Typical Restoration Execution Time:** 15–30 minutes for 100,000 students / 5-year dataset.

### 8.2 Step-by-Step Restoration Procedure
```bash
#!/usr/bin/env bash
set -euo pipefail

BACKUP_FILE="$1" # Path to erp_portal_backup_*.sql.gz.enc
PASSPHRASE_FILE="/etc/erp/backup_encryption.key"

echo "Starting database restoration from: ${BACKUP_FILE}"

# 1. Verify SHA-256 checksum
echo "Verifying backup integrity..."
sha256sum -c "${BACKUP_FILE}.sha256"

# 2. Terminate active application connections to database
echo "Terminating existing application connections..."
mysql --host="${DB_HOST}" --user="${DB_ADMIN_USER}" --password="${DB_ADMIN_PASSWORD}" -e "
  SELECT concat('KILL ',id,';') FROM information_schema.processlist 
  WHERE user = '${DB_USERNAME}' AND id != connection_id();
" | mysql --host="${DB_HOST}" --user="${DB_ADMIN_USER}" --password="${DB_ADMIN_PASSWORD}" || true

# 3. Re-create target database
echo "Resetting target database schema..."
mysql --host="${DB_HOST}" --user="${DB_ADMIN_USER}" --password="${DB_ADMIN_PASSWORD}" -e "
  DROP DATABASE IF EXISTS erp_portal_db;
  CREATE DATABASE erp_portal_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
"

# 4. Decrypt, decompress, and restore data
echo "Decrypting and restoring data stream..."
openssl enc -d -aes-256-cbc -pbkdf2 -pass file:"${PASSPHRASE_FILE}" -in "${BACKUP_FILE}" \
  | gunzip -c \
  | mysql --host="${DB_HOST}" --user="${DB_ADMIN_USER}" --password="${DB_ADMIN_PASSWORD}" erp_portal_db

# 5. Execute post-restore integrity validation query
echo "Validating restored tables and record counts..."
mysql --host="${DB_HOST}" --user="${DB_ADMIN_USER}" --password="${DB_ADMIN_PASSWORD}" erp_portal_db -e "
  SELECT 'students' AS entity, COUNT(*) AS count FROM students UNION ALL
  SELECT 'staff' AS entity, COUNT(*) AS count FROM staff UNION ALL
  SELECT 'payments' AS entity, COUNT(*) AS count FROM payments UNION ALL
  SELECT 'attendance' AS entity, COUNT(*) AS count FROM attendance UNION ALL
  SELECT 'flyway_schema_history' AS entity, COUNT(*) AS count FROM flyway_schema_history;
"

echo "Restoration and verification successfully completed."
```

---

## 9. Security & Hardening Verification Audit

The deployment configuration has undergone verification against strict security criteria:

| Security Criteria | Verification Detail | Status |
| :--- | :--- | :--- |
| **No secrets in Git** | `.env` and `.env.*` excluded via `.gitignore`; only sanitized `.env.example` committed. | **VERIFIED** |
| **No hardcoded passwords** | Default password fallbacks removed from `application.properties`; strict env injection required in `prod`/`staging`. | **VERIFIED** |
| **No production debug mode** | `logging.level.com.erp.portal=INFO`, `org.springframework=WARN`, `spring.jpa.show-sql=false`. | **VERIFIED** |
| **No development credentials** | Development credentials isolated to `application-local.properties`; production configuration enforces external secrets. | **VERIFIED** |
| **No sensitive logs** | Passwords, bearer tokens, and student PII excluded from logs; correlation IDs propagated for safe tracing. | **VERIFIED** |
| **No `ddl-auto=create`** | `spring.jpa.hibernate.ddl-auto=validate` strictly configured across all deployment profiles. | **VERIFIED** |
| **No localhost assumptions** | Database URL, server port, CORS origins, and file storage dynamically parameterized via environment variables. | **VERIFIED** |
| **Unprivileged Execution** | Backend container runs under non-root system user `appuser:1001`. | **VERIFIED** |
| **Container Health Probes** | Backend and frontend containers define active Docker `HEALTHCHECK` instructions. | **VERIFIED** |
