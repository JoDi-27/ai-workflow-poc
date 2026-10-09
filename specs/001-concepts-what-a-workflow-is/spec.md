---
id: 001
title: "Concepts: what a workflow is"
type: spike
status: done
phase: 0
depends_on: []
refs: ["PLAN Phase 0 / Spikes / 1. Concepts", "BACKLOG Spikes / Wave 1 / 1. Concepts"]
updated: 2026-10-08
---

# 001 · Concepts: what a workflow is

<!--
A spike answers questions and settles decisions; it does not ship production code. Run it with /sdd-spike 001:
the spike-researcher agent prepares a briefing and questions, you answer and add information over as many sessions
as it takes, and everything learned is recorded here. Prototypes are throwaway and live under specs/001-*/prototype/.
Progress counts the checkboxes in Goal questions, Questions for you, and Deliverables.
-->

## Inbox

<!-- Drop notes, links, constraints, or decisions here at any time, in any form. The next /sdd-spike session moves them into the Log and Findings and clears this section. -->

## Why

Every later spike and the engine's data model need one precise vocabulary. This spike turns the Concepts list in `WORKFLOW_ENGINE_INTENT.md` into a domain model: what each concept is, how concepts relate, how their lifecycles move, and which ones the engine knows about versus which exist only in workflow definitions.

It settles no `BACKLOG.md` MVP item directly. Spikes 3 (definition model), 6 (Laya/Jev role), and 7 (target architecture) depend on it.

## Goal questions

<!-- What the spike must answer, from BACKLOG.md. Ticked once Findings answers it completely. -->

- [x] **G1** Workflow, workflow definition, definition version, and run: what each one is and how they relate. What a run is attached to (task, branch, repository) and who owns it.
- [x] **G2** What a workflow's shape can be: a stage graph with rework edges. Can it have parallel stages, sub-workflows, or several entry and terminal stages? What is outside the model for the MVP?
- [x] **G3** Stage, transition, checkpoint, evaluation step, executor, evidence, protected action, typed decision, driver session, lease, and evaluation task: a definition for each, and how they relate.
- [x] **G4** Lifecycles as state machines: run (active, completed, failed, excluded), checkpoint attempt, step result (passed, failed, not evaluated, overridden), evaluation task, human step, and definition version (draft, published, superseded).
- [x] **G5** Which concepts the engine knows about and which exist only in definitions, so that SDLC terms stay out of the engine, tested by sketching a non-SDLC toy workflow with the same concepts.

## Briefing

<!-- Prepared by the spike-researcher agent: what the repository and current docs already say, with sources. -->

Prepared 2026-10-08. INTENT = `WORKFLOW_ENGINE_INTENT.md`.

### G1 · Workflow, definition, version, run

- Known: a workflow definition is a versioned, reviewable spec of stages, transitions (with rework edges), evidence types, checkpoints, typed decisions, protected actions, and the scoring formula. It is published through the UI and can be exported and imported as text. (INTENT, Concepts)
- Known: a run is one execution of a pinned definition version, with an owner, current stage, evidence, step results, pending approvals, decision records, events, and a final score. (INTENT, Concepts)
- Known: the pin covers checkpoints, rubrics, and the scoring formula. Changes reach new runs only, unless an audited migration is approved; migration is not a priority. (INTENT, Operating constraints; BACKLOG, On record)
- Known: drafts are editable, published versions immutable, and a version is published only on human approval in the UI. (INTENT, Persistence, Feedback loop; constitution Art. 2)
- Known: a run has one owner and at most one active driver lease. The data model keeps a workspace scope, with one workspace for now. (INTENT, Multi-user and shared state)
- Suggested: one run per branch; shipping means merging that branch. Settled by spike 7, but `CLAUDE.md`'s glossary states it as if decided. (BACKLOG, Runs and repositories)
- Suggested: the first version is loaded from YAML; later versions come from feedback-loop proposals. (BACKLOG, Definition format)
- Unknown: "workflow" itself is never defined; a stable identity grouping versions is implied but unnamed.
- Unknown: whether rubrics, scoring formulas, and decision questions are versioned on their own. Guardrails say "version independently", but Concepts pins them inside the definition version, and Run score and Quality monitoring mention a "scoring version" and "rubric version".
- Unknown: what a run is attached to in engine-neutral terms; whether "task" is a concept; who the owner is and whether that can change.
- Unknown: which version `start_run` uses; whether superseded versions can start runs; what "revert" means; what state a rejected proposal ends in.

### G2 · Workflow shape

- Known: stages joined by transitions, with explicit rework edges. A stage can have several outgoing transitions ("proceed, rework, or escalate"; "branches the run did not take"). (INTENT, Typed decisions role 3, Run score)
- Known: a failed checkpoint rejects the transition; the definition says what follows, a rework edge or ending the run as failed. (INTENT, Execution rules)
- Known: a transition has zero or one checkpoint. (INTENT, Reference workflow)
- Suggested: `requirements → implementation → verification → release → complete`, one major checkpoint before `release`, one rework edge. (BACKLOG, Reference workflow) The intent doc's example is longer (`intake → … → release readiness → complete`).
- Unknown: parallel stages, sub-workflows, and several entry or terminal stages are never mentioned; the Run concept says "current stage", singular.
- Unknown: whether "end the run as failed" means a failure terminal stage; whether a rework edge is a marked kind of transition; how a "rework loop" is counted for its deduction.

### G3 · Concepts and relations

- Known: a checkpoint is an ordered list of steps attached to a transition; major checkpoints and stage checks "follow the same rules". (INTENT, Concepts)
- Known: a step has an executor (four kinds), a role, points, a pass condition, a scale and rubric, inputs with a minimum trust level, an optional council, and optional deductions. (INTENT, Step definition)
- Known: evidence is typed and referenced, with provenance, revision, timestamp, and one of three trust levels. (INTENT, Evidence and trust)
- Known: a protected action is a driver-side action needing engine authorization at the moment it is taken; hooks are not the authoritative check. (INTENT, Concepts; Claude Code driver kit)
- Known: a typed decision is a question with bounded labels and a mode. The mode changes only by publishing a new version. In shadow mode it runs alongside a step's primary executor; only in bounded control can it be the primary. (INTENT, Typed decisions and Laya)
- Known: a driver session binds an agent session to a run, one human and one agent, recording provider, agent version, model, kit version, and capabilities. (INTENT, Concepts; Driver contract)
- Known: an evaluation task carries a task ID, the step definition, the rendered rubric, and evidence references. A council of N gets N tasks, aggregated by median. (INTENT, Fresh-context evaluation)
- Known: lease takeover is explicit and logged; heartbeat and takeover are "Next". (INTENT, Multi-user; BACKLOG, Next)
- Unknown: stage, transition, lease, evaluation task, human step, checkpoint attempt, decision record, event, and workspace are used but missing from the Concepts list. A stage's "objective" appears only in Transition protocol step 1.
- Unknown: whether "major" changes behavior or is only a label.
- Unknown: what a protected action is bound to (stage, transition, or checkpoint) and which run state authorizes it.
- Unknown: what the evidence "revision identifier" refers to; whether evidence belongs to the run or to one visit to a stage.
- Unknown: whether the evaluator subagent gets its own driver session (mostly spike 4).

