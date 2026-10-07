#!/usr/bin/env bash
# Read-only source backups and restores into a fresh, isolated temporary PostgreSQL container.
set -euo pipefail
umask 077
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"
compose=(docker compose --env-file .local.env -f domain-platform-infra/compose.local.yaml)
backup_dir="$(mktemp -d /tmp/gerhub-restore.XXXXXX)"
restore_container="gerhub-restore-$(openssl rand -hex 6)"
export POSTGRES_PASSWORD="$(openssl rand -hex 24)"
cleanup() { docker stop "$restore_container" >/dev/null 2>&1 || true; }
trap cleanup EXIT
# --rm removes only this new scratch container. Source data volumes are never attached to it.
docker run --rm --detach --name "$restore_container" --network none --env POSTGRES_PASSWORD \
  --tmpfs /var/lib/postgresql/data postgres:16-alpine >/dev/null
for attempt in {1..30}; do
  if docker exec "$restore_container" pg_isready -U postgres >/dev/null 2>&1; then break; fi
  sleep 1
done
for service in identity listing payment search; do
  case "$service" in
    identity) table=platform_users ;;
    listing) table=properties ;;
    payment) table=payment_orders ;;
    search) table=saved_searches ;;
  esac
  "${compose[@]}" exec -T "$service-db" pg_dump -U "${service}_user" -d "${service}_db" --format=custom > "$backup_dir/$service.dump"
  source_count="$("${compose[@]}" exec -T "$service-db" psql -U "${service}_user" -d "${service}_db" -Atc "SELECT count(*) FROM $table")"
  docker exec "$restore_container" createdb -U postgres "${service}_restore"
  docker exec -i "$restore_container" pg_restore -U postgres -d "${service}_restore" --no-owner --exit-on-error < "$backup_dir/$service.dump"
  restored_count="$(docker exec "$restore_container" psql -U postgres -d "${service}_restore" -Atc "SELECT count(*) FROM $table")"
  if [[ "$source_count" != "$restored_count" ]]; then echo "$service restore count differs" >&2; exit 1; fi
  printf '%s: restore succeeded (%s source and restored rows)\n' "$service" "$source_count"
done
"${compose[@]}" exec -T identity cat /data/signing-key.json > "$backup_dir/dev-signing-key.json"
# A private runtime-settings copy is required to read encrypted session records after a local restore.
cp .local.env "$backup_dir/settings.env"
printf 'Private backup files retained at %s\n' "$backup_dir"
