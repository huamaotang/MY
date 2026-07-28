#!/usr/bin/env bash

run_cli_job() {
  local job_name="$1"
  local cli_command="$2"
  shift 2

  local foreground=0
  if [[ "${1:-}" == "--foreground" ]]; then
    foreground=1
    shift
  fi

  local bin_dir
  bin_dir="$(cd "$(dirname "${BASH_SOURCE[1]}")" && pwd)"
  local project_dir
  project_dir="$(cd "${bin_dir}/.." && pwd)"
  local python_executable="${PYTHON_EXECUTABLE:-${project_dir}/.venv/bin/python}"
  if [[ ! -x "${python_executable}" ]]; then
    python_executable="${PYTHON_FALLBACK:-python3}"
  fi

  local log_dir="${project_dir}/logs"
  mkdir -p "${log_dir}"
  cd "${project_dir}"

  if (( foreground == 1 )); then
    exec "${python_executable}" cli.py "${cli_command}" "$@"
  fi

  local timestamp
  timestamp="$(date '+%Y%m%d_%H%M%S')"
  local log_file="${log_dir}/${job_name}_${timestamp}.log"
  local pid_file="${log_dir}/${job_name}.pid"

  if [[ -f "${pid_file}" ]]; then
    local existing_pid
    existing_pid="$(<"${pid_file}")"
    if [[ -n "${existing_pid}" ]] && kill -0 "${existing_pid}" 2>/dev/null; then
      echo "${job_name} is already running, pid=${existing_pid}"
      echo "pid_file=${pid_file}"
      exit 1
    fi
  fi

  nohup "${python_executable}" -u cli.py "${cli_command}" "$@" \
    >>"${log_file}" 2>&1 < /dev/null &
  local pid="$!"
  echo "${pid}" >"${pid_file}"

  echo "started job=${job_name} pid=${pid}"
  echo "log_file=${log_file}"
  echo "pid_file=${pid_file}"
}
