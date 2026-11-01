# 016 · Plan: Repository layout

## Approach

Create the seven component folders from spike 010, plus the kit's collector subfolder, each holding only a README. Point the root `README.md`, `CLAUDE.md`, `PLAN.md`, and the two design docs that mention the collector configuration at the real folders.

Add a fourth check, `layout`, to `.github/scripts/repo-checks.sh`, with cases in its self-test, and run it as a new step of the existing `repo-checks` job. That job is already a required check (spec 015), so the layout check is required without touching the ruleset. The check needs a README in every top-level folder, so `deploy/` and `scripts/`, which have none today, get a short one.

## Constitution check

| Article | How the plan complies |
| --- | --- |
| 3. The engine is neutral | The Claude Code collector configuration gets its home in the kit folder, not next to the engine, as the architecture's "kit-owned" config requires. |
| 4. Drivers stay subscription-compatible | The new check is shell only; it calls no model and adds no credential to CI. |
| 5. Fail closed | The layout check fails if the root README has no "Read first" table at all, rather than passing on an empty list. Its step runs with `if: ${{ !cancelled() }}`, like the others, so an earlier failure does not hide it. |
| 8. Enforce gates off the laptop | The check runs in CI as part of the required `repo-checks` job, not only as a local habit. |
| 10. Happy path first | READMEs only: no build files or stubs. Each skeleton spec adds its own code and checks. |
| 11. Tests prove acceptance criteria | The check's behavior (AC-8) is proven by self-test cases; the folder and doc ACs by commands in the task checks and a read in `/sdd-verify`. |
| 12. Small, traceable increments | Five tasks, one commit each. |
| 13. Specs are living | AC-1, R9, and AC-9 were corrected in the spec before this plan (changelog). |
| 14. Decisions are written down | Decisions table below. |
| 15. Verify current docs | No new library, action, or tool. The workflow keeps the `actions/checkout` pin from spec 015; the check uses only bash, `ls`, `sed`, and `grep`. |

Articles 1, 2, 6, 7, and 9 are about the product's runtime and are not touched.

## Design

### Folders

| Folder | Holds (later) | Design doc | Filled by (PLAN item) |
| --- | --- | --- | --- |
| `engine/` | The engine: one Maven reactor, six modules | `docs/engine/design.md` | Phase 0, Engine skeleton |
| `mcp-gateway/` | The stateless MCP gateway in front of the driver API | `docs/driver-api/README.md`, `docs/architecture/README.md` | Phase 0, Engine skeleton (stub `status` over MCP); Phase 1, Driver API |
| `analysis/` | The analysis service and its read API | `docs/architecture/README.md` | Phase 0, Kubernetes manifests (a deployable stub); Phase 2, Quality view |
| `ui/` | The UI, a static client | `docs/architecture/README.md` | Phase 0, UI skeleton |
| `runner/` | The runner service | `docs/architecture/README.md` (Runner interface), `docs/local-setup/README.md` | Phase 0, Runner stub |
| `driver-kits/` | The local plugin marketplace (spike 014) | `docs/local-setup/README.md` | Phase 0, Claude Code driver kit skeleton |
| `driver-kits/claude-code/` | The Claude Code driver kit plugin | `docs/driver-api/README.md` | Phase 0, Claude Code driver kit skeleton |
| `driver-kits/claude-code/collector/` | The OTel Collector pipeline configuration, the one place for it | `docs/architecture/README.md`, spike 006 | Phase 0, OTel Collector |
| `definitions/` | Workflow definitions and the published JSON Schema (`definitions/schema/`) | `docs/definitions/schema.md`, `docs/engine/design.md` section 7 | Phase 1, Definition model |

Every README follows one shape, a few lines: a title, "Empty until the item below lands.", what the folder will hold, "Read: <doc>", and one line per PLAN item that fills it, written `Filled by: PLAN Phase N / <section> / <item>`, so a check can match each item name against `PLAN.md`. The collector README also says it is the one place for the collector's pipeline configuration. The kit and collector READMEs also say that the draft collector config still lives in `deploy/base/collector.yaml` until the OTel Collector item moves it.

`deploy/README.md` and `scripts/README.md` are new as well, so the existing folders pass the check: `deploy/` points to `docs/local-setup/README.md` and names the base and overlays; `scripts/` lists `baseline-checks.sh` and what it is for.

### Documents

- Root `README.md`, "Read first": a row for each new top-level folder (`engine/`, `mcp-gateway/`, `analysis/`, `ui/`, `runner/`, `driver-kits/`, `definitions/`). `deploy/`, `docs/`, `scripts/`, and `specs/` already have rows.
- `CLAUDE.md`: "Current state" names every new folder and says each holds only a README; "Where things live" gets one row pointing at the root README table.
- `PLAN.md`: the Repository layout item drops "for example", lists the folders, names the collector subfolder, and is ticked at verify.
- `docs/architecture/README.md` and `docs/local-setup/README.md`: one sentence each where they mention the collector configuration, naming `driver-kits/claude-code/collector/` as its home (AC-3).

### The layout check

`repo-checks.sh layout`:

