#!/usr/bin/env bash
set -euo pipefail

bin_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "${bin_dir}/.." && pwd)"
cd "${project_dir}"
mkdir -p logs

weekday="$(date +%u)"
clock="$(date +%H%M)"
clock_number=$((10#${clock}))
if [[ "${weekday}" -gt 5 ]]; then
  exit 0
fi

cn_market_open=0
hk_market_open=0
if { [[ "${clock_number}" -ge 915 && "${clock_number}" -le 1135 ]] ||
     [[ "${clock_number}" -ge 1255 && "${clock_number}" -le 1510 ]] ||
     [[ "${clock_number}" -ge 1515 && "${clock_number}" -le 1530 ]]; }; then
  cn_market_open=1
fi
if { [[ "${clock_number}" -ge 925 && "${clock_number}" -le 1205 ]] ||
     [[ "${clock_number}" -ge 1255 && "${clock_number}" -le 1610 ]]; }; then
  hk_market_open=1
fi
if [[ "${cn_market_open}" -eq 0 && "${hk_market_open}" -eq 0 ]]; then
  exit 0
fi

lock_dir="logs/stock-market.lock"
if ! mkdir "${lock_dir}" 2>/dev/null; then
  exit 0
fi
trap 'rmdir "${lock_dir}" 2>/dev/null || true' EXIT

crawl_status=0
if [[ "${cn_market_open}" -eq 1 ]]; then
  .venv/bin/python -u cli.py stock --market cn >> "logs/stock-market-$(date +%Y%m%d).log" 2>&1 || crawl_status=$?
fi
if [[ "${hk_market_open}" -eq 1 ]]; then
  .venv/bin/python -u cli.py stock --market hk >> "logs/stock-market-$(date +%Y%m%d).log" 2>&1 || crawl_status=$?
fi
exit "${crawl_status}"
