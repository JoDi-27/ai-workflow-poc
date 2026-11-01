#!/usr/bin/env bash
# Repository checks, run by CI (.github/workflows/ci.yml) and runnable locally.
# Usage: .github/scripts/repo-checks.sh [inventory|status|scripts]   (no argument: all three)
# Prints "ok <check>" or "FAIL <check>: <how to fix>" per check; exits 1 if any failed.
set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1
failed=0

ok() { echo "ok   $1"; }
fail() { echo "FAIL $1: $2"; failed=1; }

# R3: the generated part of .claude/README.md matches .claude/bom.py.
check_inventory() {
  if python3 .claude/bom.py --check >/dev/null 2>&1; then ok inventory
  else fail inventory ".claude/README.md is out of date; run .claude/bom.py"
  fi
}

# R4: specs/STATUS.md matches the board, and the board has no warnings.
# Writes the board, compares, and puts the committed file back, so a local run changes nothing.
check_status() {
  local saved warnings
  saved=$(mktemp "${TMPDIR:-/tmp}/status.XXXXXX")
  cp specs/STATUS.md "$saved" 2>/dev/null || : > "$saved"
  warnings=$(specs/_kit/sdd status --write 2>&1 >/dev/null)
  if [ -n "$warnings" ]; then
    fail status "the spec board reports: $warnings"
  elif ! cmp -s "$saved" specs/STATUS.md; then
    fail status "specs/STATUS.md is out of date; run specs/_kit/sdd status --write"
  else
    ok status
  fi
  cp "$saved" specs/STATUS.md; rm -f "$saved"
}

# R5: every bash or sh script in the kit, the hooks, and CI parses.
check_scripts() {
  local f bad=""
  for f in specs/_kit/* .claude/hooks/* .githooks/* .github/scripts/*; do
    [ -f "$f" ] || continue
    case $(head -n 1 "$f") in
      '#!'*bash*|'#!'*/sh|'#!'*' sh') bash -n "$f" 2>/dev/null || bad="$bad $f" ;;
    esac
  done
  if [ -z "$bad" ]; then ok scripts; else fail scripts "syntax errors in:$bad (see bash -n <file>)"; fi
}

case "${1:-all}" in
  inventory) check_inventory ;;
  status) check_status ;;
  scripts) check_scripts ;;
  all) check_inventory; check_status; check_scripts ;;
  *) echo "usage: $0 [inventory|status|scripts]" >&2; exit 2 ;;
esac
exit $failed
