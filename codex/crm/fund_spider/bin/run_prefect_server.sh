#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "${script_dir}/.." && pwd)"
repository_dir="$(cd "${project_dir}/.." && pwd)"
prefect_executable="${PREFECT_EXECUTABLE:-${project_dir}/.venv/bin/prefect}"

if [[ ! -x "${prefect_executable}" ]]; then
  echo "Prefect executable not found: ${prefect_executable}" >&2
  exit 1
fi

export CRM_FUND_SPIDER_DIR="${CRM_FUND_SPIDER_DIR:-${project_dir}}"
export CRM_LOG_ROOT="${CRM_LOG_ROOT:-${repository_dir}/logs}"
export PREFECT_HOME="${PREFECT_HOME:-${project_dir}/.prefect}"
export PREFECT_SERVER_ANALYTICS_ENABLED="${PREFECT_SERVER_ANALYTICS_ENABLED:-false}"
export PREFECT_SERVER_UI_SHOW_PROMOTIONAL_CONTENT="${PREFECT_SERVER_UI_SHOW_PROMOTIONAL_CONTENT:-false}"
export PREFECT_SERVER_DATABASE_CONNECTION_URL="${PREFECT_SERVER_DATABASE_CONNECTION_URL:-postgresql+asyncpg://prefect:prefect@127.0.0.1:5433/prefect}"
# Prefect 3.7.x repeatedly scans recently cancelled runs every 20 seconds and
# schedules cleanup even when they have no unfinished child tasks. A large bulk
# cancellation can therefore starve API requests of database connections. Our
# flows do not leave unfinished child tasks behind, so keep this faulty monitor
# disabled until Prefect is upgraded to a release that deduplicates the work.
export PREFECT_API_SERVICES_CANCELLATION_CLEANUP_ENABLED="${PREFECT_API_SERVICES_CANCELLATION_CLEANUP_ENABLED:-false}"
# Keep enough warm PostgreSQL connections for the API and Prefect's remaining
# background services. PostgreSQL allows 100 connections on this installation.
export PREFECT_SERVER_DATABASE_SQLALCHEMY_POOL_SIZE="${PREFECT_SERVER_DATABASE_SQLALCHEMY_POOL_SIZE:-20}"
export PREFECT_SERVER_DATABASE_SQLALCHEMY_MAX_OVERFLOW="${PREFECT_SERVER_DATABASE_SQLALCHEMY_MAX_OVERFLOW:-10}"
export PREFECT_SERVER_DATABASE_SQLALCHEMY_POOL_TIMEOUT="${PREFECT_SERVER_DATABASE_SQLALCHEMY_POOL_TIMEOUT:-10}"
# The bundled event vacuum uses a separate one-connection pool and retries
# indefinitely when that pool times out, blocking the server event loop. Event
# retention is not needed for this local orchestration instance.
export PREFECT_SERVER_SERVICES_DB_VACUUM_ENABLED="${PREFECT_SERVER_SERVICES_DB_VACUUM_ENABLED:-[]}"

server_host="${PREFECT_SERVER_HOST:-127.0.0.1}"
server_port="${PREFECT_SERVER_PORT:-4200}"
export PREFECT_UI_API_URL="${PREFECT_UI_API_URL:-http://${server_host}:${server_port}/api}"

mkdir -p "${PREFECT_HOME}" "${CRM_LOG_ROOT}/services/prefect-server"
cd "${project_dir}"

exec "${prefect_executable}" server start \
  --host "${server_host}" \
  --port "${server_port}" \
  --analytics-off
