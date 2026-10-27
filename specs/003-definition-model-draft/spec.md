---
id: 003
title: "Definition model draft"
type: spike
status: done
phase: 0
depends_on: [001, 002]
refs: ["PLAN Phase 0 / Spikes / 3. Definition model draft", "BACKLOG Spikes / Wave 2 / 3. Definition model draft", "BACKLOG MVP / Definition format", "BACKLOG MVP / Deterministic steps", "BACKLOG MVP / Scoring", "BACKLOG MVP / Reference workflow"]
updated: 2026-10-18
---

# 003 · Definition model draft

<!--
A spike answers questions and settles decisions; it does not ship production code. Run it with /sdd-spike 003:
the spike-researcher agent prepares a briefing and questions, you answer and add information over as many sessions
as it takes, and everything learned is recorded here. Prototypes are throwaway and live under specs/003-*/prototype/.
Progress counts the checkboxes in Goal questions, Questions for you, and Deliverables.
-->

## Inbox

<!-- Drop notes, links, constraints, or decisions here at any time, in any form. The next /sdd-spike session moves them into the Log and Findings and clears this section. -->

## Why

Milestone 1 (definition model and the reference workflow in YAML, with validation) needs a concrete schema. This spike writes the reference workflow in a draft YAML schema that expresses spike 001's domain model, and checks the scoring rules by computing one example run's score by hand.

It settles the `BACKLOG.md` MVP items Definition format, Deterministic steps, Scoring, and Reference workflow. Spikes 7 (target architecture) and 8 (driver API contract) depend on it.

## Goal questions

<!-- What the spike must answer, from BACKLOG.md. Ticked once Findings answers it completely. -->

- [x] **G1** The draft YAML schema's overall shape: workflow identity and version, stages, transitions with rework edges, and evidence types. Is YAML the format, and how is a definition validated?
- [x] **G2** Checkpoints and their steps: which built-in deterministic check types exist (no expression language), how reasoning steps and their rubrics are written, how human steps and councils are written, and where Laya's typed question sits.
- [x] **G3** Scoring: the raw-to-points mapping, the per-workflow deductions for failed attempts and rework loops, how overrides and not-evaluated steps count, and how the final run score is formed.
- [x] **G4** Spike 001's decisions in the schema: stage tasks and their provider-neutral limits, and the protected action (the merge) and what authorizes it.
- [x] **G5** The reference workflow: its stages, checkpoint placement, rework edge, and protected action, written in the draft schema.
- [x] **G6** One example run walked by hand, with a failed checkpoint attempt, a rework loop, and an override, and its final score computed.
- [x] **G7** Where each step runs, after the 2026-10-18 intent review added the runner: how a step declares engine, runner, or driver; how a runner check declares its image, command, and service containers with readiness probes; how hidden acceptance tests get into the sandbox; and how runner-produced (engine-verified) evidence and minimum trust levels appear in the schema.
- [x] **G8** Whether the reference workflow uses `sample-crm`'s own spec kit inside runs (left to this spike by spike 002, `docs/test-bed.md`).

## Briefing

<!-- Prepared by the spike-researcher agent: what the repository and current docs already say, with sources. -->

Prepared 2026-10-18. INTENT = `WORKFLOW_ENGINE_INTENT.md`. Spike 001's decisions are taken as settled. Spikes 2 (test bed) and 6 (Laya role) have not finished.

### G1 · Schema shape, format, validation

- Known: a definition version holds stages, stage tasks, transitions, checkpoints, steps, rubrics, scoring, evidence types, protected actions, typed questions, run inputs, and required driver capabilities. It is the only versioned unit. (`docs/concepts/glossary.md`, Definition version; 001 Q14)
- Known: a workflow is a stable ID, such as `sdlc-reference`, with one current version. Versions are `draft`, `published`, `superseded`, or `rejected`, and only a human in the UI publishes them. (001 Q9; constitution Art. 2)
- Known: one entry stage; each terminal stage is marked success or failure. No parallel stages or sub-workflows. Rework edges carry an explicit `rework` flag. A transition has zero or one checkpoint. (001 Q2, Q8; INTENT Reference workflow)
- Known: run inputs (repository, branch, task text) are declared by the definition and stored by the engine without interpretation. The subject key is opaque. (001 Q1)
- Known: a definition can require driver capabilities (core, activity recording, evaluation, enforcement, telemetry). (INTENT Driver contract)
- Known: a definition can be exported and imported as text. Milestone 1 is done when a human can read the reference workflow, see who authorizes each transition, and compute a sample score by hand, and invalid definitions are rejected. (INTENT Concepts; Delivery table)
- Known: the stack, including the YAML and validation library, stays open until spike 9. (BACKLOG spike 9; CLAUDE.md)
- Suggested: YAML; the first version loaded from a file, later versions from feedback-loop YAML diffs approved in the UI; no UI editor. (BACKLOG Definition format, Proposal flow)
- Unknown: how validation is split; whether the file has a format-version field; who assigns version numbers; one file or several; whether evidence types declare a payload shape; whether the definition says which inputs form the subject key (handed to spike 8).

### G2 · Checkpoints and steps

- Known: each step has an executor (four kinds), a role (required or optional), points, and a pass condition (a predicate, or a minimum raw score). Reasoning and human steps add a scale and rubric bands. Steps declare inputs with a minimum trust level, a council (default 1), and optional deductions. INTENT has an illustrative YAML step. (INTENT Step definition)
- Known: steps run strictly in order and fail fast. Evaluators return only raw results; the engine applies thresholds. A council is aggregated by median. (001 Q5; INTENT Execution rules, Fresh-context evaluation)
- Known: no expression language and no check plugins. (BACKLOG On record)
- Known: human steps are pass/fail in the MVP; band scoring of human steps is "Next". A human step can require separation of duties: the approver never held a lease on the run. (BACKLOG Next; 001 Q12)
- Known: typed questions are declared at definition level and used by a step with a mode. Shadow mode influences nothing. A Laya failure gives *not evaluated*. Laya never runs a deterministic step and never replaces a human step. (`docs/concepts/model.md` ER; INTENT Typed decisions)
- Suggested: built-in checks "evidence of type X exists" and "field compares to a value". (BACKLOG Deterministic steps)
- Suggested: one shadow-mode Laya question at the major checkpoint, classifying the reasoning step into its rubric bands; spike 6 makes the final call. (BACKLOG Laya MVP use)
- Unknown: the exact check types, field addressing, and operators; evaluator instruction text beyond the rubric; whether bands have IDs (needed for Laya labels); where a shadow Laya use sits in the ordered list; whether separation of duties also applies to overrides.

### G3 · Scoring

- Known: a scored step earns `points × (raw − min) / (max − min)`; a pass/fail step earns full points on pass and zero on fail. (INTENT Execution rules)
- Known: run score = earned over possible points, normalized to 100, using the latest evaluation of each step on the path taken. Branches not taken do not count. *Not evaluated* earns 0 and is flagged. Deductions are separate line items; the score never drops below 0. (INTENT Run score)
- Known: one deduction per failed attempt of a required step and one per rework loop (each traversal of a `rework` edge). An override keeps the step's raw result. Classification derives from run status: scored, overridden, or excluded. (001 Q6, Q8, Q18)
- Suggested: linear mapping, one per-workflow deduction of each kind, odd councils only. (BACKLOG Scoring)
- Conflict: step-level deduction overrides are BACKLOG "Next", but INTENT Step definition and the glossary say a step can override the deduction. Reading: the MVP has workflow-level deductions only.
- Assumption (to confirm): with fail-fast and no evaluator timeouts in the MVP, every step in a passed attempt has a result (shadow Laya scores nothing), so *not evaluated* only appears in excluded runs' partial scores.
- Unknown: the unit of deductions; what an overridden step earns; whether an overridden failure is also deducted; whether a failed attempt followed by rework pays both; rounding (proposal: exact decimals stored, one decimal displayed).

