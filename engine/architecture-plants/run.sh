#!/usr/bin/env bash
# Proves every module rule fails when broken (spec 017 AC-2, plan "Module rules").
# For each folder here: copy its files/ into engine/, build with -Dplant=<id> (which turns on a pom
# profile of that name where a plant needs a dependency), remove the files, and require that the build
# failed with the pattern in its expect file, so it failed at the tool the design names.
# Ends with a clean build that must pass. Mutation testing and integration tests are left out to save time.
# Usage: architecture-plants/run.sh [id ...]   (default: every plant)
set -uo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
engine="$(dirname "$here")"
cd "$engine"
logs="$here/logs"   # outside target/, which clean deletes; git-ignored (logs/)
mkdir -p "$logs"
build=(./mvnw -B -ntp clean verify -Djacoco.skip=true "-Dtest=!*IntegrationTest" -Dsurefire.failIfNoSpecifiedTests=false)

ids=("$@")
if [ ${#ids[@]} -eq 0 ]; then
  for d in "$here"/*/; do [ -f "$d/expect" ] && ids+=("$(basename "$d")"); done
fi

planted=()
cleanup() {
  for f in "${planted[@]:-}"; do [ -n "$f" ] && rm -f "$f" && rmdir -p "$(dirname "$f")" 2>/dev/null; done
  planted=()
}
trap cleanup EXIT

failed=()
for id in "${ids[@]}"; do
  src="$here/$id/files"
  if [ ! -d "$src" ] || [ ! -f "$here/$id/expect" ]; then
    echo "FAIL $id: missing files/ or expect"; failed+=("$id"); continue
  fi
  while IFS= read -r f; do
    rel="${f#"$src"/}"
    if [ -e "$rel" ]; then echo "FAIL $id: $rel already exists"; cleanup; failed+=("$id"); continue 2; fi
    mkdir -p "$(dirname "$rel")"; cp "$f" "$rel"; planted+=("$rel")
  done < <(find "$src" -type f)
  "${build[@]}" "-Dplant=$id" > "$logs/$id.log" 2>&1; rc=$?
  cleanup
  if [ $rc -eq 0 ]; then
    echo "FAIL $id: the build passed (not caught; log: $logs/$id.log)"; failed+=("$id")
  elif grep -qE "$(cat "$here/$id/expect")" "$logs/$id.log"; then
    echo "ok   $id: caught ($(grep -m1 -oE "$(cat "$here/$id/expect")" "$logs/$id.log" | head -c 100))"
  else
    echo "FAIL $id: the build failed, but not at the expected tool (log: $logs/$id.log)"; failed+=("$id")
  fi
done

if "${build[@]}" > "$logs/clean.log" 2>&1; then
  echo "ok   clean build passes"
else
  echo "FAIL clean build (log: $logs/clean.log)"; failed+=("clean")
fi

if [ ${#failed[@]} -gt 0 ]; then
  echo "FAIL architecture-plants: ${failed[*]}" >&2
  exit 1
fi
echo "ok architecture-plants"
