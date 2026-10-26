#!/usr/bin/env bash
# ============================================================
# EduFlow ERP Portal — Automated Database Disaster Recovery Script
# NFR-REC-002: RTO <= 4 Hours
# ============================================================
set -euo pipefail

BACKUP_FILE="${1:?Usage: $0 <path-to-backup-file.sql.gz.enc>}"
PASSPHRASE_FILE="${PASSPHRASE_FILE:-/etc/erp/backup_encryption.key}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_ADMIN_USER="${DB_ADMIN_USER:-root}"
DB_ADMIN_PASSWORD="${DB_ADMIN_PASSWORD:?DB_ADMIN_PASSWORD is required}"
DB_NAME="${DB_NAME:-erp_portal_db}"

echo "Starting database restoration from: ${BACKUP_FILE}"

# 1. Verify SHA-256 checksum if checksum file exists
if [ -f "${BACKUP_FILE}.sha256" ]; then
  echo "Verifying backup integrity checksum..."
  sha256sum -c "${BACKUP_FILE}.sha256"
fi

# 2. Terminate active application connections
echo "Terminating existing application connections..."
mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_ADMIN_USER}" --password="${DB_ADMIN_PASSWORD}" -e "
  SELECT concat('KILL ',id,';') FROM information_schema.processlist 
  WHERE user = '${DB_USERNAME:-erp_user}' AND id != connection_id();
" 2>/dev/null | mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_ADMIN_USER}" --password="${DB_ADMIN_PASSWORD}" || true

# 3. Re-create target database schema
echo "Resetting target database schema..."
mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_ADMIN_USER}" --password="${DB_ADMIN_PASSWORD}" -e "
  DROP DATABASE IF EXISTS ${DB_NAME};
  CREATE DATABASE ${DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
"

# 4. Decrypt, decompress, and restore data stream
echo "Decrypting and restoring data stream into ${DB_NAME}..."
openssl enc -d -aes-256-cbc -pbkdf2 -pass file:"${PASSPHRASE_FILE}" -in "${BACKUP_FILE}" \
  | gunzip -c \
  | mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_ADMIN_USER}" --password="${DB_ADMIN_PASSWORD}" "${DB_NAME}"

# 5. Execute post-restore integrity validation query
echo "Validating restored tables and record counts..."
mysql --host="${DB_HOST}" --port="${DB_PORT}" --user="${DB_ADMIN_USER}" --password="${DB_ADMIN_PASSWORD}" "${DB_NAME}" -e "
  SELECT 'students' AS entity, COUNT(*) AS count FROM students UNION ALL
  SELECT 'staff' AS entity, COUNT(*) AS count FROM staff UNION ALL
  SELECT 'payments' AS entity, COUNT(*) AS count FROM payments UNION ALL
  SELECT 'attendance' AS entity, COUNT(*) AS count FROM attendance UNION ALL
  SELECT 'flyway_schema_history' AS entity, COUNT(*) AS count FROM flyway_schema_history;
"

echo "Restoration and verification successfully completed."
