# 015 · Tasks: CI pipeline and branch protection

## Tasks

- [x] **T1** Make `sdd done` regenerate `specs/STATUS.md` after a tick, with a self-test in a scratch git repository that covers pass, fail, manual, and the regeneration · `specs/_kit/sdd`, `specs/_kit/test-sdd-done.sh` · check: `specs/_kit/test-sdd-done.sh` → AC-4
- [ ] **T2** Write the failing self-test for the repository checks. In a scratch copy it plants, one at a time, a hand edit to the generated part of `.claude/README.md`, a stale `specs/STATUS.md`, a spec with an unknown status, and a hook syntax error; it expects the matching check to fail and then pass once undone, and prints one `ok`/`FAIL` line per case · `.github/scripts/test-repo-checks.sh` · check: `test -x .github/scripts/test-repo-checks.sh && bash -n .github/scripts/test-repo-checks.sh && ! .github/scripts/test-repo-checks.sh` → AC-3, AC-4, AC-5
- [ ] **T3** Write the checks `inventory`, `status`, and `scripts` · `.github/scripts/repo-checks.sh` · check: `.github/scripts/repo-checks.sh && .github/scripts/test-repo-checks.sh` → AC-3, AC-4, AC-5
- [ ] **T4** Add the workflow: `pull_request` and `push` on `main`, `contents: read`, concurrency, job `repo-checks` with one step per check, `actions/checkout` pinned by SHA · `.github/workflows/ci.yml` · check: `grep -q 'contents: read' .github/workflows/ci.yml && grep -q '3d3c42e5aac5ba805825da76410c181273ba90b1' .github/workflows/ci.yml && ! grep -qiE 'secrets\.|anthropic|claude' .github/workflows/ci.yml` → AC-6
- [ ] **T5** Push the spec branch and open its pull request to `main`; the first `repo-checks` run passes. Fix any BSD and GNU difference in `sdd` that it shows, with a Deviations row · GitHub · check: `gh pr checks --watch --interval 15` → AC-1
- [ ] **T6** [P] Document the pull request flow and "Adding a CI check" in `CONTRIBUTING.md`; add the rule to `CLAUDE.md`, `specs/README.md`, `/sdd-implement`, and `/sdd-verify` · `CONTRIBUTING.md`, `CLAUDE.md`, `specs/README.md`, `.claude/skills/sdd-implement/SKILL.md`, `.claude/skills/sdd-verify/SKILL.md` · check: `grep -q 'Adding a CI check' CONTRIBUTING.md && for f in CONTRIBUTING.md CLAUDE.md specs/README.md .claude/skills/sdd-implement/SKILL.md .claude/skills/sdd-verify/SKILL.md; do grep -qi 'pull request' "$f" || exit 1; done` → AC-11, AC-12
- [ ] **T7** [P] Add changes to what CI enforces (`.github/`, `.claude/bom.py`, `specs/_kit/sdd`, `.githooks/`) to the `sdd-reviewer` checklist · `.claude/agents/sdd-reviewer.md` · check: `grep -q 'what CI enforces' .claude/agents/sdd-reviewer.md && grep -q '.claude/bom.py' .claude/agents/sdd-reviewer.md && python3 .claude/bom.py --check` → AC-13
- [ ] **T8** Write the ruleset file and the apply script with `--verify` and `--file` · `.github/rulesets/main.json`, `.github/scripts/apply-ruleset.sh` · check: `jq -e '.bypass_actors == [] and .enforcement == "active" and ([.rules[].type] | sort) == ["deletion","non_fast_forward","pull_request","required_status_checks"] and (.rules[] | select(.type == "pull_request") | .parameters.allowed_merge_methods == ["merge"] and .parameters.required_approving_review_count == 0) and (.rules[] | select(.type == "required_status_checks") | .parameters.required_status_checks == [{"context":"repo-checks","integration_id":15368}])' .github/rulesets/main.json && bash -n .github/scripts/apply-ruleset.sh` → AC-7, AC-8, AC-9, AC-10
- [ ] **T9** Push T6–T8 to the spec branch; the checks run again on the new commit. Record both run URLs (T5's and this one) in Evidence · GitHub · check: `gh pr checks --watch --interval 15` → AC-1
- [ ] **T10** Tell the owner that direct pushes to `main` are about to stop, so parallel sessions can finish or switch to pull requests; then, with their go-ahead, apply the ruleset and merge settings, and prove that `--verify` notices drift · GitHub · check: `.github/scripts/apply-ruleset.sh --verify && jq '.bypass_actors = [{"actor_id": 5, "actor_type": "RepositoryRole", "bypass_mode": "always"}]' .github/rulesets/main.json > /tmp/015-drift.json && ! .github/scripts/apply-ruleset.sh --verify --file /tmp/015-drift.json` → AC-7, AC-8, AC-9, AC-10
- [ ] **T11** On throwaway branches cut from the spec branch, open one trial pull request per planted defect (README hand edit, stale `STATUS.md`, unknown status, hook syntax error). For each, record the failing step's name and the `failure` conclusion, that `gh pr merge --merge --admin` is refused, and that after a fix commit the checks pass and the pull request is mergeable with no review. Close them and delete the branches · GitHub · check: manual: per trial, the pull request URL, the failing step and conclusion, the refused merge output, and the mergeable state after the fix are recorded in Evidence → AC-3, AC-4, AC-5, AC-8
- [ ] **T12** Save `origin/main`'s SHA in a local branch. Then, with the owner's admin token and in this order, stopping at the first push that is accepted: push an empty commit (`git push origin <sha>:main`), force-push an amended copy of `main`'s tip, and delete `main` (`git push origin :main`). Record each refusal; reset the local trial commits · GitHub · check: manual: the three refusals with their `GH013` lines are recorded in Evidence, and `git ls-remote origin refs/heads/main` still shows the saved SHA → AC-7, AC-9
- [ ] **T13** On a trial branch cut from the spec branch, follow "Adding a CI check" to make a trivial job required and see it block the trial pull request; this applies the ruleset before merging on purpose, so restore it right after. On a second trial branch, delete a check step and run `sdd-reviewer` in verify mode on it. Close both and delete the branches · GitHub, `.github/` on trial branches · check: manual: the trivial check shown as required and blocking, the reviewer's finding, and a passing `apply-ruleset.sh --verify` after the restore are recorded in Evidence → AC-12, AC-13
- [ ] **T14** Bring the spec's pull request up to date and merge it with a merge commit; record that only "Create a merge commit" was offered and that `git log origin/main` shows the `[015] Tn` commits and the merge commit · GitHub · check: manual: the merge button options and the `git log --oneline` excerpt are recorded in Evidence → AC-10
- [ ] **T15** On a close-out branch cut from the new `main`: tick T14, confirm the push run on the merge commit passes, then run `/sdd-verify 015` there and merge the close-out pull request · GitHub · check: `git fetch -q origin main && id=$(gh run list --commit "$(git rev-parse origin/main)" --event push --json databaseId --jq '.[0].databaseId') && gh run watch "$id" --exit-status` → AC-2

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
| AC-10 | | |
| AC-11 | | |
| AC-12 | | |
| AC-13 | | |
