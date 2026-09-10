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

if [[ -f "${project_dir}/.env" ]]; then
  "${project_dir}/.venv/bin/python" -c "
import os
with open('${project_dir}/.env') as f:
    for line in f:
        line = line.strip()
        if line and '=' in line and not line.startswith('#'):
            key, value = line.split('=', 1)
            os.environ.setdefault(key.strip(), value.strip().strip('\"').strip(\"'\"))
"
fi

export CRM_FUND_SPIDER_DIR="${CRM_FUND_SPIDER_DIR:-${project_dir}}"
export CRM_LOG_ROOT="${CRM_LOG_ROOT:-${repository_dir}/logs}"
export PREFECT_HOME="${PREFECT_HOME:-${project_dir}/.prefect}"
export PREFECT_API_URL="${PREFECT_API_URL:-http://127.0.0.1:4200/api}"
# macOS system proxy (e.g. 127.0.0.1:7890) can hijack httpx/urllib requests for
# localhost when NO_PROXY is unset, turning every localhost API call into a
# 502. launchd does not inherit the user shell's NO_PROXY, so set it here.
export NO_PROXY="${NO_PROXY:-127.0.0.1,localhost,::1}"
export no_proxy="${no_proxy:-127.0.0.1,localhost,::1}"

mkdir -p \
  "${PREFECT_HOME}" \
  "${CRM_LOG_ROOT}/jobs" \
  "${CRM_LOG_ROOT}/locks/fund-spider" \
  "${CRM_LOG_ROOT}/services/prefect-worker"
cd "${project_dir}"

exec "${prefect_executable}" worker start \
  --pool "${PREFECT_WORK_POOL:-crm-process-pool}" \
  --type process \
  --name "${PREFECT_WORKER_NAME:-crm-worker}" \
  --limit "${PREFECT_WORKER_LIMIT:-4}" \
  --install-policy never
