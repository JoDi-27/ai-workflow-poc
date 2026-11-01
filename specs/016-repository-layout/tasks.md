# 016 · Tasks: Repository layout

## Tasks

- [x] **T1** Create `engine/`, `mcp-gateway/`, `analysis/`, `ui/`, `runner/`, `driver-kits/`, `driver-kits/claude-code/`, `driver-kits/claude-code/collector/`, and `definitions/`, each with a README in the plan's shape (with `Filled by:` lines); add `deploy/README.md` and `scripts/README.md` · new folders, `deploy/`, `scripts/` · check: `for d in engine mcp-gateway analysis ui runner driver-kits driver-kits/claude-code driver-kits/claude-code/collector definitions; do f=$d/README.md; test -f $f && grep -q 'docs/' $f && grep -q '^Filled by: ' $f || { echo "$f incomplete"; exit 1; }; for p in $(grep -o 'docs/[A-Za-z0-9_./-]*[A-Za-z0-9_/-]' $f); do test -e "$p" || { echo "$f: $p missing"; exit 1; }; done; grep '^Filled by: ' $f | sed 's|.*/ ||' | while read -r i; do grep -qF "$i" PLAN.md || { echo "$f: $i not in PLAN.md"; exit 1; }; done || exit 1; done && test -f deploy/README.md && test -f scripts/README.md && grep -q 'one place' driver-kits/claude-code/collector/README.md && test -z "$(find engine mcp-gateway analysis ui runner driver-kits definitions -type f ! -name README.md)"` → AC-1, AC-2, AC-3, AC-6
- [x] **T2** Update the root `README.md` "Read first" table, `CLAUDE.md` (Current state names every new folder; Where things live), `PLAN.md` (Repository layout item), and the collector sentences in `docs/architecture/README.md` and `docs/local-setup/README.md` · those five files · check: `for d in engine mcp-gateway analysis ui runner driver-kits definitions; do grep -q "| .$d/. |" README.md && grep -q "$d/" CLAUDE.md || { echo "$d missing"; exit 1; }; done && grep -q 'Repository layout' PLAN.md && ! grep 'Repository layout' PLAN.md | grep -q 'for example' && grep -q 'driver-kits/claude-code/collector/' docs/architecture/README.md && grep -q 'driver-kits/claude-code/collector/' docs/local-setup/README.md` → AC-3, AC-4, AC-5
- [x] **T3** Add `layout` to the self-test's clean-copy loop and the five layout cases (failing first), then add the `layout` check to `repo-checks.sh`, its no-argument run, and its usage comment and message · `.github/scripts/repo-checks.sh`, `.github/scripts/test-repo-checks.sh` · check: `.github/scripts/test-repo-checks.sh && .github/scripts/repo-checks.sh` → AC-4, AC-8
- [x] **T4** Add the `Layout is documented` step to the `repo-checks` job, with `if: ${{ !cancelled() }}`; add the line on repository checks to `CONTRIBUTING.md` "Adding a CI check" · `.github/workflows/ci.yml`, `CONTRIBUTING.md` · check: `grep -q 'repo-checks.sh layout' .github/workflows/ci.yml && grep -q 'repo-checks.sh' CONTRIBUTING.md && .github/scripts/apply-ruleset.sh --verify` → AC-9
- [x] **T5** Push the branch, open the pull request (saying CI gets stricter), confirm every required check passes, and record the run log lines showing the layout step in Evidence · PR · check: `gh pr checks --watch --required` → AC-7, AC-8, AC-9

- [x] **T6** Add self-test cases for the layout check inside a git work tree (clean passes, an ignored folder passes, an undocumented folder fails), from the verify review · `.github/scripts/test-repo-checks.sh` · check: `.github/scripts/test-repo-checks.sh` → AC-8

## Verification

- [x] **V1** Every acceptance criterion verified, with evidence below
- [x] **V2** PLAN.md and BACKLOG.md updated for this spec

## Evidence

| Acceptance criterion | Result | Evidence (test, command output, manual check) |
| --- | --- | --- |
| AC-1 | pass | T1 check exit 0; `test -d` on `engine/ mcp-gateway/ analysis/ ui/ runner/ driver-kits/ driver-kits/claude-code/ definitions/ deploy/ docs/` all present; `deploy/` and `docs/` in place (`deploy/` gained only its README). Reviewer confirmed |
| AC-2 | pass | T1 check exit 0: each new README names at least one `docs/` path, every path exists, every `Filled by:` item is in `PLAN.md` (nine items: Engine skeleton, UI skeleton, Runner stub, Claude Code driver kit skeleton, OTel Collector, Kubernetes manifests, Definition model, Driver API, Quality view). Reviewer checked by hand that each item sits under the named phase and section |
| AC-3 | pass | `driver-kits/claude-code/collector/README.md` line 3: "This folder is the one place for it"; T2 check: `docs/architecture/README.md` and `docs/local-setup/README.md` each name `driver-kits/claude-code/collector/` |
| AC-4 | pass | `.github/scripts/repo-checks.sh layout` prints `ok layout` on the real tree; self-test cases show it fails on a mismatch (AC-8) |
| AC-5 | pass | T2 check exit 0: `CLAUDE.md` Current state names all seven folders; `PLAN.md` line 46 lists the folders and no longer says "for example" |
| AC-6 | pass | `find engine mcp-gateway analysis ui runner driver-kits definitions -type f` lists only the nine `README.md` files |
| AC-7 | pass | PR #23 head 83fd4e0 (T6): run 41314145184, `repo-checks` pass; earlier run 41313787752 (T4) had all four steps `success`, log `Layout is documented ... ok   layout` |
| AC-8 | pass | `.github/scripts/test-repo-checks.sh`: "all cases pass", including folder without README fails, without row fails, passes once documented, row for missing folder fails, empty table fails; and in a git work tree (T6): clean passes, ignored `build/` passes, undocumented folder fails |
| AC-9 | pass | `ci.yml` line 39: step `Layout is documented` in job `repo-checks`; `apply-ruleset.sh --verify`: `ok ruleset 'main' (26310938) matches` and `ok merge settings`; `main.json` requires `repo-checks` (integration 15368), no bypass |