### G4 · Stage tasks and the protected action

- Known: each non-terminal stage has a stage task: task, acceptance criteria, tests (evidence types it produces), and limits. Task and criteria are templates over run inputs and earlier-stage evidence. (glossary; 001 Q22, Q25)
- Known: limits are max iterations, max wall time per visit, and max consecutive failed completion checks. The kit enforces them, the engine records them, exhaustion escalates to the owner. The toy workflow uses 8 iterations, 2 hours, 3 failed checks. (001 Q23–Q28; BACKLOG Stage tasks; `model.md`)
- Known: a protected action has a name and the stages where it is allowed. `authorize_action` checks the run is active, in an allowed stage, and the revision matches, then records an event. Afterwards the run moves on by a normal transition with evidence. (001 Q10, Q11)
- Known: a definition can require an enforcement-capable driver for runs with protected actions. (INTENT Driver contract)
- Suggested: the merge is the protected action. (BACKLOG Reference workflow)
- Unknown: template placeholder syntax (proposal: plain `{{inputs.x}}` and `{{evidence.type.field}}`, no logic); whether limits are required or default at workflow level; which revision `authorize_action` compares against; whether an action can be authorized more than once per visit.

### G5 · Reference workflow

- Known: INTENT wants the reference workflow to exercise everything: a major checkpoint (required deterministic, required reasoning, council of 3, an optional step, a human step), a stage check, a transition with no checkpoint, every trust level, separation of duties, every typed-decision mode, an excluded run, an override, a version upgrade. (INTENT Reference workflow)
- Known: happy path step 4 needs a deterministic, a reasoning, and a human step at the major checkpoint; step 5 needs Laya there; step 6 needs the merge. (BACKLOG happy path)
- Known: some of INTENT's list cannot run in the MVP: evidence is driver-reported only (system-attested is "Next"), advisory mode is "Next", bounded control is "On record". (BACKLOG)
- Suggested: `requirements → implementation → verification → release → complete`, one major checkpoint before `release`, one rework edge, the merge. (BACKLOG Reference workflow)
- Unknown: rework edge endpoints and whether it has a checkpoint; a failure terminal stage; stage checks; council size; separation of duties; points and deduction values.

### G6 · Example run by hand

- Known: the run must include a failed attempt, a rework loop, and an override, and a human must be able to follow the calculation. (spike Why; INTENT Delivery table)
- Unknown: depends on G3 answers and the G5 shape. A scenario is in Follow-ups.

### Researched

