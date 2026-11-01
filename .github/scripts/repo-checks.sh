#!/usr/bin/env bash
# Repository checks, run by CI (.github/workflows/ci.yml) and runnable locally.
# Usage: .github/scripts/repo-checks.sh [inventory|status|scripts|layout]   (no argument: all four)
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
  local saved warnings rc=0
  saved=$(mktemp "${TMPDIR:-/tmp}/status.XXXXXX")
  cp specs/STATUS.md "$saved" 2>/dev/null || : > "$saved"
  warnings=$(specs/_kit/sdd status --write 2>&1 >/dev/null) || rc=$?
  if [ "$rc" -ne 0 ]; then
    fail status "specs/_kit/sdd status --write exited $rc${warnings:+: $warnings}"
  elif [ -n "$warnings" ]; then
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

# Top-level folders that are not dot folders. In a git checkout, only tracked and untracked
# files that are not ignored count, so a local build/ or node_modules/ does not; outside git
# (the self-test's scratch copy holds no ignored files), every folder counts.
top_folders() {
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    git ls-files -co --exclude-standard | cut -d/ -f1 -s | sort -u | grep -v '^\.'
  else
    local d; for d in */; do [ -d "$d" ] && echo "${d%/}"; done
  fi
}

# R8: every top-level folder has a README and a row in the root README's "Read first" table,
# and every path in that table exists. An empty or missing table fails (fail closed).
check_layout() {
  local rows d p bad=""
  rows=$(sed -n '/^## Read first/,/^## /p' README.md | grep '^| `' | sed 's/^| `\([^`]*\)`.*/\1/')
  if [ -z "$rows" ]; then fail layout "README.md has no \"Read first\" table rows"; return; fi
  for p in $rows; do [ -e "$p" ] || bad="$bad row:$p(missing)"; done
  for d in $(top_folders); do
    [ -f "$d/README.md" ] || bad="$bad $d/(no README)"
    printf '%s\n' "$rows" | grep -qx "$d/" || bad="$bad $d/(no Read first row)"
  done
  if [ -z "$bad" ]; then ok layout; else fail layout "fix in README.md or add a README:$bad"; fi
}

case "${1:-all}" in
  inventory) check_inventory ;;
  status) check_status ;;
  scripts) check_scripts ;;
  layout) check_layout ;;
  all) check_inventory; check_status; check_scripts; check_layout ;;
  *) echo "usage: $0 [inventory|status|scripts|layout]" >&2; exit 2 ;;
esac
exit $failed
