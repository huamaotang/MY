#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
mkdir -p logs

export DB_HOST="${DB_HOST:-127.0.0.1}"
export DB_PORT="${DB_PORT:-3306}"
export DB_USER="${DB_USER:-root}"
export DB_NAME="${DB_NAME:-fund}"

export REQUEST_MIN_DELAY_SECONDS="${REQUEST_MIN_DELAY_SECONDS:-1.0}"
export REQUEST_MAX_DELAY_SECONDS="${REQUEST_MAX_DELAY_SECONDS:-2.0}"
export REQUEST_TIMEOUT_SECONDS="${REQUEST_TIMEOUT_SECONDS:-10}"
export REQUEST_MAX_RETRIES="${REQUEST_MAX_RETRIES:-3}"

export CRAWL_PROFILE="${CRAWL_PROFILE:-1}"
export CRAWL_NAV="${CRAWL_NAV:-1}"
export NAV_PAGE_SIZE="${NAV_PAGE_SIZE:-200}"
export NAV_START_PAGE="${NAV_START_PAGE:-1}"

timestamp="$(date +%Y%m%d_%H%M%S)"
log_file="logs/crawl_all_funds_${timestamp}.log"

echo "log_file=${log_file}"
echo "started_at=$(date '+%Y-%m-%d %H:%M:%S')"

.venv/bin/python crawl_all_funds.py 2>&1 | tee "${log_file}"