- networknt json-schema-validator: 2.x for Java 8+ and Jackson 2 (e.g. 2.0.4); 3.x for Java 17+ and Jackson 3 (e.g. 3.0.8). Supports draft-04 to 2020-12 (default 2020-12); reads YAML schemas and input via jackson-dataformat-yaml. ([GitHub](https://github.com/networknt/json-schema-validator), checked 2026-10-18)
- networknt 2.0: remote `$ref`s no longer fetched by default (classpath refs still load); errors are a `List` without codes; line and column positions are opt-in (`LocationJsonNodeFactory`) and cost performance. ([upgrading](https://github.com/networknt/json-schema-validator/blob/master/doc/upgrading.md), checked 2026-10-18)
- networknt reports 100% on the official 2020-12 test suite. json-sKema (erosb) also implements 2020-12; Everit is reported as maintenance-only, superseded by json-sKema (third-party listing, not checked on the repos). ([json-sKema](https://github.com/erosb/json-sKema), checked 2026-10-18)
- Since 2019-09, `format` is annotation-only by default. (networknt README, checked 2026-10-18)
- JSON Schema checks structure only: no cross-references, so target existence, unique IDs across lists, and rubric band coverage need a semantic validator in code whatever the library. (JSON Schema 2020-12 core)
- Jackson 2.x YAML uses SnakeYAML 2.x (2.21.0 → snakeyaml 2.4); Jackson 3.x (`tools.jackson.dataformat`, latest 3.2.2) uses snakeyaml-engine 2.10. ([Sonatype](https://central.sonatype.com/artifact/tools.jackson.dataformat/jackson-dataformat-yaml/), checked 2026-10-18)
- Not checked today: SnakeYAML follows YAML 1.1 (unquoted `yes`/`no`/`on`/`off` are booleans); snakeyaml-engine follows YAML 1.2. Matters for typed-question label sets.
- yaml-language-server supports drafts up to 2020-12; `# yaml-language-server: $schema=<path>` binds a file to a schema for editor and Claude-side validation. ([GitHub](https://github.com/redhat-developer/yaml-language-server), checked 2026-10-18)
- Open Workflow Specification (formerly CNCF Serverless Workflow, schema 1.0.4) writes its schema in YAML on draft 2020-12, uses `unevaluatedProperties: false`, and a header `document: {dsl, namespace, name, version}` separating format version from workflow version. ([workflow.yaml](https://raw.githubusercontent.com/serverlessworkflow/specification/main/schema/workflow.yaml), checked 2026-10-18)
- GitHub Actions environments: up to 6 required reviewers, one approval suffices; "Prevent self-review" is close to our separation of duties; rules live on the environment, not in workflow YAML. ([docs](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments), checked 2026-10-18)
- Argo Workflows: approval is `suspend: {}`; an optional `duration` auto-resumes, the opposite of fail-closed. ([docs](https://argo-workflows.readthedocs.io/en/latest/walk-through/suspending/), checked 2026-10-18)
- AWS Step Functions: `Retry` has `MaxAttempts` (default 3) with backoff; `Catch` routes to a named `Next` state, like our max attempts ("Next") and an explicit rework or failure edge. ([docs](https://docs.aws.amazon.com/step-functions/latest/dg/concepts-error-handling.html), checked 2026-10-18)

### Re-prepared 2026-10-18 for the runner (G7, G8, conflicts)

Q1–Q19 stand except where the conflicts below say otherwise.

#### G7 · Where steps run, runner checks, hidden tests, trust

- Known: deterministic steps run in the engine (built-in checks over evidence) or the runner (a check command in a sandbox image); agent evaluators run in the runner by default or on the driver side; each step declares *runs in*. INTENT's example writes `runs_in: runner`. (INTENT Step definition; glossary Executor)
- Known: runner jobs run at the transition request's revision, each in a throwaway pod; the runner fetches the SHA with a read-only token; unpushed work is never evaluated. Results are engine-verified, with runner, image, command, and SHA as provenance. A job that cannot run leaves its step *not evaluated*, with no driver fallback. (model.md; BACKLOG Runner)
- Known: check images and commands are definition content the engine passes through; git and Claude Code live in runner adapters. (model.md neutrality table)
- Known: the `sample-crm` check is `./mvnw -B verify` on `eclipse-temurin:25-jdk` (Dockerfile in this repo, pinned by digest), PostgreSQL as a native sidecar behind a startup probe, database via environment variables, never privileged, egress only to a Maven proxy, up to 15 minutes. (`docs/test-bed.md` Runner checks; 002 Q18–Q24)
- Known: hidden acceptance tests are drafted by Claude in a separate session, reviewed by the user, kept in this repo under `docs/`, never in `sample-crm`; not written yet. (`docs/test-bed.md`; 002 Q10, Q25)
- Known: the glossary Step entry, model.md ER (`inputs (min trust)`), and INTENT still describe a minimum trust level, which Q19 dropped.
- Known: the MVP does not build the driver-side evaluator subagent; the runner evaluator is headless Claude Code on the owner's `setup-token`. (BACKLOG Evaluator location, Runner / Model access)
- Suggested: one runner check type, a command in a sandbox image, passing on exit code 0; it also declares service containers with readiness probes; runner checks produce engine-verified evidence such as a test summary. (BACKLOG Deterministic steps, Check environment, Evidence; 002 G4)
- Suggested (researcher's sketch): `check: { command: { image: <ref>@sha256:…, run: [./mvnw, -B, verify], env: {…}, services: { postgres: { image, env, ready: { command: [pg_isready, …] } } }, timeout: 15m } }`. Services are at `localhost` (one pod network namespace).
- Unknown: hidden tests as gate or outcome; explicit `runs_in`; where the image is declared; timeouts as definition key or runner setting; whether runner checks produce evidence (and `min_trust`); whether a *not evaluated* required step costs a deduction; council behavior when one member cannot run (may go to spike 7); whether the runner evaluator reads the driver's `diff` or diffs the clone (spike 8 packet).

#### G8 · `sample-crm`'s spec kit inside runs

- Known: `sample-crm`'s kit covers baseline and maintenance only; `task/*` and `base/*` work follows the run's instructions. Planted defects are specified only in this repo (spec 004). (`docs/test-bed.md`)
- Known: the kit is Claude Code skills that stop after each step for review; provider concepts belong in driver kits and runner adapters. (CLAUDE.md; INTENT Design guardrails)
- Unknown: whether a stage task may name the repo's kit, and how its review stops fit checkpoints and in-session continuation (Art. 4).

#### Conflicts in the current draft

- Q8 lists engine checks only; a runner `command` check extends it and keeps one check per step.
- Q19 (no `min_trust`) holds only if no engine check reads runner-produced evidence; it breaks once driver-reported and engine-verified items of one type can coexist (e.g. `test_summary`).
- `required_capabilities` lists `evaluation`, which the MVP kit lacks; the engine refuses to bind a session missing a required capability. No step needs it once evaluators run in the runner. (`sdlc-reference.yaml`; INTENT Driver contract)
- `tests_green` reads a driver-reported `test_summary`, the weak point the intent review set out to remove. The `verification` stage task duplicates the runner check, and semantic rule 7 ("every step input is produced by some stage task") fails for runner-produced evidence.
- Q11 versus the runner's own fetch: no conflict; both use the request's revision. A force-push removing the SHA makes the job unable to run (*not evaluated*).
- Council of 3: each attempt reaching `code_quality` costs 3 headless sessions plus 1 for `docs_updated`, about the same as the driver-side design; spike 4 measures whether it fits. Q1 stands.
- Q13 versus Art. 4 (facts only): Art. 4 allows runner evaluations on the owner's subscription "only while the owner is the only user"; the amendment's reason is that Anthropic's terms allow subscription tokens for the owner's own scripted use "but not on behalf of other users". Neither text says whether "user" means a person or a UI account. In the test bed, one person controls both UI accounts (an accepted MVP risk), and the second account also drives runs (T03, T12: "one from each account"). Anthropic's wording is about people: no routing through Free, Pro, or Max credentials on behalf of one's users; each end user authenticates with their own credentials.
- T07 versus Q13: the test bed says "owner overrides with a reason", but Q13 binds overrides to separation of duties.
- T05 versus the draft: the test bed says "Human step rejects at requirements; rework", but the draft's only human step is `release_approval`.

#### Researched

- Kubernetes native sidecar: an init container with `restartPolicy: Always`; later containers start once its `startupProbe` succeeds; sidecars do not block Job completion. A sidecar whose startup probe never succeeds restarts until the Job's `activeDeadlineSeconds`, and the main container never starts; the runner must read that as "could not run". Probe handlers: exec, httpGet, tcpSocket, grpc. (kubernetes.io Sidecar containers, Probes, Jobs, checked 2026-10-18)
- GitHub Actions `services`: map of label to `{image, env, ports, options}`; readiness via Docker health options (`--health-cmd pg_isready …`). ([docs](https://docs.github.com/en/actions/tutorials/use-containerized-services/create-postgresql-service-containers), checked 2026-10-18)
- GitLab CI: job `image` plus `services` (`name`, `alias`, `entrypoint`, `command`); readiness waits on exposed ports and only warns on timeout, which is not fail-closed. ([docs](https://docs.gitlab.com/ci/services/), checked 2026-10-18)
- Anthropic: Pro and Max follow the Consumer Terms; developers may not route requests through Free, Pro, or Max credentials on behalf of their users; a product hosting Claude Code must have each end user authenticate with their own key or subscription; limits assume ordinary, individual usage. ([legal-and-compliance](https://code.claude.com/docs/en/legal-and-compliance), checked 2026-10-18)
- `claude setup-token`: a one-year OAuth token for CI pipelines and scripts, needs Pro, Max, Team, or Enterprise, model requests only. `--bare` mode does not read `CLAUDE_CODE_OAUTH_TOKEN`, so the runner evaluator must not use `--bare`. ([authentication](https://code.claude.com/docs/en/authentication), checked 2026-10-18)
- Not checked today: Maven compiles tests only from `src/test/java` by default; a hidden-test overlay must copy there or add a test source.

## Questions for you

<!-- Prepared by the agent, highest impact first; each says which goal questions it serves, why it matters, the options, and a suggested answer. Ticked when answered or skipped. -->

- [x] **Q1** Which of INTENT's "exercise every feature" items does the MVP reference workflow include? (→ G5, G2) *Why:* fixes the stages, checkpoints, and steps the draft expresses and milestone 1's validator covers. *Options:* only what happy path steps 4–6 need / that plus features the MVP supports cheaply (council of 3, an optional step, one stage check, a transition with no checkpoint, a failure terminal stage) / INTENT's full list with unsupported items marked. *Suggested:* the middle option; leave out other trust levels and the advisory and bounded-control modes.
- [x] **Q2** Does the reference workflow's human step require separation of duties in v1? (→ G5, G2) *Why:* it excludes everyone who held a lease on the run (001 Q12), so a solo developer could never approve their own run. *Options:* on, with a second UI user / off in v1, on in a later version / off. *Suggested:* off in v1, on in v2, which also exercises happy path step 14 (version upgrade).
- [x] **Q3** Are deductions subtracted from the normalized 0–100 score or from earned raw points? (→ G3, G6) *Why:* on raw points, the same deduction weighs differently per path. *Options:* normalized score / raw points. *Suggested:* normalized score, so "−5 for a rework loop" reads the same in every run.
- [x] **Q4** What does an overridden step earn? (→ G3, G6) *Why:* 001 Q6 keeps the raw result but not its points. *Options:* points mapped from its raw score (0 for pass/fail) / zero / full points. *Suggested:* mapped points; the override flag shows the exception.
- [x] **Q5** Does a failed required step that a human overrides still cost a failed-attempt deduction? (→ G3, G6) *Why:* the attempt passes after the override. *Options:* yes / no. *Suggested:* yes; the step did fail, and failure rates stay honest.
- [x] **Q6** When a failed attempt is followed by the rework edge, does the run pay both deductions? (→ G3, G6) *Options:* both / rework only / failed attempt only. *Suggested:* both; they measure different things.
- [x] **Q7** Where does Laya's shadow question sit in a checkpoint? (→ G2) *Why:* steps are ordered and fail fast; a separate shadow step would be skipped after a failure or make the checkpoint wait on Laya. *Options:* a block attached to the primary step it shadows, never ordered or scored / a separate step with a "shadow" role. *Suggested:* the attached block.
- [x] **Q8** Which deterministic check types does the MVP have? (→ G2) *Options:* `evidence_exists` and `field_compare` (field path, eq/ne/lt/lte/gt/gte, literal) / those plus `min_items` / a larger set (pattern, any-of). *Suggested:* the first two plus `min_items`, one check per step, combined by listing several steps.
- [x] **Q9** Do evidence types declare their payload shape? (→ G1, G2) *Why:* without one, `field_compare` paths cannot be checked at load time and malformed evidence cannot be rejected. *Options:* name only / name plus a simple field list / name plus an optional embedded JSON Schema. *Suggested:* the optional embedded JSON Schema; the simple field list if readability matters more.
- [x] **Q10** Is the format YAML, validated by a published JSON Schema 2020-12 for structure plus semantic checks in the engine? (→ G1) *Options:* YAML with both layers / YAML with semantic checks in code only / another format. *Suggested:* both layers; library choice stays with spike 9.
- [x] **Q11** Which revision must `authorize_action` match? (→ G4) *Options:* the revision of the transition request whose checkpoint let the run into the stage / the latest evidence's revision in this visit / any revision while in the stage. *Suggested:* the passed checkpoint's revision, so a commit added after review needs another attempt.
- [x] **Q12** Is a definition version one self-contained YAML file? (→ G1) *Options:* one file / one file plus referenced rubric files / a directory per version. *Suggested:* one file, one reviewable diff.
- [x] **Q13** Follows from Q2: with separation of duties on, how does a solo MVP setup get its approver, and does the rule also bind overrides? (→ G2, G5) *Why:* bootstrapping (spike 10) must then create a second human UI user, and an override is also a human decision on the run. *Options:* bootstrap two UI users, owner and approver; overrides bound too / two users; overrides not bound / one person with two UI accounts is acceptable for the MVP. *Suggested:* bootstrap two UI users, and bind overrides to the same rule, so nobody overrides a gate on a run they drove.
- [x] **Q14** Where does the reference workflow's rework edge go, and does it have a checkpoint? (→ G5, G6) *Why:* the example run walks it, and 001 Q8 counts one deduction per traversal. *Options:* `verification → implementation`, no checkpoint / `verification → implementation` plus `release → implementation` / `verification → requirements`. *Suggested:* the first: one edge, taken when the major checkpoint fails or the owner directs rework, no checkpoint of its own.
- [x] **Q15** How are versions numbered and the format versioned? (→ G1) *Options:* the engine assigns sequential integers on publish, and the file carries the workflow ID plus a `schema` field such as `ai-workflow/v0` / the author writes a semver version in the file / no format-version field yet. *Suggested:* the first, like Open Workflow's `document.dsl`, so a proposal never guesses its own number.
- [x] **Q16** Are deductions workflow-level only in the MVP? (→ G3) *Why:* INTENT and the glossary let a step override its deduction, but BACKLOG lists step-level deduction overrides as "Next". *Options:* workflow-level only; drop the step field from the draft / allow the step field now. *Suggested:* workflow-level only, as BACKLOG says, and fix the glossary wording.
- [x] **Q17** How large are the two deductions? (→ G3, G6) *Options:* 5 each / 10 each / 2–3. *Suggested:* 5 each.
- [x] **Q18** Can a human step's rejection be overridden? (→ G2, G4) *Options:* no / yes. *Suggested:* no; a rejection is already a human decision.
- [x] **Q19** Accept the draft's remaining choices (0-point gates and human step, required limits with no defaults, `wall_time` durations, plain-substitution templates, band IDs equal to Laya labels, evaluator `instructions`, no `min_trust` key)? (→ G1–G5) *Suggested:* accept all.
- [x] **Q20** In Art. 4, does "the only user" mean the only person or the only UI account? (→ G5, G7, Q13 conflict) *Why:* decides whether runner evaluations may use your `setup-token` once the approver account exists. *Options:* a person (your own second account does not count; clarify Art. 4's wording) / an account (get an API key or second credentials for the runner before bootstrapping the approver) / one UI account and no separation of duties (reverses Q2 and Q13). *Suggested:* none; this is your reading of Anthropic's terms.
- [x] **Q21** Are hidden acceptance tests a checkpoint step or an outcome measured outside the run score? (→ G7) *Why:* as a gate they need per-task files the driver must not see and change T06's expected path. *Options:* outcome only / a required gate step / an optional scored step. *Suggested:* outcome only in v1; INTENT uses objective outcomes to check the score.
- [x] **Q22** If Q21 makes them a gate, how do they reach the sandbox? (→ G7) *Options:* the runner fetches a second read-only source (this repo at a pinned revision) and copies it into the checkout / a per-task image with the tests baked in (reverses 002 Q22) / a hidden run input set in the UI. *Suggested:* the second source. Secrecy stays trust-based (Art. 8), since the driver's laptop can read this repo. (skipped: not needed, since Q21 makes hidden tests an outcome only)
- [x] **Q23** What does `tests_green` become? (→ G2, G5, G7) *Options:* a runner check that passes on exit code 0, job log as provenance only / the same plus `produces: test_summary` from a JSON file the command writes / a 0-point runner gate followed by `field_compare` on a runner-produced `test_summary`. *Suggested:* the first; `produces` goes to "Next", which differs from the BACKLOG Evidence suggestion.
- [x] **Q24** If runner checks produce evidence, how does the schema stop a driver supplying it? (→ G7, Q19 conflict) *Options:* bring back `min_trust` on step inputs / each evidence type declares its source (`runner` or `driver`) and the engine rejects driver submissions of runner types / both. *Suggested:* source on the evidence type. (skipped: moot, since Q23 has runner checks produce no evidence in the MVP)
- [x] **Q25** Must every deterministic and agent-evaluator step state `runs_in`? (→ G7) *Options:* required, checked against the check type / optional with defaults / no key, implied, with `runs_in: driver` only for driver-side evaluators. *Suggested:* required, so a reader sees who decides each step.
- [x] **Q26** Where is a runner check's image declared? (→ G7) *Options:* inline in the check, by digest / named environments at the top of the definition / runner settings. *Suggested:* inline by digest; named environments when a second check shares one.
- [x] **Q27** Does an attempt that fails only because a required runner step was *not evaluated* cost the failed-attempt deduction? (→ G3, G7) *Options:* no; recorded as not passed and retried / yes. *Suggested:* no; the deduction measures the work, and Art. 7 keeps *not evaluated* distinct from *failed*.
- [x] **Q28** What does the `verification` stage task ask the driver to do now? (→ G5, G7) *Options:* run the tests locally as its completion check, producing a non-gating driver-reported `local_test_summary` / only publish the branch and name the revision / fold `verification` into `implementation` (reopens Q14). *Suggested:* the first, like local tests before CI.
- [x] **Q29** Do runs use `sample-crm`'s own spec kit? (→ G8) *Options:* no; stage task prompts only / yes; the requirements task writes a spec with the kit / not in v1, kept as a candidate v2 change for the feedback loop. *Suggested:* the third.
- [x] **Q30** Who overrides in T07, given Q13 binds overrides to separation of duties? (→ G5, G6) *Options:* the approver account; reword T07 / the owner, with an exception to Q13. *Suggested:* the approver; only the test-bed wording changes.
- [x] **Q31** How does T05's "human rejects at requirements" fit a workflow with no human step there? (→ G5, G6) *Options:* add a human requirements review on `start_implementation` / rewrite T05 so the rejection happens at `release_approval`, followed by rework / drop T05 from the rework cases. *Suggested:* the second, low confidence.
- [x] **Q32** Accept the runner details the draft fills in? `timeout` required on a `command` check and covering services; running past it fails the check, while a never-ready service, unfetchable revision, sandbox start failure, or unavailable runner leave it *not evaluated*; `run` as an argument list; `env` literals only, no secrets; services at `localhost` with a required `ready` command; placeholder digests until the image is built; `SPRING_DATASOURCE_*` variable names. (→ G7) *Suggested:* accept all.

## Log

<!-- Append-only, oldest first: "- YYYY-MM-DD · <source> · <what was answered, added, or found>". -->

- 2026-10-18 · research · spike-researcher briefing on G1–G6, with JSON Schema validator and workflow-DSL prior art; 12 questions drafted. Noted a conflict: step-level deduction overrides are BACKLOG "Next" but appear in INTENT and the glossary.
- 2026-10-18 · answer to Q1 · Reference workflow covers what happy path steps 4–6 need plus the cheap extras: a council of 3, an optional step, one stage check, a transition with no checkpoint, and a failure terminal stage. Other trust levels and the advisory and bounded-control modes are left out.
- 2026-10-18 · answer to Q2 · Separation of duties is on for the human step from v1 (not the suggested "off in v1, on in v2"). Raised Q13: how a solo setup gets an approver, and whether it binds overrides.
- 2026-10-18 · answer to Q3 · Deductions are subtracted from the normalized 0–100 score.
- 2026-10-18 · answer to Q4 · An overridden step earns the points mapped from its raw score (0 for a pass/fail step).
- 2026-10-18 · answer to Q13 · Bootstrap creates two human UI users, an owner and an approver. Separation of duties also binds overrides: nobody overrides a gate on a run they held a lease on.
- 2026-10-18 · answer to Q5 · A failed required step that a human overrides still costs the failed-attempt deduction.
- 2026-10-18 · answer to Q6 · A failed attempt followed by the rework edge pays both deductions.
- 2026-10-18 · answer to Q7 · Laya's shadow question is a block attached to the step it shadows; it runs when that step runs, is never ordered or scored, and cannot delay or block the checkpoint.
- 2026-10-18 · answer to Q8 · Deterministic check types: `evidence_exists`, `field_compare` (field path, eq/ne/lt/lte/gt/gte, literal), and `min_items`. One check per step; combine by listing several steps.
- 2026-10-18 · answer to Q9 · An evidence type has a name and an optional embedded JSON Schema for its payload.
- 2026-10-18 · answer to Q10 · Format is YAML, validated in two layers: a published JSON Schema 2020-12 for structure, and semantic checks in the engine. Library stays with spike 9.
- 2026-10-18 · answer to Q11 · `authorize_action` must match the revision of the transition request whose checkpoint let the run into the stage.
- 2026-10-18 · research · Remaining briefing unknowns: Q14–Q16 added; template syntax, limit defaults, band IDs, points and deduction values, and rounding are proposed in the draft and confirmed on review.
- 2026-10-18 · answer to Q12 · A definition version is one self-contained YAML file.
- 2026-10-18 · answer to Q14 · One rework edge, `verification → implementation`, with no checkpoint; taken when the major checkpoint fails or the owner directs rework.
- 2026-10-18 · answer to Q15 · The engine assigns sequential integer version numbers on publish; the file carries the workflow ID and a schema field such as `ai-workflow/v0`.
- 2026-10-18 · answer to Q16 · Deductions are workflow-level only in the MVP; the step-level field is dropped from the draft and the glossary wording is fixed.
- 2026-10-18 · research · Draft written in `docs/definitions/` (schema.md, sdlc-reference.yaml, example-run.md); glossary Step and Deduction entries updated for Q16. Smaller choices made in the draft are recorded as assumptions in Findings for review.
- 2026-10-18 · answer to Q17 · Both deductions are 5 points of the normalized score.
- 2026-10-18 · answer to Q18 · A human step's rejection cannot be overridden.
- 2026-10-18 · answer to Q19 · The remaining draft choices are accepted as written.
- 2026-10-18 · note from the user · Recommendation and follow-ups agreed as drafted.
- 2026-10-18 · inbox (merge of `main`) · Intent review (PR #2): gate checks, deterministic and reasoning, run in a server-side runner at the pushed revision; runner results are engine-verified, with runner, image, command, and revision as provenance; reasoning steps run in the runner by default, driver-side evaluation is optional; a runner job that cannot run leaves its step not evaluated, with no fallback (INTENT Checkpoints, Evidence and trust, Fresh-context evaluation; constitution Art. 4, 5, 8; BACKLOG Runner).
- 2026-10-18 · inbox (merge of `main`) · BACKLOG Deterministic steps suggests one runner check type, "a command in a sandbox image that passes on exit code 0"; BACKLOG Runner / Check environment in definitions, "settled by spike 3", suggests a runner check declares an image and a command. INTENT's illustrative step adds `runs_in: runner`; the glossary Executor entry adds *runs in*.
- 2026-10-18 · inbox (merge of `main`) · Spike 002 (PR #1, G4): runner checks also declare service containers (PostgreSQL with a readiness probe); spike 3 decides how hidden acceptance tests get into the sandbox and whether runs use `sample-crm`'s spec kit; T05 (rework), T06 (failed checkpoint), and T07 (override) in `docs/test-bed.md` are ready-made cases for the example run. Infrastructure is now Kubernetes (local cluster), sandboxes are pods.
- 2026-10-18 · inbox (merge of `main`) · Conflicts raised: Q19's "no `min_trust` key" assumed only driver-reported evidence; Q8's check list has no runner check; the draft requires the `evaluation` capability and feeds `tests_green` a driver-reported `test_summary`; Q13's two bootstrap UI users may conflict with constitution Art. 4, which allows runner evaluations on the owner's subscription "only while the owner is the only user".
- 2026-10-18 · research · spike-researcher re-prepared G7, G8, and the conflicts (Kubernetes sidecars and probes, CI `services` prior art, Anthropic subscription terms, `setup-token`). Found two more test-bed conflicts: T07 (owner overrides) versus Q13, and T05 (human rejection at requirements) versus the draft. Q20–Q31 drafted.
- 2026-10-18 · answer to Q20 · In Art. 4, "the only user" means the only person. The user's own second UI account does not count, so Q13's two accounts stand. Art. 4's wording is to be clarified.
- 2026-10-18 · answer to Q21 · Hidden acceptance tests are an outcome only, measured outside the run score and compared with it; they are not a checkpoint step. Q22 skipped.
- 2026-10-18 · answer to Q23 · `tests_green` becomes a runner check that passes on exit code 0; the job log is provenance only and runner checks produce no evidence in the MVP. Runner-produced evidence goes to BACKLOG "Next". Q24 skipped as moot; Q19 (no `min_trust` key) still holds.
- 2026-10-18 · answer to Q25 · Every deterministic and agent-evaluator step must state `runs_in`, checked against its check type.
- 2026-10-18 · answer to Q26 · A runner check declares its sandbox image inline, pinned by digest; named environments can come when a second check shares one.
- 2026-10-18 · answer to Q27 · An attempt that does not pass only because a required runner step was *not evaluated* costs no failed-attempt deduction; it is recorded as not passed and retried.
- 2026-10-18 · answer to Q28 · The `verification` stage task has the driver run the tests locally as its completion check, producing a driver-reported `local_test_summary` that never gates.
- 2026-10-18 · answer to Q29 · Runs do not use `sample-crm`'s own spec kit in v1; using it is a candidate v2 change for the feedback loop.
- 2026-10-18 · answer to Q30 · In T07 the approver account overrides; Q13 stands, and T07's wording in `docs/test-bed.md` changes.
- 2026-10-18 · answer to Q31 · T05 is rewritten so the human rejects at `release_approval`, followed by rework; the workflow shape from Q1 stays.
- 2026-10-18 · research · Draft revised for the runner (schema.md, sdlc-reference.yaml, example-run.md); `docs/test-bed.md` T05, T07, hidden tests, and spec-kit lines updated. Runner details filled in by the draft are recorded as assumptions; Q32 asks to confirm them.
- 2026-10-18 · answer to Q32 · The runner details filled in by the draft are accepted as written.
- 2026-10-18 · note from the user · Updated recommendation and follow-ups agreed.
- 2026-10-18 · note from the user · At verify: amend Art. 4 now; file the other follow-ups as notes on existing items.

## Findings

<!-- Per goal question. Label each point as a decision (by the user), a fact (with source), or an assumption (to confirm). -->

### G1

- **Decision** (Q10): the format is YAML, validated in two layers: a published JSON Schema 2020-12 for structure (usable by editors and the feedback-loop skill before submission), and semantic checks in the engine for what JSON Schema cannot express (references resolve, unique IDs, reachability, band coverage). The library is chosen in spike 9.
- **Decision** (Q9): an evidence type has a name and an optional embedded JSON Schema for its payload, so the same validator checks evidence on submit and `field_compare` paths at load time.
- **Fact**: JSON Schema has no cross-reference keyword, so the semantic layer is needed whatever the library (JSON Schema 2020-12 core).
- **Decision** (Q12): a definition version is one self-contained YAML file.
- **Decision** (Q15): the engine assigns sequential integer version numbers when a version is published. The file carries the workflow ID and a format field, such as `schema: ai-workflow/v0`, never its own version number.
- **Fact**: the draft is written: `docs/definitions/schema.md` (top-level keys, stages, transitions, steps, scoring, 13 semantic rules, 8 invalid fixtures) and `docs/definitions/sdlc-reference.yaml`, which parses as YAML (checked with Ruby Psych, 2026-10-18). The formal JSON Schema file is milestone 1 work.
- **Decision** (Q19): `evidence_types` and `scoring` are required top-level keys; unknown keys are rejected.

### G2

- **Decision** (Q2): the reference workflow's human step requires separation of duties from v1. The approver must never have held a lease on the run (001 Q12).
- **Decision** (Q7): a shadow-mode typed question is a block attached to the step it shadows, not a step in the ordered list. It runs when that step runs, earns no points, and never delays or blocks the checkpoint.
- **Decision** (Q13): separation of duties binds both the human step and overrides on that checkpoint: nobody who held a lease on the run may approve or override.
- **Decision** (Q8): the deterministic check types are `evidence_exists`, `field_compare` (field path, one of eq/ne/lt/lte/gt/gte, and a literal), and `min_items` (list length). One check per step; several conditions mean several steps.
- **Decision** (Q19): rubric bands carry an `id`, and a shadowing typed question's labels must equal the shadowed step's band IDs.
- **Decision** (Q19): an `agent_evaluator` step may add `instructions` beyond the rubric. A human step has a `prompt` and no scale in the MVP.
- **Decision** (Q19): the MVP schema has no `min_trust` key, since only `driver_reported` evidence exists (Q1 left out other trust levels).
- **Conflict** (merge of `main`): Q8 lists engine checks only; BACKLOG now suggests a runner check type. Q19's "no `min_trust` key" assumed driver-reported evidence only; runner results are engine-verified. To resolve in the new questions.
- **Decision** (Q23): `tests_green` is a runner `command` check that passes on exit code 0. Runner checks produce no evidence in the MVP, so Q19 (no `min_trust` key) stands and the Q19/Q24 conflict is resolved.
- **Decision** (Q25): every deterministic and agent-evaluator step states `runs_in` (`engine`, `runner`, or `driver`), and the validator checks it against the check type: built-in checks run in the engine, `command` checks in the runner.
- **Fact**: the draft is revised (`docs/definitions/schema.md`): a fourth check type, `command`, runs in the runner; steps state `runs_in`; semantic rules are now 15 (new: `runs_in` matches the check type, images pinned by digest, every service has a `ready` command), with 10 invalid fixtures.

### G3

- **Decision** (Q3): deductions are subtracted from the normalized 0–100 score, as separate line items; the score never drops below 0 (INTENT Run score).
- **Decision** (Q4): an overridden step earns the points mapped from its raw score, `points × (raw − min) / (max − min)`, so a pass/fail step earns 0. The override is flagged and the run is classified `overridden` (001 Q18).
- **Decision** (Q5): a failed required step that is then overridden still costs the failed-attempt deduction.
- **Decision** (Q6): a failed attempt followed by a rework traversal pays both deductions.
- **Decision** (Q16): deductions are workflow-level only in the MVP. Steps carry no deduction field; step-level overrides stay in BACKLOG "Next".
- **Decision** (Q17): both deductions are 5 points of the normalized score. Values are stored unrounded and shown to one decimal.
- **Fact** (derived; corrects the briefing's assumption): in a completed run, *not evaluated* can appear only on an optional step whose executor was unavailable; it earns 0 and is flagged. A required step that is not evaluated fails closed, so its attempt does not pass (001 step result lifecycle; constitution, fail closed).
- **Decision** (Q27): an attempt that does not pass only because a required runner step was *not evaluated* costs no failed-attempt deduction. It is recorded as not passed and can be retried.

### G4

- **Decision** (Q11): `authorize_action` matches the revision of the transition request whose checkpoint let the run into the stage. A commit added after the review needs another checkpoint attempt before it can be merged.
- **Decision** (Q19): templates use plain `{{inputs.<name>}}` and `{{evidence.<type>.<field path>}}` substitution with no logic.
- **Decision** (Q19): limits are required on every stage task, with no workflow-level defaults; `wall_time` is a duration such as `30m` or `3h`.
- **Decision** (Q18): a human step's rejection cannot be overridden; the run reworks, retries, or is discarded instead.
- **Fact**: the draft expresses the merge as `protected_actions.merge.allowed_in: [release]` and requires the `enforcement` capability (semantic rule 12).

### G5

- **Decision** (Q1): the MVP reference workflow covers happy path steps 4–6 (deterministic, reasoning, human, and Laya steps at the major checkpoint; the merge) plus a council of 3, an optional step, one stage check, a transition with no checkpoint, and a failure terminal stage. Out: trust levels other than driver-reported, advisory mode, bounded control.
- **Decision** (Q2): its human step has separation of duties on from v1.
- **Decision** (Q13): the MVP bootstrap creates two human UI users, an owner and an approver. Input for spike 10 (bootstrapping).
- **Decision** (Q14): one rework edge, `verification → implementation`, with no checkpoint. It is taken when the major checkpoint fails or the owner directs rework.
- **Fact**: the reference workflow is drafted in `docs/definitions/sdlc-reference.yaml`: stages `requirements` (entry), `implementation`, `verification`, `release`, `complete` (success), `discarded` (failure). The major checkpoint on `verification → release` has `diff_present` (min_items), `tests_green` (field_compare), `code_quality` (council of 3, Laya shadow), optional `docs_updated`, and `release_approval` (human, separation of duties). The stage check on `release → complete` has `merge_recorded` (evidence_exists). Transitions with no checkpoint: `start_implementation`, `verify`, `rework`, `discard`.
- **Decision** (Q19): `release_approval` and the deterministic gates other than `tests_green` earn 0 points, so possible points are 35.
- **Conflict** (merge of `main`): Q13's second UI user versus constitution Art. 4 ("only while the owner is the only user") for runner evaluations on the owner's subscription. To resolve in the new questions.
- **Decision** (Q20): in Art. 4, "the only user" means the only person. The owner's own second UI account does not count, so Q13 stands: bootstrap an owner and an approver account, and runner evaluations may use the owner's `setup-token`. The Q13/Art. 4 conflict is resolved; Art. 4's wording is to be clarified.
- **Decision** (Q28): the `verification` stage task has the driver run the tests locally as its completion check and push the branch; it produces a driver-reported `local_test_summary` that no step gates on.
- **Decision** (Q31): no human step is added at requirements; test-bed task T05 is rewritten so the human rejects at `release_approval`, followed by rework.
- **Fact**: `docs/definitions/sdlc-reference.yaml` is revised: `required_capabilities` drops `evaluation`; `test_summary` becomes the driver's `local_test_summary`; `tests_green` is a runner `command` check (`./mvnw -B verify`, PostgreSQL service, 15 minutes); `code_quality` and `docs_updated` run in the runner. It parses as YAML (Ruby Psych, 2026-10-18). `docs/test-bed.md` T05 and T07 are reworded per Q30 and Q31.

### G6

- **Fact**: the example run is walked in `docs/definitions/example-run.md`. Attempt 1 fails `tests_green` (−5), the rework edge is taken (−5), attempt 2 fails `code_quality` at median 5 and a second user overrides it (−5, mapped points 8.889 of 20), then the human approves and the merge is authorized at the reviewed revision. Earned 22.639 of 35, normalized 64.683, run score 49.683 (shown as 49.7), classification `overridden`.
- **Fact** (spike 002): test-bed tasks T05, T06, and T07 (`docs/test-bed.md`) are ready-made cases for the example run; the run must be redone once G7 places `tests_green` and `code_quality`.
- **Decision** (Q30): in test-bed task T07 the approver account overrides, not the owner, because separation of duties binds overrides (Q13).
- **Fact**: `docs/definitions/example-run.md` is redone for the runner, with owner and approver accounts. The score is unchanged: earned 22.639 of 35, normalized 64.683, run score 49.683 (shown as 49.7), classification `overridden`. It notes that a never-ready database would make attempt 1 *not evaluated* with no deduction (Q27), and that attempt 2 uses four runner evaluations.

### G7

- **Fact** (INTENT; BACKLOG Runner): deterministic steps run in the engine (built-in checks over evidence) or in the runner (a check command in a sandbox image); agent evaluators run in the runner by default or on the driver side. Runner jobs run at the transition request's revision, each in a throwaway sandbox (a Kubernetes pod), and their results are engine-verified.
- **Fact** (spike 002, G4): a runner check must be able to declare service containers with readiness probes; how hidden acceptance tests reach the sandbox is left to this spike.

- **Fact** (INTENT Driver contract; BACKLOG Evaluator location): with evaluators in the runner, no reference-workflow step needs the `evaluation` capability, and the MVP kit lacks it; keeping it in `required_capabilities` would stop every session from binding. It must be removed from the draft.
- **Decision** (Q21): hidden acceptance tests are an outcome measured outside the run score and compared with it, not a checkpoint step. The definition needs nothing for them; Q22 does not arise.
- **Decision** (Q23): runner checks produce no evidence in the MVP; the job log and the runner, image, command, and revision are provenance only. This departs from the BACKLOG Evidence suggestion ("runner checks produce engine-verified evidence, such as the test summary"); runner-produced evidence goes to BACKLOG "Next".
- **Decision** (Q25): `runs_in` is required on deterministic and agent-evaluator steps.
- **Decision** (Q26): a runner check declares its sandbox image inline, pinned by digest. Named environments wait until a second check shares one.
- **Decision** (Q32): `timeout` is a required key of the `command` check and covers the whole job, services included; running past it *fails* the check, while a service never becoming ready, an unfetchable revision, a sandbox that does not start, or an unavailable runner leave it *not evaluated*; `run` is an argument list, not a shell string; `env` holds literals only, with no secrets in definitions; services are reached at `localhost` and each needs a `ready` command; image digests are placeholders until the image is built; the database variables use Spring Boot names (`SPRING_DATASOURCE_*`), to match spec 004's plan.
- **Fact** (research): what a council does when one member cannot run, and whether a runner evaluator reads the driver's `diff` or diffs the clone itself, are runner-interface and packet questions for spikes 7 and 8.

### G8

- **Fact** (`docs/test-bed.md`): `sample-crm` has its own spec kit for baseline and maintenance work; work on `task/*` and `base/*` branches follows the run's instructions; whether the reference workflow uses the kit inside runs is left to this spike.
- **Decision** (Q29): runs do not use `sample-crm`'s own spec kit in v1; stage tasks follow their prompts only. Using the kit is a candidate v2 change for the feedback loop.

## Recommendation and decisions

<!-- Drafted when every goal question is answered, and agreed with the user. -->

| Decision | Choice | Alternatives considered | Why |
| --- | --- | --- | --- |
| Format | One self-contained YAML file per definition version, with `schema: ai-workflow/v0` and the workflow ID; the engine numbers versions on publish (Q10, Q12, Q15) | Code-only validation; rubric files or a directory per version; author-written semver | One reviewable diff per version; proposals never guess their number |
| Validation | Two layers: published JSON Schema 2020-12 for structure, engine semantic rules for references and coverage (Q10) | Semantic checks in code only | JSON Schema cannot check cross-references; a published schema lets editors and the feedback-loop skill check drafts first |
| Evidence types | Name plus an optional embedded JSON Schema for the payload (Q9) | Name only; simple field list | Field paths checked at load time, malformed evidence rejected on submit, one validator |
| Deterministic checks | Built-in engine checks `evidence_exists`, `field_compare` (eq/ne/lt/lte/gt/gte), `min_items`, plus a runner `command` check; one check per step (Q8, Q23) | Two types only; pattern and any-of | Covers the reference and toy workflows with no expression language |
| Where steps run | `runs_in` required on deterministic and agent-evaluator steps: built-in checks in the `engine`, `command` checks in the `runner`, evaluators in the `runner` (or `driver`, which then requires the `evaluation` capability) (Q25) | Optional with defaults; implied by check type | A reader sees who decides each step, as milestone 1 asks |
| Runner check | `command`: image inline by digest, `run` as an argument list, literal `env`, `services` each with a `ready` command, required `timeout` covering the job; exit code 0 passes, a timeout fails, a job that cannot run is *not evaluated*; no evidence produced in the MVP, log kept as provenance (Q23, Q26, Q32) | `produces: test_summary`; named environments; images in runner settings | One check gives the gate with no extra parsing; runs stay pinned and replayable; fail closed on infrastructure |
| Hidden acceptance tests | An outcome measured outside the run score and compared with it, never a gate (Q21) | A required gate; an optional scored step | Keeps the test-bed paths, and an objective outcome checks the score instead of feeding it |
| Laya in a checkpoint | `typed_decisions` block on the shadowed step, mode `shadow`, labels equal its band IDs (Q7, Q19) | A separate shadow step | Never ordered, scored, or able to delay or block the checkpoint |
| Deductions | Workflow-level only, 5 points each off the normalized score, per failed attempt (including overridden failures) and per rework traversal; both paid when they coincide; none for an attempt that only had a *not evaluated* required step (Q3, Q5, Q6, Q16, Q17, Q27) | Raw-point deductions; step-level overrides; 10 or 2–3 points | Same weight in every run; failure and rework rates stay honest |
| Overrides | An overridden step earns points mapped from its raw score; a human rejection cannot be overridden (Q4, Q18) | Zero or full points; overridable rejections | Score shows real quality, the flag shows the exception; a rejection is already a human decision |
| Separation of duties | On for `release_approval` from v1, binding approvals and overrides; bootstrap creates two UI accounts. Art. 4's "only user" means the only person, so one person's two accounts may use the owner's `setup-token` (Q2, Q13, Q20) | Off in v1; overrides unbound; one person with two accounts | Exercises the guardrail from the start; nobody overrides a gate on a run they drove |
| Protected action | `merge` allowed in `release`; `authorize_action` requires the revision the passed checkpoint reviewed (Q11) | Latest evidence revision; any revision | A commit added after review cannot be merged without another attempt |
| Stage tasks | Plain-substitution templates; limits required on every stage task, `wall_time` as `30m`/`3h` (Q19) | Template logic; workflow-level limit defaults | Readable and explicit; no expression language |
| Reference workflow | `requirements → implementation → verification → release → complete`, failure terminal `discarded`, one rework edge `verification → implementation` with no checkpoint, major checkpoint before `release` (`tests_green` and both evaluators in the runner), stage check before `complete`; `verification` runs tests locally for feedback only; no `sample-crm` spec kit in v1; test-bed T05 rejects at `release_approval`, T07's override is by the approver (Q1, Q14, Q28–Q31) | Happy-path-only; INTENT's full list; other rework targets; a requirements review step | Covers happy path steps 4–6 plus cheap extras, keeps v1 provider-neutral |

## Deliverables

- [x] Every goal question answered, and the recommendation agreed with the user
- [x] Draft schema and the reference workflow example in `docs/` (or `definitions/`), with the hand-computed example run
- [x] BACKLOG.md items settled by this spike updated

## Follow-ups

<!-- New specs, BACKLOG items, experiments, or open questions this spike created. -->

- Milestone 1: write the formal JSON Schema from `docs/definitions/schema.md`, implement the 13 semantic rules, and add the 8 invalid fixtures as tests.
- Spike 6 (Laya role) may change the typed question, its labels, or its place; the draft follows the BACKLOG suggestion until then.
- Spike 8 (driver API contract): who computes the subject key from `run_inputs`, and the evaluation task packet built from a step's `instructions`, rubric, and inputs.
- Spike 10 (local setup): bootstrap two human UI users, an owner and an approver (Q13).
- Optional: write the article-publication toy workflow (`docs/concepts/model.md`) in the same schema to check that no SDLC term leaks into it.
- BACKLOG "Next": an `advisory` mode and `min_trust` key arrive with their features; step-level deductions stay in "Next". *(Dropped at verify: already covered by the existing "Laya beyond shadow mode", "System-attested evidence", and "Step-level deduction overrides" items.)*

Added after reopening for the runner (2026-10-18):

- Constitution Art. 4: clarify that "the only user" means the only person, so one person's second UI account does not count (Q20). Needs an amendment log entry.
- BACKLOG Evidence: runner checks produce no evidence in the MVP (Q23); add a "Next" item for runner-produced evidence and the `min_trust` key that comes with it.
- BACKLOG Runner / Check environment in definitions: settled by this spike (Q26, Q32).
- Spike 7: what a council does when one member's runner job cannot run; how the runner reports "could not run" versus "failed" (Q32's split).
- Spike 8: whether a runner evaluator reads the driver's `diff` or diffs the clone itself, in the evaluation task packet.
- Spec 004 and spike 10: the check image and its digest; the database variable names (`SPRING_DATASOURCE_*`) must match spec 004's plan.
- Quality monitoring: measure each test-bed task's hidden acceptance tests after the run, outside the score, and compare with it (Q21).
- Feedback loop: using `sample-crm`'s spec kit in runs is a candidate v2 definition change (Q29).
- Optional: write the runner-check form into the article toy workflow too, to check that no Kubernetes term leaks into the schema.

Filed at the second verify (2026-10-18), as agreed with the user: Art. 4 amended (constitution amendment log), with matching wording in `WORKFLOW_ENGINE_INTENT.md` and BACKLOG Runner / Model access; BACKLOG Evidence annotated and a "Runner-produced evidence" item added to Next; Check environment ticked; spikes 7, 8, and 10 carry "Input from spike 3" notes (spike 10 covers spec 004's variable names); the Quality view, "Richer feedback loop", and "Non-SDLC toy workflow" items carry the rest.

Filed at verify (2026-10-18), as agreed with the user: the milestone 1 item is a note on PLAN Phase 1 "Definition model"; the spike 6, 8, and 10 items are "Input from spike 3" notes on those BACKLOG spikes; the toy workflow extends the BACKLOG "Next" item.

Done during the spike (from the briefing's "Things to try"): the reference workflow draft, the semantic rules with invalid fixtures, the hand-computed run, and version numbering.

## Changelog

- 2026-10-18 Created.
- 2026-10-18 Briefing and questions prepared; status in-progress.
- 2026-10-18 Q1–Q16 answered; draft schema, reference workflow, and example run written in `docs/definitions/`.
- 2026-10-18 Q17–Q19 answered; all goal questions answered; recommendation agreed with the user.
- 2026-10-18 Verified and closed: BACKLOG Definition format, Deterministic steps, Scoring, and Reference workflow decided; PLAN spike 3 ticked; follow-ups filed.
- 2026-10-18 Reopened after merging `main`: the intent review added the runner and spike 002 handed over runner-check inputs. G2, G5, and G6 unticked; G7 and G8 added; inputs and conflicts in the Inbox. BACKLOG Deterministic steps and Reference workflow and PLAN spike 3 unticked; Definition format and Scoring stay decided.
- 2026-10-18 Inbox processed; briefing re-prepared for the runner; Q20–Q31 added.
- 2026-10-18 Q20–Q31 answered; draft revised for the runner; T05 and T07 reworded in `docs/test-bed.md`; G8 answered.
- 2026-10-18 Q32 answered; G2, G5, G6, G7 answered; recommendation and follow-ups updated for the runner.
- 2026-10-18 Updated recommendation agreed with the user.
- 2026-10-18 Verified and closed again: BACKLOG Deterministic steps, Reference workflow, and Runner / Check environment decided, Scoring and Evidence updated; Art. 4 amended; PLAN spike 3 ticked; follow-ups filed.
