#!/usr/bin/env bash
set -euo pipefail

BASE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="$BASE_DIR/run-logs"
PID_DIR="$BASE_DIR/run-pids"

START_PROXY="${START_PROXY:-1}"
PROXY_CONNECTION_NAME="${PROXY_CONNECTION_NAME:-sistemafinancierodistribuido:us-central1:sfd-db}"
PROXY_PORT="${PROXY_PORT:-5432}"

DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-sfd}"
DB_URL="${DB_URL:-jdbc:postgresql://127.0.0.1:${DB_PORT}/${DB_NAME}}"
DB_USER="${DB_USER:-app_user}"
DB_PASS="${DB_PASS:-123456789}"

mkdir -p "$LOG_DIR" "$PID_DIR"

usage() {
  cat <<'EOF'
Usage: ./run-services.sh [start|stop|status]

Environment variables:
  START_PROXY=1|0
  PROXY_CONNECTION_NAME=project:region:instance
  PROXY_PORT=5432
  DB_URL=jdbc:postgresql://127.0.0.1:5432/sfd
  DB_USER=app_user
  DB_PASS=123456789
EOF
}

is_port_listening() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    if ss -ltn 2>/dev/null | awk '{print $4}' | grep -Eq ":${port}$"; then
      return 0
    fi
  elif command -v lsof >/dev/null 2>&1; then
    if lsof -iTCP -sTCP:LISTEN -P -n 2>/dev/null | grep -Eq ":${port}\\b"; then
      return 0
    fi
  fi
  return 1
}

find_jar() {
  local dir="$1"
  local jar=""
  local candidate

  shopt -s nullglob
  for candidate in "$dir"/target/*.jar; do
    case "$candidate" in
      *original*.jar|*sources.jar|*javadoc.jar) continue ;;
      *) jar="$candidate"; break ;;
    esac
  done
  shopt -u nullglob

  if [ -n "$jar" ]; then
    echo "$jar"
    return 0
  fi
  return 1
}

pid_is_running() {
  local pid="$1"
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
    return 0
  fi
  return 1
}

start_proxy() {
  local pid_file="$PID_DIR/cloud-sql-proxy.pid"
  local log_file="$LOG_DIR/cloud-sql-proxy.log"

  if [ -f "$pid_file" ] && pid_is_running "$(cat "$pid_file")"; then
    echo "Proxy already running (pid $(cat "$pid_file"))."
    return 0
  fi

  if is_port_listening "$PROXY_PORT"; then
    echo "Port $PROXY_PORT already in use. Skipping proxy start."
    return 0
  fi

  if [ ! -x "$BASE_DIR/cloud-sql-proxy" ]; then
    echo "cloud-sql-proxy not found at $BASE_DIR/cloud-sql-proxy"
    return 1
  fi

  echo "Starting Cloud SQL Proxy on port $PROXY_PORT..."
  nohup "$BASE_DIR/cloud-sql-proxy" --port "$PROXY_PORT" "$PROXY_CONNECTION_NAME" \
    > "$log_file" 2>&1 & echo $! > "$pid_file"
}

start_service() {
  local name="$1"
  local dir="$2"
  local pid_file="$PID_DIR/$name.pid"
  local log_file="$LOG_DIR/$name.log"
  local jar

  if [ -f "$pid_file" ] && pid_is_running "$(cat "$pid_file")"; then
    echo "$name already running (pid $(cat "$pid_file"))."
    return 0
  fi

  if ! jar="$(find_jar "$dir")"; then
    echo "Jar not found for $name in $dir/target. Run ./start-all.sh first."
    return 1
  fi

  echo "Starting $name..."
  (cd "$dir" && nohup env \
    "SPRING_DATASOURCE_URL=$DB_URL" \
    "SPRING_DATASOURCE_USERNAME=$DB_USER" \
    "SPRING_DATASOURCE_PASSWORD=$DB_PASS" \
    java -jar "$jar" > "$log_file" 2>&1 & echo $! > "$pid_file")
}

stop_service() {
  local name="$1"
  local pid_file="$PID_DIR/$name.pid"
  local pid=""

  if [ ! -f "$pid_file" ]; then
    echo "$name not running (no pid)."
    return 0
  fi

  pid="$(cat "$pid_file")"
  if pid_is_running "$pid"; then
    echo "Stopping $name (pid $pid)..."
    kill "$pid" || true
  fi
  rm -f "$pid_file"
}

status_service() {
  local name="$1"
  local pid_file="$PID_DIR/$name.pid"
  local pid=""

  if [ -f "$pid_file" ]; then
    pid="$(cat "$pid_file")"
    if pid_is_running "$pid"; then
      echo "$name: running (pid $pid)"
      return 0
    fi
  fi
  echo "$name: stopped"
}

start_all() {
  if [ "$START_PROXY" = "1" ]; then
    start_proxy || true
  fi

  start_service "auth-service" "$BASE_DIR/auth-service"
  start_service "account-service" "$BASE_DIR/account-service"
  start_service "transaction-service" "$BASE_DIR/transaction-service"
  start_service "audit-service" "$BASE_DIR/audit-service"
  start_service "report-service" "$BASE_DIR/report-service/report-service"
  start_service "web-interface" "$BASE_DIR/web-interface"

  echo ""
  echo "Logs: $LOG_DIR"
  echo "UI: http://localhost:8085/index.html"
  echo "Admin: http://localhost:8085/admin.html"
}

stop_all() {
  stop_service "web-interface"
  stop_service "report-service"
  stop_service "audit-service"
  stop_service "transaction-service"
  stop_service "account-service"
  stop_service "auth-service"
  stop_service "cloud-sql-proxy"
}

status_all() {
  status_service "cloud-sql-proxy"
  status_service "auth-service"
  status_service "account-service"
  status_service "transaction-service"
  status_service "audit-service"
  status_service "report-service"
  status_service "web-interface"
}

case "${1:-start}" in
  start) start_all ;;
  stop) stop_all ;;
  status) status_all ;;
  *) usage; exit 1 ;;
esac
