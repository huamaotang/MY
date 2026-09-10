#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "${script_dir}/.." && pwd)"

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
export PREFECT_HOME="${PREFECT_HOME:-${project_dir}/.prefect}"
export PREFECT_API_URL="${PREFECT_API_URL:-http://127.0.0.1:4200/api}"

"${project_dir}/bin/run_prefect_server.sh" &
SERVER_PID=$!

"${project_dir}/bin/run_prefect_worker.sh" &
WORKER_PID=$!

wait $SERVER_PID $WORKER_PID
