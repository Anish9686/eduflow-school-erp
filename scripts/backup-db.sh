#!/usr/bin/env bash
# ============================================================
# EduFlow ERP Portal — Automated Database Backup Script
# NFR-REC-001: RPO <= 24 Hours
# ============================================================
set -euo pipefail

TIMESTAMP=$(date -u +"%Y%m%d_%H%M%SZ")
BACKUP_DIR="${BACKUP_DIR:-/var/backups/erp/mysql}"
BACKUP_FILE="${BACKUP_DIR}/erp_portal_backup_${TIMESTAMP}.sql.gz.enc"
PASSPHRASE_FILE="${PASSPHRASE_FILE:-/etc/erp/backup_encryption.key}"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-erp_portal_db}"

mkdir -p "${BACKUP_DIR}"

echo "[${TIMESTAMP}] Starting consistent transactional backup of ${DB_NAME}..."

# Execute consistent snapshot using single-transaction to prevent table locks
mysqldump \
  --host="${DB_HOST}" \
  --port="${DB_PORT}" \
  --user="${DB_USERNAME:?DB_USERNAME is required}" \
  --password="${DB_PASSWORD:?DB_PASSWORD is required}" \
  --single-transaction \
  --quick \
  --routines \
  --triggers \
  --events \
  --set-gtid-purged=OFF \
  "${DB_NAME}" \
  | gzip -c \
  | openssl enc -aes-256-cbc -salt -pbkdf2 -pass file:"${PASSPHRASE_FILE}" \
  > "${BACKUP_FILE}"

# Generate SHA-256 checksum for verification
sha256sum "${BACKUP_FILE}" > "${BACKUP_FILE}.sha256"

echo "Backup created and encrypted: ${BACKUP_FILE}"

# Optional sync to off-site cloud storage if configured
if [ -n "${BACKUP_S3_BUCKET:-}" ]; then
  echo "Synchronizing backup to S3 bucket ${BACKUP_S3_BUCKET}..."
  aws s3 cp "${BACKUP_FILE}" "${BACKUP_S3_BUCKET}/production/${TIMESTAMP}/" --sse aws:kms
  aws s3 cp "${BACKUP_FILE}.sha256" "${BACKUP_S3_BUCKET}/production/${TIMESTAMP}/"
fi

echo "[${TIMESTAMP}] Backup completed successfully."
