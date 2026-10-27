#!/usr/bin/env bash
# SessionStart: print the spec board and the claims into context (stdout is added as context).
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0
[ -x specs/_kit/sdd ] || exit 0
echo "Spec board (specs/_kit/sdd status):"
specs/_kit/sdd status 2>&1
echo
echo "Claims (specs/_kit/sdd claims):"
specs/_kit/sdd claims 2>&1
exit 0
