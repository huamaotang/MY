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

server_host="${PREFECT_SERVER_HOST:-127.0.0.1}"
server_port="${PREFECT_SERVER_PORT:-4200}"
export PREFECT_UI_API_URL="${PREFECT_UI_API_URL:-http://${server_host}:${server_port}/api}"

mkdir -p "${PREFECT_HOME}" "${CRM_LOG_ROOT}/services/prefect-server"
cd "${project_dir}"

exec "${prefect_executable}" server start \
  --host "${server_host}" \
  --port "${server_port}" \
  --analytics-off
