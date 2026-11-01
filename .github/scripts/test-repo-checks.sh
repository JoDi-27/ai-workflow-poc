#!/usr/bin/env bash
# Self-test for repo-checks.sh: in a scratch copy of the repository, plant each defect,
# expect its check to fail, undo it, and expect the check to pass again.
# Run: .github/scripts/test-repo-checks.sh   (exit 0 when every case passes)
set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TMP=$(mktemp -d "${TMPDIR:-/tmp}/repo-checks-test.XXXXXX")
trap 'rm -rf "$TMP"' EXIT

# Copy tracked and untracked files (not ignored ones, not .git), so the copy matches the working tree.
(cd "$ROOT" && git ls-files -co --exclude-standard) | (cd "$ROOT" && tar -cf - -T -) | (cd "$TMP" && tar -xf -)
cd "$TMP" || exit 1

CHECKS=.github/scripts/repo-checks.sh
failures=0

# run_check <name> <check> <expected exit: 0 or 1>
run_check() {
  local name=$1 check=$2 want=$3 rc=0
  if [ ! -x "$CHECKS" ]; then
    echo "FAIL $name ($CHECKS is missing)"; failures=$((failures + 1)); return
  fi
  "$CHECKS" "$check" >/dev/null 2>&1 || rc=1
  if [ "$rc" = "$want" ]; then echo "ok   $name"; else echo "FAIL $name (exit $rc, wanted $want)"; failures=$((failures + 1)); fi
}

# plant <file>: keep a copy so undo can restore it.
plant() { cp "$1" "$1.orig"; }
undo() { mv "$1.orig" "$1"; }

for c in inventory status scripts layout; do run_check "clean copy passes $c" "$c" 0; done

# R3: a hand edit inside the generated part of .claude/README.md.
f=.claude/README.md; plant "$f"
sed -i.bak 's/^| `\/sdd-status` |/| `\/sdd-status` (edited by hand) |/' "$f" && rm -f "$f.bak"
run_check "hand-edited inventory fails"   inventory 1
undo "$f"; run_check "  passes once undone" inventory 0

# R4: a spec's status changed without regenerating specs/STATUS.md.
f=$(ls specs/[0-9][0-9][0-9]-*/spec.md | head -1); plant "$f"
sed -i.bak 's/^status: .*/status: blocked/' "$f" && rm -f "$f.bak"
run_check "stale STATUS.md fails"         status 1
undo "$f"; run_check "  passes once undone" status 0

# R4: a spec with an unknown status, with STATUS.md regenerated so only the warning is left.
plant "$f"; plant specs/STATUS.md
sed -i.bak 's/^status: .*/status: bogus/' "$f" && rm -f "$f.bak"
specs/_kit/sdd status --write >/dev/null 2>&1
run_check "unknown spec status fails"     status 1
undo "$f"; undo specs/STATUS.md; run_check "  passes once undone" status 0

# R4 (fail closed): sdd fails without a word and leaves STATUS.md as it was.
f=specs/_kit/sdd; plant "$f"
printf '#!/usr/bin/env bash\nexit 1\n' > "$f"
run_check "silently failing sdd fails"    status 1
undo "$f"; run_check "  passes once undone" status 0

# R5: a syntax error in a hook script.
f=.claude/hooks/stop-claims.sh; plant "$f"
printf 'if then fi\n' >> "$f"
run_check "hook syntax error fails"       scripts 1
undo "$f"; run_check "  passes once undone" scripts 0

# R8: a top-level folder with no README, then a README but no "Read first" row, then the row.
mkdir newpart && echo x > newpart/file.txt; plant README.md
run_check "folder without README fails"   layout 1
echo "# newpart" > newpart/README.md
run_check "folder without row fails"      layout 1
sed -i.bak 's/^| `docs\/` |/| `newpart\/` | Test folder |\
| `docs\/` |/' README.md && rm -f README.md.bak
run_check "  passes once documented"      layout 0
undo README.md; rm -rf newpart

# R8: a "Read first" row for a folder that does not exist.
plant README.md
sed -i.bak 's/^| `docs\/` |/| `missing\/` | Gone |\
| `docs\/` |/' README.md && rm -f README.md.bak
run_check "row for missing folder fails"  layout 1
undo README.md; run_check "  passes once undone" layout 0

# R8 (fail closed): a "Read first" section with no rows.
plant README.md
sed -i.bak '/^## Read first/,/^## Stack/{/^| `/d;}' README.md && rm -f README.md.bak
run_check "empty Read first table fails"  layout 1
undo README.md; run_check "  passes once undone" layout 0

[ "$failures" -eq 0 ] && echo "all cases pass" || echo "$failures case(s) failed"
[ "$failures" -eq 0 ]
