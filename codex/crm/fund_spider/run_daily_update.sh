#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
mkdir -p logs

run_date="${RUN_DATE:-$(date +%Y%m%d)}"
log_file="${LOG_FILE:-logs/${run_date}_daily.log}"
pid_file="${PID_FILE:-logs/${run_date}_daily.pid}"

if [[ -f "${pid_file}" ]] && kill -0 "$(cat "${pid_file}")" 2>/dev/null; then
  echo "daily job already running, pid=$(cat "${pid_file}")"
  echo "log_file=${log_file}"
  exit 1
fi

export DAILY_CRAWL_FUND_LIST="${DAILY_CRAWL_FUND_LIST:-0}"
export DAILY_CRAWL_RATING="${DAILY_CRAWL_RATING:-1}"
export DAILY_USE_CURSOR="${DAILY_USE_CURSOR:-1}"
export DAILY_CURSOR_DATE="${DAILY_CURSOR_DATE:-${run_date}}"
export FUND_START_CODE="${FUND_START_CODE:-0}"
export DAILY_CURSOR_JOB_NAME="${DAILY_CURSOR_JOB_NAME:-daily_update_from_${FUND_START_CODE}}"
export NAV_PAGE_SIZE="${NAV_PAGE_SIZE:-200}"
export RATING_PAGE_SIZE="${RATING_PAGE_SIZE:-50}"
export DAILY_RATING_MAX_PAGES="${DAILY_RATING_MAX_PAGES:-1}"
export DAILY_NAV_REFRESH_DAYS="${DAILY_NAV_REFRESH_DAYS:-1}"
export DAILY_PROFILE_REFRESH_DAYS="${DAILY_PROFILE_REFRESH_DAYS:-30}"
export DAILY_FEATURE_REFRESH_DAYS="${DAILY_FEATURE_REFRESH_DAYS:-7}"
export DAILY_RATING_REFRESH_DAYS="${DAILY_RATING_REFRESH_DAYS:-7}"
export DAILY_HOLDING_REFRESH_DAYS="${DAILY_HOLDING_REFRESH_DAYS:-7}"
export REQUEST_MIN_DELAY_SECONDS="${REQUEST_MIN_DELAY_SECONDS:-1.0}"
export REQUEST_MAX_DELAY_SECONDS="${REQUEST_MAX_DELAY_SECONDS:-2.0}"

nohup .venv/bin/python -u cli.py daily \
  --refresh-fund-list "${DAILY_CRAWL_FUND_LIST}" \
  --crawl-rating "${DAILY_CRAWL_RATING}" \
  --use-cursor "${DAILY_USE_CURSOR}" \
  --cursor-date "${DAILY_CURSOR_DATE}" \
  --cursor-job-name "${DAILY_CURSOR_JOB_NAME}" \
  --fund-start-code "${FUND_START_CODE}" \
  --nav-refresh-days "${DAILY_NAV_REFRESH_DAYS}" \
  --profile-refresh-days "${DAILY_PROFILE_REFRESH_DAYS}" \
  --feature-refresh-days "${DAILY_FEATURE_REFRESH_DAYS}" \
  --rating-refresh-days "${DAILY_RATING_REFRESH_DAYS}" \
  --holding-refresh-days "${DAILY_HOLDING_REFRESH_DAYS}" \
  --rating-max-pages "${DAILY_RATING_MAX_PAGES}" \
  >> "${log_file}" 2>&1 &

pid="$!"
echo "${pid}" > "${pid_file}"

echo "started pid=${pid}"
echo "log_file=${log_file}"
echo "pid_file=${pid_file}"
