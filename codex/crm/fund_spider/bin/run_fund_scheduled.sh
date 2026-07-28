#!/usr/bin/env bash
set -u

bin_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
project_dir="$(cd "${bin_dir}/.." && pwd)"
log_dir="${project_dir}/logs"
lock_dir="${log_dir}/fund_scheduler.lock"
trigger="${1:-auto}"

mkdir -p "${log_dir}"
if ! mkdir "${lock_dir}" 2>/dev/null; then
  echo "$(date '+%Y-%m-%d %H:%M:%S') status=skipped_overlap trigger=${trigger}"
  exit 0
fi
trap 'rmdir "${lock_dir}" 2>/dev/null || true' EXIT

if [[ "${trigger}" == "auto" ]]; then
  if [[ "$(date '+%H')" == "08" ]]; then
    trigger="morning"
  else
    trigger="evening"
  fi
fi

nav_status=0
feature_status=0
timestamp="$(date '+%Y%m%d_%H%M%S')"
nav_log="${log_dir}/nav_performance_scheduled_${timestamp}.log"
feature_log="${log_dir}/feature_scheduled_${timestamp}.log"

"${bin_dir}/run_nav_performance.sh" --foreground \
  >>"${nav_log}" 2>&1 || nav_status=$?

if [[ "${trigger}" == "morning" ]]; then
  "${bin_dir}/run_feature.sh" --foreground \
    >>"${feature_log}" 2>&1 || feature_status=$?
elif [[ "${trigger}" != "evening" ]]; then
  echo "unsupported trigger: ${trigger}" >&2
  exit 2
fi

if (( nav_status != 0 || feature_status != 0 )); then
  echo "trigger=${trigger} nav_status=${nav_status} feature_status=${feature_status} nav_log=${nav_log} feature_log=${feature_log}" >&2
  exit 1
fi

echo "trigger=${trigger} status=success nav_log=${nav_log} feature_log=${feature_log}"
