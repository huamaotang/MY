#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "${script_dir}/.." && pwd)"
prefect_executable="${PREFECT_EXECUTABLE:-${project_dir}/.venv/bin/prefect}"

if [[ ! -x "${prefect_executable}" ]]; then
  echo "Prefect executable not found: ${prefect_executable}" >&2
  exit 1
fi

export CRM_FUND_SPIDER_DIR="${CRM_FUND_SPIDER_DIR:-${project_dir}}"
export PREFECT_HOME="${PREFECT_HOME:-${project_dir}/.prefect}"
export PREFECT_API_URL="${PREFECT_API_URL:-http://127.0.0.1:4200/api}"

mkdir -p "${PREFECT_HOME}" "${project_dir}/logs"
cd "${project_dir}"

exec "${prefect_executable}" worker start \
  --pool "${PREFECT_WORK_POOL:-crm-process-pool}" \
  --type process \
  --name "${PREFECT_WORKER_NAME:-crm-worker}" \
  --limit "${PREFECT_WORKER_LIMIT:-4}" \
  --install-policy never
