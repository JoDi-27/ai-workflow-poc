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

## Documentation

Docs are written in plain, short sentences, consistent with the existing Markdown files. Add each new design document to `docs/README.md`.
