#!/usr/bin/env bash
# Fails if a module has production classes but ran no tests (spec 017, plan Deviations).
# JaCoCo's check passes a module with no execution data, and PIT skips a module with no tests,
# so without this guard untested code would pass both gates. Run after `./mvnw verify`, from engine/.
set -euo pipefail
cd "$(dirname "$0")/.."
bad=""
for pom in */pom.xml; do
  m="${pom%/pom.xml}"
  # A module with no target/classes has nothing to check; `|| true` keeps pipefail from ending the script.
  classes=$(find "$m/target/classes" -name '*.class' ! -name 'package-info.class' 2>/dev/null | head -1 || true)
  [ -z "$classes" ] && continue
  if ! ls "$m"/target/surefire-reports/TEST-*.xml >/dev/null 2>&1; then
    bad="$bad $m"
  fi
done
if [ -n "$bad" ]; then
  echo "FAIL every-module-tested: production classes but no test results in:$bad" >&2
  exit 1
fi
echo "ok every-module-tested"
