#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${script_dir}/run_cli_job.sh"

foreground=0
forwarded_args=()
for argument in "$@"; do
  if [[ "${argument}" == "--foreground" ]]; then
    foreground=1
  else
    forwarded_args+=("${argument}")
  fi
done
set -- "${forwarded_args[@]}"

if [[ "${1:-}" != "" && "${1:-}" != -* ]]; then
  fund_code="$1"
  shift
  set -- --fund-code "${fund_code}" "$@"
fi

if (( foreground == 1 )); then
  set -- --foreground "$@"
fi

run_cli_job "nav_history" "nav-history" "$@"
