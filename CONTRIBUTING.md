# Contributing

How to make changes to this repository. What the project is and where to read are in `README.md`.

## Setup

Turn on the repository's git hooks once per clone:

```sh
git config core.hooksPath .githooks
```

This enables `.githooks/commit-msg`, which rejects commit messages that do not follow the format below.

## Work starts from a spec

All work goes through the spec kit in `specs/`. Nothing is implemented without a spec, even a small one. `specs/README.md` explains the commands, claims, and the practices every spec, plan, and task follows. `specs/constitution.md` has the rules all code must respect.

## Commit messages

The first line starts with the ID of the spec the commit belongs to:

| Prefix | Use it for | Example |
| --- | --- | --- |
| `[NNN] Tn` | A task of spec NNN | `[003] T2 Add run state table` |
| `[NNN]` | Work on spec NNN that is not one task | `[003] Close spike` |
| `[meta]` | Housekeeping that changes no behavior or requirements: hooks, settings, kit scripts, doc fixes | `[meta] Fix hook` |

Anything that is not housekeeping needs a spec, so `[meta]` is not a way around one.

Two hooks check the format:

- `.githooks/commit-msg` checks every commit made in a clone that ran the setup command. It lets through the messages git writes itself (`Merge …`, `Revert "…"`) and `fixup!`, `squash!`, and `amend!` commits.
- `.claude/hooks/pre-commit-message.sh` checks the commits Claude Code makes, before git runs. It is listed in `.claude/README.md`.

Neither hook runs on commits made in the GitHub web UI, so follow the format there by hand.

## Pull requests

`main` takes changes only through a pull request whose required checks pass. Nobody can push to it directly, force-push it, or delete it, the owner included (spec 015).

1. Work on a branch. A Claude Code session in a worktree already has one.
2. Commit in the format above and push the branch.
3. Open a pull request to `main`, for example with `gh pr create --base main`.
4. Wait for the checks: `gh pr checks --watch`. Run `.github/scripts/repo-checks.sh` first to see the repository checks fail or pass locally.
5. Merge with a merge commit, for example `gh pr merge --merge`. Squash and rebase are turned off, so every `[NNN] Tn` commit stays on `main`. No approving review is needed; merging is the owner's review.

A spec's work usually lands in one pull request. If work remains after it merges (for example `/sdd-verify`), it goes in a second, small pull request from a new branch.

## Adding a CI check

CI is `.github/workflows/ci.yml`. Each job's `name` is a status check, and the ruleset in `.github/rulesets/main.json` lists the ones that must pass. To add one:

1. Add a job to `.github/workflows/ci.yml` with an explicit `name:`. Do not add path filters: a required check that a filter skips stays pending and blocks every merge.
2. Open the pull request and let the job run once, so GitHub knows its name.
3. In the same pull request, add `{ "context": "<job name>", "integration_id": 15368 }` to `required_status_checks` in `.github/rulesets/main.json`. 15368 is GitHub Actions, so only the Actions job can satisfy the check.
4. After the pull request merges, the owner runs `.github/scripts/apply-ruleset.sh`, then `.github/scripts/apply-ruleset.sh --verify`, which compares GitHub's live rules with the file.

Changes to `.github/`, `.claude/bom.py`, `specs/_kit/sdd`, or `.githooks/` change what CI enforces. Say in the pull request what gets stricter or looser; `sdd-reviewer` flags them too.

## Documentation

Docs are written in plain, short sentences, consistent with the existing Markdown files. Add each new design document to `docs/README.md`.
