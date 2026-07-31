#!/usr/bin/env bash
set -euo pipefail

market="${1:-all}"
shift || true
if [[ "${market}" != "cn" && "${market}" != "hk" && "${market}" != "all" ]]; then
  echo "market must be cn, hk, or all" >&2
  exit 2
fi

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/run_cli_job.sh"
run_cli_job "stock_${market}" "stock" "$@" --market "${market}"
