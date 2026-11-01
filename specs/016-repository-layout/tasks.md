# 016 · Tasks: Repository layout

## Tasks

- [x] **T1** Create `engine/`, `mcp-gateway/`, `analysis/`, `ui/`, `runner/`, `driver-kits/`, `driver-kits/claude-code/`, `driver-kits/claude-code/collector/`, and `definitions/`, each with a README in the plan's shape (with `Filled by:` lines); add `deploy/README.md` and `scripts/README.md` · new folders, `deploy/`, `scripts/` · check: `for d in engine mcp-gateway analysis ui runner driver-kits driver-kits/claude-code driver-kits/claude-code/collector definitions; do f=$d/README.md; test -f $f && grep -q 'docs/' $f && grep -q '^Filled by: ' $f || { echo "$f incomplete"; exit 1; }; for p in $(grep -o 'docs/[A-Za-z0-9_./-]*[A-Za-z0-9_/-]' $f); do test -e "$p" || { echo "$f: $p missing"; exit 1; }; done; grep '^Filled by: ' $f | sed 's|.*/ ||' | while read -r i; do grep -qF "$i" PLAN.md || { echo "$f: $i not in PLAN.md"; exit 1; }; done || exit 1; done && test -f deploy/README.md && test -f scripts/README.md && grep -q 'one place' driver-kits/claude-code/collector/README.md && test -z "$(find engine mcp-gateway analysis ui runner driver-kits definitions -type f ! -name README.md)"` → AC-1, AC-2, AC-3, AC-6
- [x] **T2** Update the root `README.md` "Read first" table, `CLAUDE.md` (Current state names every new folder; Where things live), `PLAN.md` (Repository layout item), and the collector sentences in `docs/architecture/README.md` and `docs/local-setup/README.md` · those five files · check: `for d in engine mcp-gateway analysis ui runner driver-kits definitions; do grep -q "| .$d/. |" README.md && grep -q "$d/" CLAUDE.md || { echo "$d missing"; exit 1; }; done && grep -q 'Repository layout' PLAN.md && ! grep 'Repository layout' PLAN.md | grep -q 'for example' && grep -q 'driver-kits/claude-code/collector/' docs/architecture/README.md && grep -q 'driver-kits/claude-code/collector/' docs/local-setup/README.md` → AC-3, AC-4, AC-5
- [x] **T3** Add `layout` to the self-test's clean-copy loop and the five layout cases (failing first), then add the `layout` check to `repo-checks.sh`, its no-argument run, and its usage comment and message · `.github/scripts/repo-checks.sh`, `.github/scripts/test-repo-checks.sh` · check: `.github/scripts/test-repo-checks.sh && .github/scripts/repo-checks.sh` → AC-4, AC-8
- [x] **T4** Add the `Layout is documented` step to the `repo-checks` job, with `if: ${{ !cancelled() }}`; add the line on repository checks to `CONTRIBUTING.md` "Adding a CI check" · `.github/workflows/ci.yml`, `CONTRIBUTING.md` · check: `grep -q 'repo-checks.sh layout' .github/workflows/ci.yml && grep -q 'repo-checks.sh' CONTRIBUTING.md && .github/scripts/apply-ruleset.sh --verify` → AC-9
- [ ] **T5** Push the branch, open the pull request (saying CI gets stricter), confirm every required check passes, and record the run log lines showing the layout step in Evidence · PR · check: `gh pr checks --watch --required` → AC-7, AC-8, AC-9

## Verification

- [ ] **V1** Every acceptance criterion verified, with evidence below
- [ ] **V2** PLAN.md and BACKLOG.md updated for this spec

## Evidence

| Acceptance criterion | Result | Evidence (test, command output, manual check) |
| --- | --- | --- |
| AC-1 | | |
| AC-2 | | |
| AC-3 | | |
| AC-4 | | |
| AC-5 | | |
| AC-6 | | |
| AC-7 | | |
| AC-8 | | |
| AC-9 | | |