### G4 · Lifecycles

- Known: a run ending on a failed required step is "reported as excluded" (Run score), while Execution rules say "ending the run as failed". The spike lists both as run states.
- Known: passed, failed, and not evaluated stay distinct. Fail-fast marks remaining steps not evaluated; an unavailable executor gives not evaluated; an override is recorded and the score flagged overridden. (constitution Art. 5, 7; INTENT, Execution rules)
- Known: a transition request is a proposal, committed atomically once every required step has a result, or rejected with the failed steps and the rework path. Requests are serialized and idempotent. (INTENT, Transition protocol; Multi-user)
- Known: the event list gives the lifecycle edges (`CHECKPOINT_STARTED`, `EVALUATION_TASK_ISSUED`, `HUMAN_STEP_DECIDED`, `TRANSITION_REJECTED`, …). There is no event for abandoning or cancelling a run. (INTENT, Transition protocol)
- Known: draft and published are defined; "superseded" appears only in the spike 1 text.
- Suggested: evaluation-task timeouts and retries, and human-step scoring, are "Next". (BACKLOG, Next)
- Unknown (contradiction): Execution rules run steps in order with fail-fast; Transition protocol step 4 reads as issuing evaluation tasks and queuing human steps all at once.
- Unknown: whether a run can be abandoned; whether an open checkpoint attempt can be withdrawn; what the run may do while an attempt waits on a human.
- Unknown: evaluation task states beyond issued and submitted; human step states beyond pending and decided; whether a retry reuses passed results; what follows an override.

### G5 · Engine versus definition

- Known: no SDLC and no provider concepts in the engine; stages, evidence types, checkpoints, and rubrics come from definitions. (INTENT, Operating constraints, Guardrails; constitution Art. 3)
- Known: generic vocabulary the engine can know: trust levels, executor kinds, step roles, typed-decision modes, driver capabilities, actor types, event types. (INTENT)
- Known: definition-only: stage names, evidence type names (`diff`, `design_doc`), rubric text, protected action names (merge, release, deploy), question wording.
- Suggested: the non-SDLC toy workflow is a "Next" item; this spike only sketches it on paper. (BACKLOG, Next)
- Unknown (SDLC leaks): "one run per branch" (BACKLOG, `CLAUDE.md`); engine-verified evidence by "resolving a commit on the shared remote" (INTENT, Evidence and trust), which needs git in the engine; a "revision identifier" assuming versioned artifacts. Each needs a neutral form: an opaque subject, an opaque revision, or a verifier adapter outside the engine core.

### G3/G4 · Stage loops (researched 2026-10-08, after the user's note)

- Known: `status` already returns the current stage and its "objective", the closest existing hook for a loop task. The Claude Code kit plans session-start, post-tool-use, and pre-tool-use hooks, but no Stop hook. (INTENT, Transition protocol step 1; Claude Code driver kit)
- Known: the design must not depend on a usage-based API budget. (INTENT, Operating constraints)
- Unknown: BACKLOG.md has no item on loop configuration, budgets, or exhaustion.
- Unknown: what one loop "iteration" is in provider-neutral terms; Claude Code uses "turn" for two different things (see below).

### Researched

