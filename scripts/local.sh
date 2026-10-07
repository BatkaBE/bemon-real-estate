#!/usr/bin/env bash
# Manage an isolated local backend stack without deleting its database volumes.
set -euo pipefail
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
settings_file="$project_root/.local.env"

initialize_settings() {
  command -v openssl >/dev/null || { echo "openssl is required to generate local credentials" >&2; exit 1; }
  umask 077
  # New service settings are appended once; existing credentials and volumes are retained.
  if [[ -e "$settings_file" ]]; then
    # Append new settings without rotating credentials for databases/accounts already in use.
    if ! grep -q '^WEB_SESSION_SECRET=' "$settings_file"; then
      printf 'WEB_SESSION_SECRET=%s\n' "$(openssl rand -hex 32)" >> "$settings_file"
    fi
    if ! grep -q '^DEV_AGENT_EMAIL=' "$settings_file"; then
      printf 'DEV_AGENT_EMAIL=agent@gerhub.local\n' >> "$settings_file"
      printf "DEV_AGENT_PASSWORD='Agent#%s'\n" "$(openssl rand -hex 16)" >> "$settings_file"
    fi
    for setting in INTERNAL_SERVICE_KEY SEARCH_DB_PASSWORD PAYMENT_DB_PASSWORD OAUTH_MOBILE_CLIENT_SECRET; do
      if ! grep -q "^${setting}=" "$settings_file"; then
        printf '%s=%s\n' "$setting" "$(openssl rand -hex 32)" >> "$settings_file"
      fi
    done
    return
  fi
  local settings_temp
  settings_temp="$(mktemp "$project_root/.local.env.tmp.XXXXXX")"
  {
    printf 'IDENTITY_DB_PASSWORD=%s\n' "$(openssl rand -hex 24)"
    printf 'LISTING_DB_PASSWORD=%s\n' "$(openssl rand -hex 24)"
    for setting in INTERNAL_SERVICE_KEY SEARCH_DB_PASSWORD PAYMENT_DB_PASSWORD OAUTH_MOBILE_CLIENT_SECRET; do
      printf '%s=%s\n' "$setting" "$(openssl rand -hex 32)"
    done
    printf 'OAUTH_WEB_CLIENT_SECRET=%s\n' "$(openssl rand -hex 32)"
    printf 'DEV_ADMIN_EMAIL=admin@gerhub.local\n'
    printf "DEV_ADMIN_PASSWORD='Local#%s'\n" "$(openssl rand -hex 16)"
    printf 'WEB_SESSION_SECRET=%s\n' "$(openssl rand -hex 32)"
    printf 'DEV_AGENT_EMAIL=agent@gerhub.local\n'
    printf "DEV_AGENT_PASSWORD='Agent#%s'\n" "$(openssl rand -hex 16)"
    printf 'BEMON_IDENTITY_PORT=9000\nBEMON_LISTING_PORT=8080\nBEMON_SEARCH_PORT=8001\n'
  } > "$settings_temp"
  mv -n "$settings_temp" "$settings_file"
  echo "Generated private local settings in .local.env; retain this file with the database volumes."
}

compose() {
  docker compose --project-name "${BEMON_COMPOSE_PROJECT_NAME:-bemon-local}" \
    --env-file "$settings_file" -f "$project_root/domain-platform-infra/compose.local.yaml" "$@"
}

case "${1:-help}" in
  init)
    initialize_settings
    ;;
  build)
    cd "$project_root"
    mvn -B package -DskipTests
    ;;
  up)
    initialize_settings
    for service in identity listing payment; do
      if [[ ! -f "$project_root/domain-$service-service/target/$service-service-0.1.0-SNAPSHOT.jar" ]]; then
        echo "Build the services first: bash scripts/local.sh build" >&2
        exit 1
      fi
    done
    compose up --build --detach --wait --wait-timeout 240
    ;;
  status)
    compose ps
    ;;
  logs)
    compose logs --tail 100 "${@:2}"
    ;;
  stop)
    compose stop
    ;;
  smoke)
    command -v node >/dev/null || { echo "Node.js 20.6+ is required for the smoke check" >&2; exit 1; }
    node --env-file="$settings_file" "$project_root/scripts/smoke.mjs"
    ;;
  seed)
    initialize_settings
    node --env-file="$settings_file" "$project_root/scripts/seed-local.mjs"
    ;;
  *)
    echo "Usage: bash scripts/local.sh {init|build|up|status|logs [service]|stop|smoke|seed}"
    ;;
esac
