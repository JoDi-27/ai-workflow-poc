#!/usr/bin/env bash
# Plants each violation in plants/<id>/ into the tree, one at a time, runs `mvn clean verify`
# with -Dplant=<id> (which turns on any matching pom profile), records the outcome, and
# removes the planted files again. Ends with a clean build that must be green.
# Usage: ./run-trial.sh [id ...]      (default: all plants)
set -uo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME_25:-$(/usr/libexec/java_home -v 25)}"
MVN=(mvn -B -ntp -s maven-settings.xml ${MVN_OFFLINE:--o})
mkdir -p logs
ids=("$@"); [ ${#ids[@]} -eq 0 ] && ids=($(ls plants))

planted=()
cleanup() { for f in "${planted[@]:-}"; do [ -n "$f" ] && rm -f "$f"; done; planted=(); }
trap cleanup EXIT

for id in "${ids[@]}"; do
  planted=()
  while IFS= read -r f; do rel="${f#plants/$id/}"; mkdir -p "$(dirname "$rel")"; cp "$f" "$rel"; planted+=("$rel"); done \
    < <(find "plants/$id" -type f)
  "${MVN[@]}" clean verify -Dplant="$id" > "logs/$id.log" 2>&1; rc=$?
  cleanup
  if [ $rc -eq 0 ]; then echo "== $id: BUILD SUCCESS (not caught)"; continue; fi
  echo "== $id: BUILD FAILURE"
  grep -m1 -oE "Failed to execute goal [^ ]+ \([^)]*\) on project [a-z-]+" "logs/$id.log"
  grep -E "^\[ERROR\] /.*\.java|ERROR\]    [a-z]|Used undeclared|Unused declared|Architecture Violation|^Rule '|was violated|^(Class|Field|Method|Cycle|Dependencies of Slice|  [0-9]+\. Dependencies)|<poc\.engine" "logs/$id.log" \
    | grep -v "^\[ERROR\] *$" | head -8
done

"${MVN[@]}" clean verify > logs/clean.log 2>&1 && echo "== clean: BUILD SUCCESS" || { echo "== clean: BUILD FAILURE (see logs/clean.log)"; exit 1; }
