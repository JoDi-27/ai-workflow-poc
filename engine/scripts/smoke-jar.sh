#!/usr/bin/env bash
# Starts the packaged engine jar and requires health UP and status 200 (spec 017 T13).
# Tests run on the test classpath, so they miss a library missing from the jar; this does not.
# Needs a built jar (./mvnw package) and the database from compose.yaml, or AWF_DB_* set. Run from engine/.
set -euo pipefail
cd "$(dirname "$0")/.."
jar=$(ls engine-app/target/engine-app-*.jar 2>/dev/null | grep -v -- '-plain' | head -1)
[ -n "$jar" ] || { echo "FAIL smoke-jar: no engine-app jar; run ./mvnw -B -DskipTests package" >&2; exit 1; }
java="${SMOKE_JAVA:-}"
if [ -z "$java" ]; then
  if [ -n "${JAVA_HOME:-}" ]; then java="$JAVA_HOME/bin/java"
  elif [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 25 >/dev/null 2>&1; then java="$(/usr/libexec/java_home -v 25)/bin/java"
  else java=java; fi
fi
port="${SMOKE_PORT:-18080}"
log="$(mktemp)"
"$java" -jar "$jar" --server.port="$port" > "$log" 2>&1 &
pid=$!
trap 'kill "$pid" 2>/dev/null || true; wait "$pid" 2>/dev/null || true; rm -f "$log"' EXIT

health=""
for _ in $(seq 1 90); do
  if ! kill -0 "$pid" 2>/dev/null; then
    echo "FAIL smoke-jar: the engine exited during start:" >&2; tail -40 "$log" >&2; exit 1
  fi
  health=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/actuator/health" || true)
  [ "$health" = "200" ] && break
  sleep 1
done
if [ "$health" != "200" ]; then
  echo "FAIL smoke-jar: health answered '$health', not 200" >&2; tail -40 "$log" >&2; exit 1
fi
body=$(curl -s "http://localhost:$port/actuator/health")
case "$body" in *'"status":"UP"}') ;; *) echo "FAIL smoke-jar: health is not UP: $body" >&2; exit 1 ;; esac
status=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$port/v1/status?session_id=smoke")
[ "$status" = "200" ] || { echo "FAIL smoke-jar: status answered $status" >&2; exit 1; }
echo "ok smoke-jar: $(basename "$jar") starts, health UP, status 200"
