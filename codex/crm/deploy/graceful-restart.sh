#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  deploy/graceful-restart.sh <service> <jar_path> <port>

Example:
  deploy/graceful-restart.sh system backend/system/target/system-0.1.0.jar 8782

Environment:
  JAVA_BIN       Java command. Default: java
  JAVA_OPTS      Extra JVM options.
  ACTUATOR_HOST  Host used for local actuator calls. Default: 127.0.0.1
  ACTUATOR_BASE  Full actuator base URL. Default: http://ACTUATOR_HOST:port/actuator
  DRAIN_SECONDS  Wait time after marking DOWN. Default: 10
  STOP_TIMEOUT   Wait time for graceful process exit. Default: 45
  LOG_ROOT       Root for service logs. Default: <repository>/logs/services
  LOG_DIR        Override directory for this service.
  LOG_PATH       Override Spring Boot file-appender directory.
USAGE
}

if [ "$#" -ne 3 ]; then
  usage
  exit 2
fi

SERVICE="$1"
JAR_PATH="$2"
PORT="$3"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

JAVA_BIN="${JAVA_BIN:-java}"
JAVA_OPTS="${JAVA_OPTS:-}"
ACTUATOR_HOST="${ACTUATOR_HOST:-127.0.0.1}"
DRAIN_SECONDS="${DRAIN_SECONDS:-10}"
STOP_TIMEOUT="${STOP_TIMEOUT:-45}"
LOG_ROOT="${LOG_ROOT:-${REPOSITORY_DIR}/logs/services}"
SERVICE_LOG_DIR="${LOG_DIR:-${LOG_ROOT}/${SERVICE}}"
APP_LOG_PATH="${LOG_PATH:-${SERVICE_LOG_DIR}}"
ACTUATOR_BASE="${ACTUATOR_BASE:-http://${ACTUATOR_HOST}:${PORT}/actuator}"
JAR_NAME="$(basename "${JAR_PATH}")"

mkdir -p "${SERVICE_LOG_DIR}"

find_pid() {
  pgrep -f "java .*${JAR_NAME}" || true
}

mark_out_of_service() {
  if ! curl -fsS "${ACTUATOR_BASE}/health" >/dev/null 2>&1; then
    echo "Actuator is not reachable for ${SERVICE} on ${ACTUATOR_BASE}; skipping drain."
    return 0
  fi

  echo "Marking ${SERVICE} DOWN..."
  curl -fsS -X POST "${ACTUATOR_BASE}/serviceregistry?status=DOWN" \
      -H 'Content-Type: application/json' \
      -d '{}' >/dev/null 2>&1 || {
    echo "Could not update serviceregistry status for ${SERVICE}; continuing with SIGTERM."
  }
}

stop_existing() {
  local pid
  pid="$(find_pid | head -n 1)"
  if [ -z "${pid}" ]; then
    echo "No running process found for ${JAR_PATH}."
    return 0
  fi

  mark_out_of_service
  echo "Waiting ${DRAIN_SECONDS}s for traffic drain..."
  sleep "${DRAIN_SECONDS}"

  echo "Sending SIGTERM to ${SERVICE} pid=${pid}..."
  kill -TERM "${pid}"

  local waited=0
  while kill -0 "${pid}" >/dev/null 2>&1; do
    if [ "${waited}" -ge "${STOP_TIMEOUT}" ]; then
      echo "Process ${pid} did not stop within ${STOP_TIMEOUT}s; sending SIGKILL."
      kill -KILL "${pid}"
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done
}

start_new() {
  if [ ! -f "${JAR_PATH}" ]; then
    echo "Jar not found: ${JAR_PATH}" >&2
    exit 1
  fi

  local log_file="${SERVICE_LOG_DIR}/console.log"
  echo "Starting ${SERVICE} from ${JAR_PATH}..."
  LOG_PATH="${APP_LOG_PATH}" nohup "${JAVA_BIN}" ${JAVA_OPTS} -jar "${JAR_PATH}" >>"${log_file}" 2>&1 &
  local pid="$!"
  echo "Started ${SERVICE} pid=${pid}, log=${log_file}"
}

stop_existing
start_new
