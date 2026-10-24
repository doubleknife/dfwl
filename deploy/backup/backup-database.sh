#!/usr/bin/env bash
# Ubuntu production: root authenticates through the local MySQL Unix socket.
set -Eeuo pipefail
umask 077
export TZ=Asia/Shanghai
unset MYSQL_PWD

readonly DATABASE=fleet_ops
readonly BACKUP_DIR=/opt/whdfwl/backup/database
readonly MYSQL_SOCKET=/var/run/mysqld/mysqld.sock
readonly RETENTION_DAYS=30
temporary_file=''

log() { printf '[%s] %s\n' "$(date '+%F %T %Z')" "$*"; }
cleanup() {
    local result=$?
    trap - EXIT
    if [[ -n "$temporary_file" && -f "$temporary_file" ]]; then
        rm -f -- "$temporary_file"
    fi
    if (( result != 0 )); then
        log "ERROR: database backup failed (exit $result); existing backups retained."
    fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if (( EUID != 0 )); then
    log 'ERROR: run this script as root (sudo).'
    exit 1
fi

for tool in mysqldump gzip flock mktemp find date mv stat setfacl; do
    command -v "$tool" >/dev/null || { log "ERROR: missing command: $tool"; exit 1; }
done

exec 9>/run/lock/whdfwl-db-backup.lock
if ! flock -n 9; then
    log 'Another database backup is running; skipping this invocation.'
    exit 0
fi

mkdir -p -- "$BACKUP_DIR"
chmod 700 -- "$BACKUP_DIR"
setfacl -m u:ubuntu:r-x /opt/whdfwl /opt/whdfwl/backup
setfacl -m u:ubuntu:r-x "$BACKUP_DIR"
readonly stamp="$(date '+%Y%m%d_%H%M%S')"
readonly destination="$BACKUP_DIR/${DATABASE}_${stamp}_$$.sql.gz"
temporary_file=$(mktemp "$BACKUP_DIR/.${DATABASE}_${stamp}.XXXXXX.part")

log "Starting backup of $DATABASE."
# --single-transaction provides a consistent snapshot for InnoDB without
# blocking ordinary writes. Do not run schema migrations during a backup.
mysqldump --no-defaults --protocol=SOCKET --socket="$MYSQL_SOCKET" --user=root \
    --single-transaction --quick --routines --triggers --events \
    --hex-blob --no-tablespaces --set-gtid-purged=OFF \
    --default-character-set=utf8mb4 --databases "$DATABASE" \
    | gzip -c > "$temporary_file"

test -s "$temporary_file"
gzip -t -- "$temporary_file"
mv -- "$temporary_file" "$destination"
temporary_file=''
setfacl -m u:ubuntu:r-- "$destination"
log "Backup completed: $destination ($(stat -c '%s' "$destination") bytes)."

# Prune only this script's completed backups, and only after successful backup.
find "$BACKUP_DIR" -maxdepth 1 -type f -regextype posix-extended \
    -regex '.*/fleet_ops_[0-9]{8}_[0-9]{6}_[0-9]+\.sql\.gz' \
    -mmin "+$((RETENTION_DAYS * 24 * 60))" -print -delete
log "Retention cleanup completed: $RETENTION_DAYS days."
