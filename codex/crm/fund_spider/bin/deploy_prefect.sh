#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "${script_dir}/.." && pwd)"
repository_dir="$(cd "${project_dir}/.." && pwd)"
prefect_executable="${PREFECT_EXECUTABLE:-${project_dir}/.venv/bin/prefect}"
work_pool="${PREFECT_WORK_POOL:-crm-process-pool}"

if [[ ! -x "${prefect_executable}" ]]; then
  echo "Prefect executable not found: ${prefect_executable}" >&2
  exit 1
fi

export CRM_FUND_SPIDER_DIR="${CRM_FUND_SPIDER_DIR:-${project_dir}}"
export CRM_LOG_ROOT="${CRM_LOG_ROOT:-${repository_dir}/logs}"
export PREFECT_HOME="${PREFECT_HOME:-${project_dir}/.prefect}"
export PREFECT_API_URL="${PREFECT_API_URL:-http://127.0.0.1:4200/api}"

mkdir -p \
  "${PREFECT_HOME}" \
  "${CRM_LOG_ROOT}/jobs" \
  "${CRM_LOG_ROOT}/locks/fund-spider" \
  "${CRM_LOG_ROOT}/pids/fund-spider" \
  "${CRM_LOG_ROOT}/services/prefect-server" \
  "${CRM_LOG_ROOT}/services/prefect-worker"
cd "${project_dir}"

if ! "${prefect_executable}" work-pool inspect "${work_pool}" >/dev/null 2>&1; then
  "${prefect_executable}" work-pool create \
    --type process \
    --set-as-default \
    "${work_pool}"
fi

if ! "${prefect_executable}" work-queue inspect realtime --pool "${work_pool}" >/dev/null 2>&1; then
  "${prefect_executable}" work-queue create realtime \
    --pool "${work_pool}" \
    --priority 1
fi

if ! "${prefect_executable}" work-queue inspect batch --pool "${work_pool}" >/dev/null 2>&1; then
  "${prefect_executable}" work-queue create batch \
    --pool "${work_pool}" \
    --priority 10
fi

exec "${prefect_executable}" deploy \
  --all \
  --prefect-file "${project_dir}/prefect.yaml" \
  --no-prompt