- Claude Code `Stop` and `SubagentStop` hooks can keep a session working by returning `{"decision":"block","reason":…}` (or exit 2); `reason` becomes Claude's next instruction. Hook types: `command`, `http`, `prompt`, `agent`. The Stop input has `session_id`, `transcript_path`, `stop_hook_active`, `last_assistant_message`, and more, but no token or cost fields. ([hooks](https://code.claude.com/docs/en/hooks), checked 2026-10-08)
- Claude Code overrides a Stop hook after 8 consecutive blocks by default (`CLAUDE_CODE_STOP_HOOK_BLOCK_CAP`; `0` disables). A plugin's `settings.json` cannot set `env`, so a driver kit plugin cannot raise the cap. ([hooks guide](https://code.claude.com/docs/en/hooks-guide), [env vars](https://code.claude.com/docs/en/env-vars), [plugin components](https://code.claude.com/docs/en/plugins/components), checked 2026-10-08)
- For an `http` Stop hook, a connection failure, non-2xx status, or non-JSON body is non-blocking: if the engine is down, Claude stops (the safe direction). API errors fire `StopFailure` (`rate_limit`, `billing_error`, …), which is log-only. ([hooks](https://code.claude.com/docs/en/hooks), checked 2026-10-08)
- Interactive sessions have no turn limit: `--max-turns` and `--max-budget-usd` are print mode only; the Agent SDK has `maxTurns`/`maxBudgetUsd`; subagent frontmatter has `maxTurns`. "Turn" means a user-message-to-finish cycle in the Claude Code glossary, but tool-use round trips in the SDK's `maxTurns`. ([CLI reference](https://code.claude.com/docs/en/cli-reference), [agent loop](https://code.claude.com/docs/en/agent-sdk/agent-loop), [glossary](https://code.claude.com/docs/en/glossary), checked 2026-10-08)
- `/goal` is a built-in goal loop (interactive, `-p`, Remote Control): after each turn a small fast model judges the condition (not met, met, impossible). It has no deterministic iteration or time limit; a limit written into the condition is model-judged. It pauses on claude.ai usage limits. ([goal](https://code.claude.com/docs/en/goal), introduced v2.1.139–142, checked 2026-10-08)
- `/loop` is interval-based, not goal-based. ([scheduled tasks](https://code.claude.com/docs/en/scheduled-tasks), checked 2026-10-08)
- The official `ralph-wiggum` plugin (`/ralph-loop "<prompt>" --max-iterations N --completion-promise TEXT`) counts iterations in a state file from its Stop hook, unlimited by default; its README calls `--max-iterations` the primary safety mechanism. The original Ralph pattern (`while :; do cat PROMPT.md | claude-code ; done`) is an unattended external loop, which conflicts with Art. 4. ([ralph-wiggum README](https://github.com/anthropics/claude-code/blob/main/plugins/ralph-wiggum/README.md), [ghuntley.com/ralph](https://ghuntley.com/ralph/), checked 2026-10-08)
- Agent frameworks treat budget exhaustion as its own outcome: OpenAI Agents SDK `max_turns` (default 10) raises `MaxTurnsExceeded` or calls a handler; LangGraph `recursion_limit` raises `GraphRecursionError`, and `RemainingSteps` lets a graph route to a fallback first; AutoGen composes termination conditions with `|` and `&`. ([OpenAI Agents SDK](https://openai.github.io/openai-agents-python/running_agents), [LangGraph](https://docs.langchain.com/oss/python/langgraph/graph-api), [AutoGen](https://microsoft.github.io/autogen/stable/user-guide/agentchat-user-guide/tutorial/termination.html), checked 2026-10-08)
- Observable from the driver: iterations (count Stop firings), tokens and approximate cost (OTel `claude_code.token.usage`, `claude_code.cost.usage`; status line JSON). Wall time needs no driver data: the engine has stage-visit timestamps. ([monitoring](https://code.claude.com/docs/en/monitoring-usage), [status line](https://code.claude.com/docs/en/statusline), checked 2026-10-08)
- Provider-neutral loop fields (definition): goal/task and acceptance criteria as templates, tests as evidence types, maximum iterations (one iteration = one attempt by the agent to finish the loop), maximum wall time per stage visit, maximum consecutive failed exit checks, exhaustion behavior. Driver-specific (kit): provider "turns", token and USD budgets, the block cap, completion-promise strings, the `/goal` evaluator model, fresh context versus same session per iteration.
- A loop can also end for reasons other than budget exhaustion: a subscription usage limit, the 8-block cap, an unrecoverable error, or the user interrupting.

- AWS Step Functions (Amazon States Language) has exactly one `StartAt` state and any number of terminal states. `Choice` states have several next states; `Parallel` holds nested sub-machines, each with its own `StartAt`, failing as a whole if any branch fails. ([State machine structure](https://docs.aws.amazon.com/step-functions/latest/dg/statemachine-structure.html), [Parallel state](https://docs.aws.amazon.com/step-functions/latest/dg/state-parallel.html), checked 2026-10-08)
- GitHub Actions jobs form a DAG through `needs` and reject cycles, so a DAG model cannot express rework loops. ([Using jobs in a workflow](https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/using-jobs-in-a-workflow), checked 2026-10-08)
- So: rework edges call for a state machine with cycles, not a DAG. Parallelism can be added later as nested sub-machines without changing a single-active-stage core.

## Questions for you

<!-- Prepared by the agent, highest impact first; each says which goal questions it serves, why it matters, the options, and a suggested answer. Ticked when answered or skipped. -->

- [x] **Q1** What should the engine know about the thing a run works on? (→ G1, G5) *Why:* the session-start hook must find "the run for this branch", but Art. 3 keeps branch and repository out of the engine; this fixes run identity, lookup, and uniqueness. *Options:* (a) an opaque subject key plus definition-declared run inputs (repository, branch, task text) stored as data / (b) explicit repository and branch fields on the run / (c) run ID only, with the driver kit mapping branch to run locally. *Suggested:* (a), with at most one active run per workflow and subject key; (c) breaks with a second user.
- [x] **Q2** Which graph shapes should the MVP definition model accept? (→ G2) *Why:* one current stage or a set of them; drives checkpoint and scoring complexity. *Options:* (a) one entry stage, one active stage, cycles through rework edges, one or more terminal stages marked success or failure / (b) (a) plus parallel fork and join / (c) (a) plus sub-workflows. *Suggested:* (a); the intent says "current stage" and never mentions parallelism, and (b) can come later as nested sub-machines.
- [x] **Q3** Is "excluded" a run status or a classification of the run's score? (→ G4) *Why:* the intent uses "ending the run as failed" and "reported as excluded" for what looks like the same outcome. *Options:* (a) status active, completed, failed, abandoned; score scored, excluded, overridden / (b) status active, completed, excluded, failed (failed = abort or error). *Suggested:* (a); Art. 7's distinction is about score reporting, and it leaves room for abandoning a run.
- [x] **Q4** After a failed checkpoint attempt, what ends the run instead of allowing another attempt? (→ G2, G4) *Why:* per-attempt deductions imply retries, yet a definition can "end the run as failed". *Options:* an on-fail action per checkpoint / a maximum number of attempts / only an explicit human abandon. *Suggested:* the run stays in its stage and ends only through a transition to a failure terminal or a human abandon; max attempts goes to "Next".
- [x] **Q5** Within one checkpoint attempt, does a step start only after the previous one passed? (→ G3, G4) *Why:* Execution rules (ordered, fail fast) contradict Transition protocol step 4 (issue everything at once); parallel saves time but can waste a human decision and evaluator usage. *Options:* strictly in order / all at once, cancelling on failure / machine steps at once, human step last. *Suggested:* strictly in order for the MVP.
- [x] **Q6** After a human overrides a failed required step, does the checkpoint continue with the remaining steps? (→ G3, G4) *Why:* passing immediately could skip a later human step. *Options:* resume from the next step / pass immediately, rest not evaluated / override only after all steps ran. *Suggested:* resume from the next step; "overridden" is its own step-result status and raw points are kept.
- [x] **Q7** When a checkpoint is attempted again, do steps that passed last time run again? (→ G4) *Why:* "changed artifacts require reevaluation" implies reuse when nothing changed; affects replay and evaluator usage. *Options:* re-run everything / reuse passed results with unchanged input revisions / reuse only deterministic steps. *Suggested:* re-run everything for the MVP; reuse goes to "Next".
- [x] **Q8** Is a rework edge marked explicitly in the definition? (→ G2) *Why:* the rework-loop deduction needs a precise count, and "backward" is ambiguous with cycles. *Options:* explicit `rework` flag / inferred as any edge to an earlier stage. *Suggested:* explicit flag, each traversal counting as one loop.
- [x] **Q9** Do new runs always start on exactly one published "current" version of a workflow? (→ G1, G4) *Why:* defines "superseded", what `start_run` picks, and what "revert" means. *Options:* one current version, publishing supersedes the old one, revert publishes a copy / several published versions to choose from / current plus experimental via rollout. *Suggested:* one current version; rejected drafts stay on record.
- [x] **Q10** Is a protected action allowed only in certain stages, or is it part of a transition? (→ G3) *Why:* `authorize_action` must answer from run state. *Options:* the definition lists the stages where each action is allowed, and the run moves on by a normal transition with evidence the action happened / the action is the transition / the action has its own checkpoint. *Suggested:* the stage list; checkpoints stay on transitions only.
- [x] **Q11** What is the "revision" evidence is checked against at decision time? (→ G3, G5) *Why:* the engine cannot see the laptop and must not know git. *Options:* an opaque run-level revision string sent with each transition request and `authorize_action` / a revision per evidence type only / no freshness check in the MVP. *Suggested:* the opaque run-level string (a commit SHA in the SDLC kit), so only the reviewed revision gets merged.
- [x] **Q12** Is a run's owner fixed as the human who started it, even if another human takes over the lease? (→ G1, G3) *Why:* attribution and the separation-of-duties rule "the approver is not the run's driver". *Options:* fixed owner, lease holders tracked separately / ownership follows the lease / team ownership. *Suggested:* fixed owner; separation of duties excludes every human who ever held a lease on the run.
- [x] **Q13** Who can abandon a run, and from where? (→ G4) *Why:* raised by the Q4 answer; abandoning ends a run without a checkpoint, so it is a human-weight action, but a driver may need to give up a run it cannot finish. *Options:* the owner only, in the UI / the owner in the UI, and the driver may request it for the owner to confirm / owner or driver directly. *Suggested:* the owner in the UI, with the driver able to request it (Art. 2 keeps final human-weight actions in the UI).
- [x] **Q14** Are rubrics, scoring formulas, and typed-decision questions versioned on their own, or only as part of the definition version? (→ G1) *Why:* the intent contradicts itself: Guardrails say "version independently", while Concepts and Operating constraints pin them inside the definition version, and Run score and Quality monitoring mention a "scoring version" and "rubric version". *Options:* only inside the definition version; "rubric version" is derived as the definition version where that rubric last changed / independently versioned artifacts that a definition version references / independent for rubrics only. *Suggested:* only inside the definition version for the MVP; one pin, one replay rule, and quality views can still split by derived rubric version.
- [x] **Q15** Does evidence belong to the run as a whole, or to one visit to a stage? (→ G3) *Why:* with rework loops a stage is visited several times; a checkpoint must know which evidence counts. Raised by the Q11 answer. *Options:* the run, with each item tagged by stage visit and revision; a step uses the latest item of its type whose revision matches the run's current revision / each stage visit, so evidence is resubmitted after rework / the run, latest item of each type wins, ignoring revision. *Suggested:* the first; rework does not throw away evidence that is still valid, and the revision check (Q11) catches stale items.
- [x] **Q16** When a checkpoint needs evidence that was submitted in an earlier stage, which visit's evidence counts? (→ G3) *Why:* raised by the Q15 answer (evidence per stage visit). For example, the checkpoint before `release` may need the requirements document submitted in `requirements`. *Options:* the latest visit of the stage where it was submitted / only evidence from the current stage visit, so the driver resubmits everything the checkpoint needs / the step definition names the stage it reads from, latest visit of that stage. *Suggested:* the latest visit of the stage where that evidence type was submitted; rework back to a stage replaces only that stage's evidence.
- [x] **Q17** While a checkpoint attempt is open, what can the driver do? (→ G4) *Why:* an attempt can wait hours on a human; the model needs a rule for concurrent requests and for evidence that changes meanwhile. Raised while drafting `docs/concepts/model.md`. *Options:* one open attempt per run; the driver may submit evidence (the attempt uses only evidence whose revision matches its request) and may withdraw the attempt, which counts as a failed attempt for deductions / one open attempt, no withdrawal; the driver waits / several attempts may be open on different transitions. *Suggested:* the first; withdrawal lets the driver react to a fix without waiting on a human, and counting it as failed stops it being used to dodge deductions.
- [x] **Q18** How is the score classification set from the run status? (→ G4) *Why:* Q3 split status from classification but did not say how one follows from the other. *Options:* `completed` → `scored` (or `overridden` if any override), `failed` or `abandoned` → `excluded` with the partial score / `abandoned` runs get no score at all / classification set by a human at the end. *Suggested:* the first; it is deterministic and matches "a run that ends on a failed required step is reported as excluded".
- [x] **Q19** Do evaluation tasks and human step requests get a `cancelled` state when their attempt ends first (withdrawn, or run abandoned)? (→ G4) *Why:* otherwise a human could still be asked to decide a step that no longer matters, and an evaluator could submit into a closed attempt. *Options:* yes, cancelled, and late submissions are rejected / no, late results are recorded but ignored. *Suggested:* yes; nothing stays waiting in the UI inbox for a dead attempt.
- [x] **Q20** While the driver waits on an open checkpoint attempt, can it still submit evidence? (→ G4) *Why:* raised by the Q17 answer ("wait only"); the driver may keep working while a human decides. *Options:* yes; the open attempt ignores it unless its revision matches, and it is there for the next request / no; evidence submission is blocked until the attempt ends. *Suggested:* yes; it blocks nothing and keeps the record complete.
- [x] **Q21** By "a step in the workflow (or a node in the graph)", do you mean a *stage*? (→ G3) *Why:* in the glossary, "step" means an evaluation step inside a checkpoint; the two must not share a name. *Options:* a stage / an evaluation step / both. *Suggested:* a stage; the loop is the work done in the stage, and the checkpoint on the way out stays separate.
- [x] **Q22** Where do a stage loop's task, acceptance criteria, and tests come from? (→ G1, G3, G5) *Why:* in the SDLC workflow, the implementation stage's criteria come from what the requirements stage produced, so they cannot all be fixed in the definition. *Options:* the definition declares them as templates that can reference run inputs and evidence from earlier stages / fixed text in the definition only / produced at run time only, as evidence from earlier stages. *Suggested:* templates; fixed text and run-time evidence are both special cases of it.
- [x] **Q23** Who runs the loop, and who enforces its configuration (such as max turns)? (→ G3, G5) *Why:* the engine never invokes the driver's model (Art. 4), and the laptop is trust-based (Art. 8). *Options:* the driver runs the loop; the engine hands out the loop spec through `status`, records each iteration, and the driver kit enforces the limits locally / as before, and the engine also refuses a transition request once the recorded budget is exceeded / the loop configuration is advisory only. *Suggested:* the first; limits are enforced where the turns happen, and the engine records them so any overrun is visible.
- [x] **Q24** What happens when a stage loop runs out of budget before meeting its acceptance criteria? (→ G4) *Why:* this is a new way for work to stop that neither the run nor the checkpoint lifecycle covers yet. *Options:* the definition declares what follows per stage (escalate to the owner, take a named transition such as rework or failure) / the driver always escalates to the owner in the UI, who extends the budget, reworks, or abandons / the driver requests the transition anyway and the checkpoint decides. *Suggested:* the definition declares it, with escalation to the owner as the default.
- [x] **Q25** How do a stage loop's acceptance criteria and tests relate to the checkpoint on the way out? (→ G3) *Why:* you said the loop does not replace checkpoints; the model needs to say whether the two share anything. *Options:* the loop's criteria are the driver's own exit condition, and its test results are submitted as evidence that checkpoint steps may use; the checkpoint stays independent / the checkpoint automatically gets a deterministic step per loop criterion / no link at all. *Suggested:* the first; the loop is how the work gets done, and the checkpoint is the independent check of it.
- [x] **Q26** Can a driver keep working on its own after it tries to stop (a Stop hook or `/goal` continuing the session), inside a session a human started and is watching, or is that "unattended model invocation" under Art. 4? (→ G3, G5) *Why:* every in-session goal loop works this way; if it is not allowed, a stage loop is only instructions to the agent and its limits are advisory. *Options:* allowed, the human can interrupt any time / allowed up to the loop's iteration limit, then control returns to the human / not allowed, the human prompts each iteration. *Suggested:* allowed up to the iteration limit; INTENT defines "interactive" as "a human starts the agent", and the limit keeps every continuation bounded and recorded.
- [x] **Q27** Are loop limits in a definition expressed only in provider-neutral units? (→ G5) *Why:* "max turns" means different things even within Claude Code, and token budgets differ by provider and conflict with "no usage-based budget". *Options:* yes: maximum iterations (one attempt by the agent to finish the loop), maximum wall time per stage visit, maximum consecutive failed exit checks; turns and tokens stay in the driver kit / also allow token budgets in definitions / leave units to each driver kit. *Suggested:* yes, provider-neutral units only.
- [x] **Q28** Only budget exhaustion triggers the escalation to the owner; a loop that ends for another reason (subscription usage limit, the user interrupting, an error) just pauses, and the driver resumes it later. Agreed? (→ G4) *Why:* raised by research for the Q24 answer; these stops are not the stage's fault. *Options:* yes / treat every early stop as exhaustion. *Suggested:* yes.

## Log

<!-- Append-only, oldest first: "- YYYY-MM-DD · <source> · <what was answered, added, or found>". -->

- 2026-10-08 · research · spike-researcher prepared the briefing and Q1–Q12. Main gaps found: "workflow" is undefined; the intent doc contradicts itself on step ordering (Execution rules vs Transition protocol step 4), on failed vs excluded runs, and on independent vs pinned versioning of rubrics and scoring; several concepts in use are missing from the Concepts list; "one run per branch" and commit-based evidence would leak SDLC terms into the engine.
- 2026-10-08 · answer to Q1 · Decision: the engine knows a run's subject only as an opaque subject key, plus run inputs declared by the definition (for example repository, branch, task text) stored as data. At most one active run per workflow and subject key.
- 2026-10-08 · answer to Q2 · Decision: the MVP model has one entry stage, one active stage at a time, cycles through rework edges, and one or more terminal stages, each marked success or failure. No parallel fork/join and no sub-workflows.
- 2026-10-08 · answer to Q3 · Decision: "excluded" classifies the score, not the run. Run status: active, completed, failed, abandoned. Score classification: scored, excluded, overridden.
- 2026-10-08 · answer to Q4 · Decision: after a failed checkpoint attempt the run stays in its stage. It ends only by a transition to a failure terminal stage or by a human abandoning it. A maximum number of attempts goes to BACKLOG "Next". Raised Q13 (who can abandon).
- 2026-10-08 · answer to Q5 · Decision: within a checkpoint attempt, steps run strictly in order; a step starts only after the previous one has a result. The first failure of a required step stops the attempt and the remaining steps are "not evaluated". Resolves the contradiction with Transition protocol step 4.
- 2026-10-08 · answer to Q6 · Decision: after a human overrides a failed required step, the checkpoint resumes from the next step. "Overridden" is its own step-result status; the raw points are kept.
- 2026-10-08 · answer to Q7 · Decision: a new checkpoint attempt re-runs every step; no reuse of earlier results in the MVP. Reuse of unchanged results goes to BACKLOG "Next".
- 2026-10-08 · answer to Q8 · Decision: rework edges are marked with an explicit `rework` flag on the transition; each traversal counts as one rework loop for the deduction.
- 2026-10-08 · answer to Q9 · Decision: a workflow has exactly one current published version. Publishing makes a version current and supersedes the previous one; new runs always start on the current version. Revert publishes a copy of an older version. Rejected drafts stay on record.
- 2026-10-08 · answer to Q10 · Decision: the definition lists the stages in which each protected action is allowed. After the action, the run moves on by a normal transition with evidence that it happened. Checkpoints stay on transitions only.
- 2026-10-08 · answer to Q11 · Decision: an opaque run-level revision string, sent by the driver with each transition request and `authorize_action` call (a commit SHA in the SDLC kit). The engine only compares strings. Raised Q15 (evidence scope).
- 2026-10-08 · answer to Q12 · Decision: a run's owner is fixed as the human who started it. Lease holders are tracked separately. Separation of duties excludes every human who ever held a lease on the run.
- 2026-10-08 · research · Fact: "major" does not change behavior; major checkpoints and stage checks "follow the same rules" (INTENT, Concepts). Not asked. Raised Q14 from the versioning contradiction in the briefing.
- 2026-10-08 · answer to Q13 · Decision: only the run's owner abandons a run, in the UI. The driver can request it; the owner confirms.
- 2026-10-08 · answer to Q14 · Decision: rubrics, scoring formulas, and typed-decision questions are versioned only as part of the definition version; every change is a new definition version. A "rubric version" for quality views is derived: the definition version where that rubric last changed.
- 2026-10-08 · answer to Q15 · Decision (differs from the suggested answer): evidence belongs to one stage visit, not to the run as a whole. After rework, evidence is resubmitted for the new visit. The revision check (Q11) still applies within a visit. Raised Q16 (which visit counts for evidence from earlier stages).
- 2026-10-08 · answer to Q16 · Decision: when a step needs evidence submitted in an earlier stage, the latest visit of the stage where that evidence type was submitted counts. Rework back to a stage replaces only that stage's evidence.
- 2026-10-08 · note from the user · Agreed to draft the glossary, ER diagram, lifecycle diagrams, and non-SDLC toy workflow under `docs/concepts/` for review.
- 2026-10-08 · research · Drafted `docs/concepts/glossary.md` and `docs/concepts/model.md` (ER diagrams, lifecycles for run, definition version, transition request and checkpoint attempt, step result, evaluation task and human step request; engine-versus-definition table; article-publication toy workflow). Gaps found while drafting became Q17–Q19 and are marked "proposed" in the drafts. Mermaid syntax not machine-checked.
- 2026-10-08 · research · Toy workflow result: every concept maps onto article publication (subject = article ID, revision = document revision, protected action `publish` allowed in `ready`) without any SDLC term.
- 2026-10-08 · answer to Q17 · Decision (differs from the suggested answer): one open checkpoint attempt per run, and the driver waits for it to finish. No withdrawal and no other transition request while it is open. Raised Q20 (evidence submission while waiting).
- 2026-10-08 · answer to Q18 · Decision: score classification is derived from run status: `completed` → `scored`, or `overridden` if any override; `failed` or `abandoned` → `excluded`, with the partial score shown separately.
- 2026-10-08 · answer to Q19 · Decision: when an attempt ends early (now only when the run is abandoned), its open evaluation tasks and human step requests become `cancelled`, and late submissions are rejected.
- 2026-10-08 · note from the user · Draft review: "there is an issue in the toy workflow diagram, the rest is fine". Glossary, ER diagrams, and lifecycles accepted; toy workflow diagram to fix (issue asked).
- 2026-10-08 · note from the user · "A step in the workflow (or a node in the graph) is almost like a small self contained goal based loop. There is a task, acceptance criterias and tests. Being a loop, there also is of course loop configuration (max turns for example). This does not invalidate nor replace formal checkpoints." Reopens G3 (the stage concept changes); raised Q21–Q25. Research on driver-side goal loops launched.
- 2026-10-08 · answer to Q21 · Decision: the goal loop is a *stage*. "Step" keeps meaning an evaluation step inside a checkpoint.
- 2026-10-08 · answer to Q22 · Decision (user's words): "this is a framework. we provide templated behaviour. what is actually materialized is dependent on what is being configured for each workflow". The framework provides the stage-loop template; each workflow's definition configures what a stage's task, acceptance criteria, and tests actually are.
- 2026-10-08 · answer to Q23 · Decision: the driver runs the loop and its kit enforces the limits locally. The engine hands out the loop spec through `status` and records each iteration, so overruns are visible.
- 2026-10-08 · answer to Q24 · Decision (differs from the suggested answer): when a stage loop runs out of budget before meeting its acceptance criteria, the driver always escalates to the owner in the UI, who extends the budget, reworks, or abandons. Not configured per stage.
- 2026-10-08 · research · spike-researcher on driver-side goal loops (Stop hooks, `/goal`, Ralph, framework budgets); see Briefing. Raised Q26 (Art. 4 and in-session continuation), Q27 (neutral units), Q28 (early stops that are not exhaustion).
- 2026-10-08 · answer to Q25 · Decision: a stage loop's acceptance criteria are the driver's own exit condition; its test results are submitted as evidence that checkpoint steps may use. The checkpoint stays an independent check.
- 2026-10-08 · answer to Q26 · Decision: inside a session a human started, the driver may keep working on its own (Stop hook or `/goal` continuation) up to the loop's iteration limit; then control returns to the human. This is not "unattended model invocation" under Art. 4.
- 2026-10-08 · answer to Q27 · Decision: loop limits in a definition use provider-neutral units only: maximum iterations (one attempt by the agent to finish the loop), maximum wall time per stage visit, maximum consecutive failed exit checks. Provider turns and token budgets stay in the driver kit.
- 2026-10-08 · answer to Q28 · Decision: only budget exhaustion escalates to the owner. A loop stopped by a subscription usage limit, a user interrupt, or an error pauses, and the driver resumes it later.
- 2026-10-08 · research · Updated `docs/concepts/glossary.md` and `docs/concepts/model.md` with the stage loop (definitions, ER, stage-visit lifecycle, engine-versus-definition rows, toy workflow loop config) for review.
- 2026-10-08 · note from the user · Toy workflow diagram issue: "separation of duties each word is in a box". Cause: the `;` in the transition label is a Mermaid statement separator. Fixed by removing it, and the colons inside two other labels.
- 2026-10-08 · answer to Q20 · Decision: go with the suggestion. While the driver waits on an open checkpoint attempt it can still submit evidence; the open attempt ignores it unless its revision matches, and it is there for the next request.
- 2026-10-08 · note from the user · "I worded the stage as a loop, its does not need to be taken literally, I just want to keep the concept of it (self contained and validated task)". Renamed "stage loop" to *stage task* in the drafts; iterating (a goal loop) is how a driver kit typically does the work, not a model requirement. Limits, iterations as their unit, and exhaustion escalation (Q23–Q28) kept.
- 2026-10-08 · note from the user · "drafts are fine, go ahead with the recommendation". Updated glossary and model accepted, including the stage task and the fixed toy workflow. G3, G4, G5 resolved.
- 2026-10-08 · note from the user · "i agree". Recommendation and follow-ups agreed as drafted.
- 2026-10-08 · verify · Closed with `/sdd-verify 001`. Follow-ups applied: `WORKFLOW_ENGINE_INTENT.md` (Concepts rewritten to match `docs/concepts/`; Execution rules, Transition protocol step 4, and Guardrails versioning reworded); `CLAUDE.md` glossary (run and subject); `specs/constitution.md` Art. 4 amended and logged; `BACKLOG.md` (new decided Stage tasks item, Runs and repositories wording, spike 1 ticked, inputs added to spikes 3, 4, 7, 8, two Next items, one On record item); `PLAN.md` spike 1 ticked. Remaining assumptions handed to spikes 7 and 8.

## Findings

<!-- Per goal question. Label each point as a decision (by the user), a fact (with source), or an assumption (to confirm). -->

### G1

- **Decision (Q1):** a run works on a *subject*, which the engine knows only as an opaque subject key. The definition declares the run inputs (for example repository, branch, task text); the engine stores them as data without interpreting them.
- **Decision (Q1):** at most one active run per workflow and subject key. The driver finds "its" run by workflow and subject key.
- **Assumption:** the driver kit computes the subject key (the SDLC kit: repository + branch). Handed to spike 8 (BACKLOG, spike 8 "Input from spike 1").
- **Decision (Q9):** a *workflow* is the stable identity that groups its definition versions. It has exactly one *current* published version; new runs always start on it. Publishing a version makes it current and supersedes the previous one. Revert publishes a copy of an older version as a new version. Rejected drafts stay on record.
- **Decision (Q12):** a run's *owner* is the human who started it, and never changes. Driver lease holders are tracked separately.
- **Decision (Q14):** a definition version is the only versioned unit. Rubrics, scoring formulas, and typed-decision questions live inside it; any change to them is a new definition version. Quality views derive a "rubric version" as the definition version where that rubric last changed. Resolves the Guardrails "version independently" contradiction.
- **Decision (Q9, Q14):** definition version states: `draft` (editable), `published` and current, `superseded`, and `rejected` (a draft not approved, kept on record).

### G2

- **Decision (Q2):** the shape is a state machine, not a DAG: one entry stage, exactly one active stage at a time, cycles allowed through rework edges, and one or more terminal stages, each marked *success* or *failure*.
- **Decision (Q2):** parallel fork/join and sub-workflows are outside the MVP model. **Fact:** they can be added later as nested sub-machines without changing a single-active-stage core (AWS Step Functions `Parallel` state, see Briefing).
- **Decision (Q4):** a failed checkpoint attempt leaves the run in its current stage. "Ending the run as failed" means a transition to a failure terminal stage.
- **Decision (Q8):** a rework edge is a transition with an explicit `rework` flag, not inferred from the graph. Each traversal counts as one rework loop.
- **Fact:** a transition has zero or one checkpoint, and a stage can have several outgoing transitions (INTENT, Reference workflow; Typed decisions role 3).

### G3

- **Decision (user note):** the loop is not literal. The concept is a *stage task*: a self-contained, validated piece of work (task, acceptance criteria, tests, limits). Iterating toward it is how a driver typically works, not something the model requires.
- **Decision (Q21):** the goal loop belongs to a *stage*; "step" keeps meaning an evaluation step.
- **Decision (Q22):** the framework provides templated stage-loop behavior; each workflow's definition configures what is materialized (the stage's task, acceptance criteria, tests, and loop configuration).
- **Decision (Q23):** the driver runs the stage loop and enforces its limits; the engine hands out the loop spec through `status` and records each iteration (Art. 8).
- **Decision (Q25):** the loop's acceptance criteria are the driver's exit condition; its test results become evidence that checkpoint steps may use; the checkpoint stays independent.
- **Fact:** in Claude Code a kit can drive the loop with a Stop hook that blocks until the exit check passes or the limit is reached; an `http` Stop hook that cannot reach the engine lets Claude stop, so the loop fails safe (Briefing, Researched).
- **Decision (user note):** a stage is a small, self-contained, goal-based loop: it has a task, acceptance criteria, tests, and loop configuration (for example max turns). The loop does not replace or weaken formal checkpoints. Glossary and ER diagrams need updating once Q21–Q25 are answered.
- **Decision (review):** the glossary (`docs/concepts/glossary.md`) and the ER diagrams (`docs/concepts/model.md`) are accepted as the definitions and relationships of every concept.
- **Fact:** "major" is a label only; major checkpoints and stage checks follow the same rules (INTENT, Concepts).
- **Decision (Q10):** a protected action is declared in the definition with the stages where it is allowed. `authorize_action` checks that the run is active, in one of those stages, and that the revision matches (Q11); it records one event. After the action, the run moves on by a normal transition, with evidence that the action happened. Checkpoints attach only to transitions.
- **Decision (Q11):** evidence carries an opaque revision string. The driver sends the run's current revision with each transition request and `authorize_action` call; the engine checks freshness by comparing strings.
- **Decision (Q15):** evidence belongs to a *stage visit* (one entry of the run into a stage), tagged with its revision. Rework into a stage starts a new visit, and its evidence is submitted again.
- **Decision (Q16):** a step that needs evidence from an earlier stage reads the latest visit of the stage where that evidence type was submitted. Rework back to a stage replaces only that stage's evidence.
- **Decision (Q12):** separation of duties is checked against the event log: the approver of a human step must not be anyone who ever held a driver lease on the run.

### G4

- **Decision (Q3):** run status is `active`, `completed`, `failed`, or `abandoned`. Score classification is separate: `scored`, `excluded`, or `overridden`. A run that ends on a failed required step is reported with an excluded score.
- **Decision (Q4):** after a failed attempt the run stays `active` in its stage and may attempt again (each failed attempt earns its deduction). It becomes `failed` only by reaching a failure terminal stage, and `abandoned` only by a human abandoning it. A maximum number of attempts goes to BACKLOG "Next".
- **Decision (review):** `completed` means a success terminal stage was reached (run lifecycle diagram, accepted).
- **Decision (Q5):** a checkpoint attempt runs its steps strictly in order. A step starts only after the previous one has a result. The first failed required step ends the attempt as failed, and the remaining steps are `not evaluated`.
- **Decision (Q6):** step result states are `passed`, `failed`, `not evaluated`, and `overridden`. An override of a failed required step resumes the attempt from the next step and keeps the raw points.
- **Decision (Q7):** each new attempt re-runs every step; results are never carried over between attempts. Reuse goes to BACKLOG "Next".
- **Decision (Q24):** stage-loop budget exhaustion always escalates to the owner in the UI. The owner extends the budget, directs a rework, or abandons the run. **Assumption:** "directs a rework" means the owner tells the driver to take the rework transition, which still goes through its checkpoint, if any. Handed to spike 7 (BACKLOG, spike 7 "Input from spike 1").
- **Decision (Q28):** stage-visit loop states: working, paused (usage limit, interrupt, error; the driver resumes), exhausted (budget spent; escalated to the owner), criteria met (the driver requests a transition). Only exhaustion escalates.
- **Decision (Q13):** `active → abandoned` only by the owner in the UI. The driver can request abandonment; the request waits for the owner's confirmation.
- **Decision (review):** the lifecycle diagrams in `docs/concepts/model.md` are accepted, with Q17–Q19 applied.
- **Decision (Q20):** while waiting on an open attempt the driver may still submit evidence; the attempt ignores it unless its revision matches.
- **Decision (Q17):** a run has at most one open checkpoint attempt. The driver waits for it: no withdrawal and no other transition request while it is open. Only abandoning the run cancels an open attempt.
- **Decision (Q18):** score classification is derived: `completed` → `scored` (or `overridden` if any override); `failed` or `abandoned` → `excluded`, partial score shown separately.
- **Decision (Q19):** a cancelled attempt cancels its open evaluation tasks and human step requests; late submissions are rejected.
- **Decision (review):** evaluation task: `issued → submitted | cancelled`; human step request: `waiting → approved | rejected | cancelled` (Q19; lifecycle diagrams, accepted). Timeouts and retries stay in BACKLOG "Next".

### G5

- **Decision (Q27):** loop limits are provider-neutral (iterations, wall time per stage visit, consecutive failed exit checks); turns and token budgets belong to driver kits.
- **Decision (Q26):** in-session continuation up to the iteration limit is compatible with Art. 4; driver-side looping beyond it, or an external unattended loop (original Ralph pattern), is not.
- **Decision (Q1):** branch and repository stay out of the engine; they are definition-declared run inputs behind an opaque subject key.
- **Fact (toy workflow, `docs/concepts/model.md`):** every concept maps onto a non-SDLC article-publication workflow without SDLC terms. The engine-versus-definition split is tabled there.
- **Decision (Q11):** revisions are opaque strings to the engine; it never resolves commits. Engine-verified evidence (resolving a commit on a shared remote) would need a verifier adapter outside the engine core. **Fact:** that is post-MVP; system-attested evidence is already in BACKLOG "Next".

## Recommendation and decisions

<!-- Drafted when every goal question is answered, and agreed with the user. -->

Agreed with the user on 2026-10-08.

| Decision | Choice | Alternatives considered | Why |
| --- | --- | --- | --- |
| Run subject (Q1) | The engine knows only an opaque subject key plus definition-declared run inputs stored as data. At most one active run per workflow and subject key. | Repository and branch fields on the run; run ID only with a local branch-to-run map in the kit | Keeps SDLC terms out of the engine (Art. 3) and works across users and laptops |
| Workflow shape (Q2, Q8) | One entry stage, one active stage at a time, cycles through transitions flagged `rework`, one or more terminal stages marked success or failure. No parallel stages or sub-workflows in the MVP. | Parallel fork and join; sub-workflows; rework inferred from the graph | Matches "current stage"; an explicit flag makes the rework deduction countable; parallelism can come later as nested sub-machines |
| Stage task (user note, Q21–Q22, Q25) | Each non-terminal stage has a self-contained, validated stage task: task, acceptance criteria, tests, and limits, configured per workflow from framework templates. The driver does the work, typically by iterating; its test results become evidence; checkpoints stay independent. | A stage objective only; a literal loop as a model element; checkpoint steps generated from the criteria | Gives every stage a clear, checkable goal without weakening the independent checkpoint |
| Stage task limits (Q23, Q24, Q26–Q28) | Provider-neutral limits (iterations, wall time per visit, consecutive failed completion checks). The driver kit enforces them; the engine records every iteration. Exhaustion always escalates to the owner in the UI (extend, rework, or abandon). Other stops only pause. Continuing on its own inside a human-started session up to the limit is allowed under Art. 4. | Engine refuses transitions over budget; token budgets in definitions; exhaustion behavior per stage; human prompts every iteration | Limits are enforced where the work happens and recorded where they can be audited (Art. 8); no usage-based budget; the human decides what happens next |
| Versions (Q9, Q14) | A workflow has exactly one current published version, and new runs start on it. The definition version is the only versioned unit, including rubrics, scoring, and typed questions. States: draft, published, superseded, rejected. Revert publishes a copy. | Several published versions to choose from; rollouts; independently versioned rubrics and scoring | One pin and one replay rule; matches happy path step 14; quality views can still derive a rubric version |
| Run lifecycle and score (Q3, Q4, Q13, Q18) | Run status: active, completed, failed, abandoned. A failed checkpoint attempt keeps the run in its stage. A run ends only in a terminal stage or when the owner abandons it in the UI (the driver may request it). Score classification is derived: scored, overridden, or excluded (failed or abandoned). | "Excluded" as a status; a maximum number of attempts; an on-fail action per checkpoint; driver abandons directly | Separates how a run ended from how its score is reported (Art. 7); keeps final human-weight actions in the UI (Art. 2) |
| Checkpoint execution (Q5–Q7, Q17, Q19, Q20) | At most one open attempt per run; the driver waits (no withdrawal, no other transition request) but can still submit evidence. Steps run strictly in order and fail fast. An override resumes at the next step and keeps raw points. Every attempt re-runs all steps. Abandoning the run cancels the attempt, its evaluation tasks, and its human step requests. | Steps all at once; withdrawal; reusing passed results; recording late results | Matches fail-fast and "not evaluated" exactly; an override never removes another step; each attempt is self-contained for replay |
| Evidence and revision (Q11, Q15, Q16) | Evidence belongs to a stage visit. A step needing earlier-stage evidence reads the latest visit of the stage where it was submitted. The driver sends an opaque run-level revision with each transition request and authorization; the engine compares strings. | Evidence per run; latest wins regardless of revision; revision per evidence type; no freshness check | Rework produces fresh evidence for the reworked stage only; freshness without the engine knowing git |
| Protected actions (Q10) | The definition lists the stages where each action is allowed. After the action, the run moves on by a normal transition with evidence it happened. | The action is the transition; the action has its own checkpoint | Checkpoints stay on transitions only; authorization is a read of state plus one event |
| Ownership (Q12) | The owner is fixed as the human who started the run. Separation of duties excludes every human who ever held a lease on the run. | Ownership follows the lease; team ownership | Clear attribution; strict and easy to check from the event log |
| Engine neutrality (G5) | The engine knows only the generic vocabulary in `docs/concepts/model.md`; content lives in definitions. The non-SDLC toy workflow maps without SDLC terms. | Keep "branch" and commit resolution in the engine | Art. 3; the toy workflow shows the vocabulary is enough |

## Deliverables

- [x] Every goal question answered, and the recommendation agreed with the user
- [x] Glossary (`docs/concepts/glossary.md`)
- [x] Entity-relationship sketch (Mermaid)
- [x] Lifecycle diagrams (Mermaid state diagrams)
- [x] Non-SDLC toy workflow sketch
- [x] Concepts section of `WORKFLOW_ENGINE_INTENT.md` updated to match
- [x] BACKLOG.md items settled by this spike updated

## Follow-ups

<!-- New specs, BACKLOG items, experiments, or open questions this spike created. -->

Applied when the spike closes (`/sdd-verify 001`):

- `WORKFLOW_ENGINE_INTENT.md`: update Concepts to match `docs/concepts/glossary.md` (including workflow, stage task, stage visit, subject); reword Guardrails "version independently" (Q14); reword Transition protocol step 4 so steps run strictly in order (Q5); "ending the run as failed" means a failure terminal stage (Q4).
- `CLAUDE.md` glossary: replace "one run per branch" with "one active run per workflow and subject key; the SDLC kit uses repository + branch as the subject" (Q1).
- `specs/constitution.md`: amend Art. 4 (Q26): continuing on its own inside a human-started session, up to the stage task's iteration limit, is not unattended model invocation. Record it in the amendments log.
- `BACKLOG.md` MVP, Execution: add a stage task item (templated task, acceptance criteria, tests; neutral limits; kit enforces, engine records; exhaustion escalates to the owner). Amend "Runs and repositories" to the subject-key wording.
- `BACKLOG.md` Next: reuse of unchanged step results across attempts (Q7); a maximum number of attempts per checkpoint (Q4). On record: parallel stages and sub-workflows (Q2).

Handed to later spikes:

- Spike 3 (definition model): express the decisions above in the YAML schema, including the stage task and its limits; walk one run with a failed attempt, a rework loop, and an override, and compute its score by hand.
- Spike 4 (driver feasibility): prototype a Stop-hook stage task in a scratch repo (does the 8-block cap fire despite progress; does Stop fire on Esc); an `http` Stop hook against a stub (block continues, failure lets Claude stop); `/goal` with a turn limit in its condition (is a model-judged limit reliable). Known constraint: a plugin cannot raise the 8-block cap.
- Spike 7 (architecture): how an open checkpoint attempt progresses while waiting on a human, now that the driver must wait; how exhaustion escalations reach the owner.
- Spike 8 (driver API): who computes the subject key, and how iterations and escalations are reported.

## Changelog

- 2026-10-07 Created.
- 2026-10-08 Briefing and questions Q1–Q12 prepared; status in-progress.
- 2026-10-08 Q1–Q4 answered; Q13 added.
- 2026-10-08 Q5–Q8 answered; G2 resolved.
- 2026-10-08 Q9–Q12 answered; Q14 and Q15 added.
- 2026-10-08 Q13–Q15 answered; G1 resolved; Q16 added.
- 2026-10-08 Q16 answered. Concept drafts written under `docs/concepts/`; Q17–Q19 added.
- 2026-10-08 Q17–Q19 answered; glossary and ER diagrams accepted; Q20 added; toy workflow diagram flagged by the user.
- 2026-10-08 User added the stage-as-goal-loop concept; G3 reopened; Q21–Q25 added.
- 2026-10-08 Q21–Q24 answered; stage-loop research recorded; Q26–Q28 added.
- 2026-10-08 Q25–Q28 answered; concept drafts updated with the stage loop.
- 2026-10-08 Q20 answered; toy diagram fixed; "stage loop" reframed as "stage task".
- 2026-10-08 Drafts accepted; G3–G5 resolved; recommendation and follow-ups drafted for agreement.
- 2026-10-08 Recommendation agreed; ready for /sdd-verify.
- 2026-10-08 Verified and closed; follow-ups applied to the intent doc, CLAUDE.md, constitution, BACKLOG.md, and PLAN.md. Status done.
