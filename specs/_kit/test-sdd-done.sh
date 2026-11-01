#!/usr/bin/env bash
# Self-test for `sdd done`, in a scratch git repository so no real spec or claim is touched.
# Run: specs/_kit/test-sdd-done.sh   (exit 0 when every case passes)
set -u

KIT="$(cd "$(dirname "$0")" && pwd)"
TMP=$(mktemp -d "${TMPDIR:-/tmp}/sdd-done-test.XXXXXX")
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/specs/900-demo"
cp -R "$KIT" "$TMP/specs/_kit"
cd "$TMP" && git init -q

cat > specs/900-demo/spec.md <<'EOF'
---
id: 900
title: "Demo"
type: feature
status: in-progress
phase: 0
depends_on: []
---
EOF
cat > specs/900-demo/tasks.md <<'EOF'
# 900 · Tasks

<!--
- [ ] **T1** inside a comment · check: `false`
-->

- [ ] **T1** Passing check · check: `test -d specs` → AC-1
- [ ] **T2** Failing check · check: `exit 3` → AC-1
- [ ] **T3** Manual · check: manual: someone looks → AC-2
- [ ] **T4** No check → AC-2
EOF

export CLAUDE_CODE_SESSION_ID=sdd-done-test
SDD=specs/_kit/sdd
failures=0

# expect <name> <expected exit: 0 or 1> <command...>
expect() {
  local name=$1 want=$2 rc=0; shift 2
  "$@" >/dev/null 2>&1 || rc=1
  if [ "$rc" = "$want" ]; then echo "ok   $name"; else echo "FAIL $name (exit $rc, wanted $want)"; failures=$((failures + 1)); fi
}
ticked() { grep -q "^- \[x\] \*\*$1\*\*" specs/900-demo/tasks.md; }
status_row() { grep '^| 900 ' specs/STATUS.md 2>/dev/null; }

expect "refuses an unclaimed task"           1 $SDD done 900 T1
$SDD claim 900 T1 >/dev/null 2>&1
expect "ticks when the check passes"         0 $SDD done 900 T1
expect "  task line is ticked"               0 ticked T1
expect "  commented-out copy is untouched"   0 grep -q '^- \[ \] \*\*T1\*\* inside a comment' specs/900-demo/tasks.md
expect "  STATUS.md is regenerated"          0 sh -c "grep '^| 900 ' specs/STATUS.md | grep -q '| 1/4 |'"
expect "refuses an already ticked task"      1 $SDD done 900 T1
$SDD claim 900 T2 >/dev/null 2>&1
expect "refuses when the check fails"        1 $SDD done 900 T2
expect "  task line stays unticked"          1 ticked T2
expect "refuses --manual on a command check" 1 $SDD done 900 T2 --manual "x"
$SDD release 900 T2 >/dev/null 2>&1
$SDD claim 900 T3 >/dev/null 2>&1
expect "refuses a manual check with no note" 1 $SDD done 900 T3
expect "ticks a manual check with a note"    0 $SDD done 900 T3 --manual "tester looked"
expect "  note is kept on the task"          0 grep -q 'checked: tester looked' specs/900-demo/tasks.md
expect "  STATUS.md is regenerated again"    0 sh -c "grep '^| 900 ' specs/STATUS.md | grep -q '| 2/4 |'"
expect "refuses an unknown task"             1 $SDD done 900 T9

[ "$failures" -eq 0 ] && echo "all cases pass" || echo "$failures case(s) failed"
[ "$failures" -eq 0 ]