1. Read the root `README.md` "Read first" table: the lines from `## Read first` to the next `## ` heading that start with `` | ` ``, taking the first backticked path of each row. If the table has no rows, fail.
2. For every row, fail if the path does not exist (file or folder).
3. For every top-level folder whose name does not start with a dot, fail if it has no `README.md`, or if no row's path is `<name>/`. The folders come from `git ls-files -co --exclude-standard | cut -d/ -f1 -s | sort -u`, so ignored folders in a working tree (`build/`, `node_modules/`, `graphify-out/`) do not count, and a local run agrees with CI.

It prints one `FAIL layout: …` line naming every offending folder or row, like the other checks. Dot folders (`.claude/`, `.github/`, `.githooks/`) are not required to have a row; `.claude/` has one today and the check still verifies it exists.

The self-test adds `layout` to its clean-copy loop and gets five cases: a new folder holding one file but no README fails; with a README but no row it still fails; with the row it passes; a row for a missing folder fails; a "Read first" section with its rows deleted fails (article 5). The usage comment and message in `repo-checks.sh` gain `layout`.

`CONTRIBUTING.md` "Adding a CI check" gets one line: a small repository check can instead be a new `repo-checks.sh` subcommand and a step in the `repo-checks` job, which is already required.

`ci.yml` gets a fourth step, `Layout is documented`, with `if: ${{ !cancelled() }}`.

## Requirement coverage

| Requirement | Covered by |
| --- | --- |
| R1 | The seven folders and `driver-kits/claude-code/` (T1) |
| R2 | One README per folder, in the shape above (T1) |
| R3 | `driver-kits/claude-code/collector/README.md` (T1), and the pointers in the two design docs (T2) |
| R4 | "Read first" rows (T2), kept true by the layout check (T3) |
| R5 | `CLAUDE.md` and `PLAN.md` edits (T2) |
| R6 | READMEs only (T1); the T1 check lists every file in the new folders |
| R7 | The spec's pull request (T5) |
| R8 | `repo-checks.sh layout` and its self-test cases (T3) |
| R9 | The `Layout is documented` step inside the required `repo-checks` job (T4) |

## Decisions

| # | Decision | Alternatives | Why |
| --- | --- | --- | --- |
| D1 | The layout check is a step in the existing `repo-checks` job | A new job added to the ruleset's required checks | `repo-checks` is already required, so no ruleset change and no owner-run `apply-ruleset.sh`; the step name still shows which check failed |
| D2 | Only non-dot top-level folders must have a README and a row | Every folder, including `.claude/`, `.github/`, `.githooks/` | Dot folders are tooling, not components; `.github/` and `.githooks/` are described in `CONTRIBUTING.md` |
| D3 | Every row's path must exist, files included | Check only folder rows | One rule catches a stale row of either kind, at no extra cost |
| D4 | Add READMEs to `deploy/` and `scripts/` | Exempt existing folders | One rule for every folder, so a later spec cannot point to an exception |
| D5 | Folder READMEs say only "empty until …", what it holds, and which doc to read | Copy design detail into each README | The design docs stay the single source; a README cannot drift from them if it holds no design |
| D6 | Collector config folder `driver-kits/claude-code/collector/` | `collector/` at the root; `deploy/` | The owner's answer (Q1): the config maps Claude Code names, so it is kit-owned |

## Test strategy

| Acceptance criterion | Verified by |
| --- | --- |
| AC-1 | T1 check: `test -d` on each folder and on `driver-kits/claude-code/` |
| AC-2 | T1 check: every new README names at least one `docs/` path, every such path exists, and every `Filled by:` item name is found in `PLAN.md` |
| AC-3 | T1 check: the collector README says "one place"; T2 check: both design docs name `driver-kits/claude-code/collector/` |
| AC-4 | T3: `repo-checks.sh layout` passes on the real tree; its self-test proves it would catch a mismatch |
| AC-5 | T2 check: `grep` for each folder in `CLAUDE.md`, and no "for example" on PLAN's layout line (PLAN already lists every folder) |
| AC-6 | T1 check: `find` in the new folders lists only `README.md` files |
| AC-7 | T5: `gh pr checks --watch` on the spec's pull request |
| AC-8 | T3: the self-test cases in `test-repo-checks.sh`; T5's run log shows the step ran, recorded in Evidence (`gh pr checks` shows only jobs) |
| AC-9 | T4: the step is in the `repo-checks` job (`grep`), and `apply-ruleset.sh --verify` confirms `repo-checks` is required on `main` (needs `gh` logged in as an admin, and `jq`); T5's run log shows the step |

## Risks and unknowns

- **Folder names fixed early.** Skeleton specs inherit these names. A rename later is a small pull request, but it touches `deploy/` image paths and docs; the names come from spike 010 to keep that unlikely.
- **README table parsing.** The check reads Markdown with `sed` and `grep`. A reformatted table (another column order, or a path without backticks) would fail it. That fails closed, and the self-test's clean case shows when it happens.
- **`deploy/` manifests mention the collector config inline.** Until the OTel Collector item moves it, the config sits in `deploy/base/collector.yaml` while the README says the kit folder is its home. Both READMEs say so, so a reader is not misled. Kustomize refuses files outside its root by default, which that later item must handle.
- **CI gets stricter.** A new failing step joins a required job; nothing is removed or loosened. The pull request says so, and `sdd-reviewer` flags it.
- **Other open branches.** A session that adds a top-level folder on another branch fails the layout check after this merges. That is the intent.

## Deviations

| Date | Task | Change | Why |
| --- | --- | --- | --- |
| 2026-11-01 | T3 | `top_folders` falls back to listing every folder when not inside a git work tree | The self-test runs in a scratch copy with no `.git`, where `git ls-files` fails. The copy holds only files that are not ignored, so the fallback sees the same folders |
| 2026-11-01 | T6 (new) | Self-test cases for the layout check inside a git work tree | Verify review: the self-test only exercised the fallback, not the `git ls-files` path CI runs |
