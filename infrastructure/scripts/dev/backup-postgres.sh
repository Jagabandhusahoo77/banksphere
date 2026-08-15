#!/bin/bash
# BankSphere DEV Postgres backup — runs ON the DEV EC2 instance (e.g. via
# an SSM session, or as a cron/systemd-timer job you set up separately —
# no automated schedule is created by Terraform in this phase, see
# docs/deployment/postgresql.md's "Backups" section for why and what a
# real schedule would need).
#
# Dumps all six databases with pg_dumpall (simplest correct option for a
# small number of databases sharing one Postgres instance — captures
# roles/ownership too, which per-database pg_dump would not), gzips the
# result, and writes it to the SAME encrypted EBS volume Postgres itself
# lives on (see infrastructure/terraform/environments/dev/ec2.tf's
# root_block_device). This is a real, working backup mechanism — restore
# = `gunzip -c <file> | docker exec -i banksphere-postgres psql -U banksphere postgres`
# — but it is NOT off-instance: if the EBS volume is lost, the backups
# are lost with it. Copying these dumps to S3 (or provisioning EBS
# snapshots via AWS Backup) is the natural next step and is intentionally
# left for a later phase — see docs/deployment/postgresql.md.
set -euo pipefail

BACKUP_DIR="/opt/banksphere/backups"
TIMESTAMP=$(date -u +%Y%m%dT%H%M%SZ)
BACKUP_FILE="${BACKUP_DIR}/banksphere-dev-${TIMESTAMP}.sql.gz"
RETENTION_DAYS=7

mkdir -p "${BACKUP_DIR}"

echo "== Dumping all databases to ${BACKUP_FILE} =="
docker exec banksphere-postgres pg_dumpall -U banksphere | gzip > "${BACKUP_FILE}"

echo "== Verifying the dump is non-empty =="
if [ ! -s "${BACKUP_FILE}" ]; then
  echo "ERROR: backup file is empty — treating this as a failed backup." >&2
  rm -f "${BACKUP_FILE}"
  exit 1
fi

echo "== Pruning backups older than ${RETENTION_DAYS} days =="
find "${BACKUP_DIR}" -name 'banksphere-dev-*.sql.gz' -mtime "+${RETENTION_DAYS}" -delete

echo "== Backup complete: $(du -h "${BACKUP_FILE}" | cut -f1) =="
