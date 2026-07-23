#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
mkdir -p logs

weekday="$(date +%u)"
clock="$(date +%H%M)"
clock_number=$((10#${clock}))
if [[ "${weekday}" -gt 5 ]]; then
  exit 0
fi

if ! { [[ "${clock_number}" -ge 915 && "${clock_number}" -le 1135 ]] ||
       [[ "${clock_number}" -ge 1255 && "${clock_number}" -le 1510 ]] ||
       [[ "${clock_number}" -ge 1515 && "${clock_number}" -le 1530 ]]; }; then
  exit 0
fi

lock_dir="logs/stock-market.lock"
if ! mkdir "${lock_dir}" 2>/dev/null; then
  exit 0
fi
trap 'rmdir "${lock_dir}" 2>/dev/null || true' EXIT

.venv/bin/python -u cli.py stock >> "logs/stock-market-$(date +%Y%m%d).log" 2>&1
