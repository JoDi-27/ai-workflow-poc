# Glossary

From spike 001 (`specs/001-concepts-what-a-workflow-is/`), where every point is decided in the Log. The relationships and lifecycles are in `model.md`.

Terms are grouped by when they exist: in a workflow definition, while a run executes, or around both.

## Definition-time concepts

These are authored in a workflow definition. The engine knows the concept but not the content; for example, it knows "stage" but not "implementation".

- **Workflow:** the stable identity, such as `sdlc-reference`, that groups the versions of one definition. It has exactly one *current* version, and new runs always start on it.
- **Definition version:** one immutable, published snapshot of a workflow's definition. It contains every stage, transition, checkpoint, step, rubric, scoring rule, evidence type, protected action, typed question, run input, and required driver capability. It is the only versioned unit. Changing a rubric or the scoring formula means publishing a new definition version. States: `draft`, `published`, `superseded`, `rejected`.
- **Stage:** a node of the workflow graph: a named state a run can be in. Each non-terminal stage has a *stage task*. A definition has exactly one *entry* stage and one or more *terminal* stages. Each terminal stage is marked *success* or *failure*.
- **Stage task:** the self-contained, validated piece of work a stage asks for. The definition configures it from the framework's templates: a *task*, *acceptance criteria*, *tests*, and *limits*. The driver does the work until the criteria pass, typically by iterating (a goal loop), but how is up to the driver kit. The engine hands out the stage task through `status` and records the work. It does not replace checkpoints: its criteria are the driver's own completion check, and its test results become evidence that checkpoint steps may use.
- **Task, acceptance criteria, tests:** templates in the definition that can reference run inputs and evidence from earlier stages. What they materialize to depends on each workflow's configuration. Tests are declared as evidence types the stage task produces.
- **Limits:** provider-neutral bounds on a stage task: maximum iterations, maximum wall time per stage visit, and maximum consecutive failed completion checks. Provider turns and token budgets belong to driver kits, not definitions.
- **Transition:** a permitted move from one stage to another. It has zero or one checkpoint. A stage can have several outgoing transitions.
- **Rework edge:** a transition marked with the `rework` flag. Each traversal counts as one rework loop for the deduction.
- **Checkpoint:** an ordered list of steps attached to a transition, run when that transition is requested. *Major* is a label only; a major checkpoint and a *stage check* follow the same rules.
- **Step (evaluation step):** one unit of a checkpoint. It declares an executor, a role (*required* or *optional*), points, a pass condition, inputs (evidence types and the minimum trust level), and, for reasoning and human steps, a scale and rubric. It can also declare a council size and deductions.
- **Executor:** what performs a step: *deterministic* code in the engine, a fresh-context *agent evaluator* on the driver side, a *human* in the UI, or a *DecisionEngine* provider such as Laya (subject to its mode).
- **Rubric:** a step's scale divided into bands, each with a descriptor and optional examples.
- **Evidence type:** a named kind of evidence the definition declares, such as `diff`, `test_summary`, or `fact_check_report`.
- **Run input:** a value the definition declares as needed to start a run, such as repository, branch, or task text. The engine stores it without interpreting it.
- **Protected action:** a named driver-side action, such as `merge` or `publish`, that needs engine authorization at the moment it is taken. The definition lists the stages where it is allowed.
- **Typed question:** a question with a bounded set of labels, answered by a DecisionEngine. Each use declares a *mode*: *shadow*, *advisory*, or *bounded control*.
- **Deduction:** points subtracted per failed attempt of a required step and per rework loop. Each workflow sets a default, and a step can override it.
- **Driver capability:** something a driver kit declares it can do: *core*, *activity recording*, *evaluation*, *enforcement*, or *telemetry*. A definition can require some of them.

## Run-time concepts

These are created by the engine while a run executes.

- **Run:** one execution of a workflow, pinned to the definition version that was current when it started. It has an owner, a subject, a status, a current stage, and finally a score.
- **Subject:** the thing a run works on. The engine knows it only as an opaque *subject key* computed by the driver kit (in the SDLC kit, repository + branch). There is at most one active run per workflow and subject key.
- **Owner:** the human who started the run. The owner never changes, even when another human takes over the lease.
- **Stage visit:** one entry of a run into a stage. Reworking back into a stage starts a new visit, with a fresh stage task.
- **Iteration:** one attempt by the driver's agent to complete the stage task, ending with a completion check against the acceptance criteria. The driver reports each one; the engine records it. Inside a human-started session the driver may continue on its own up to the iteration limit; then control returns to the human.
- **Exhaustion:** a stage task reaching one of its limits before its acceptance criteria are met. It always escalates to the owner in the UI, who extends the budget, directs a rework, or abandons the run. Work stopped for another reason (a usage limit, an interrupt, an error) is *paused*, not exhausted, and the driver resumes it.
- **Evidence:** a typed item submitted during a stage visit, with provenance, an opaque *revision*, a timestamp, and a trust level (*engine-verified*, *system-attested*, or *driver-reported*). A step that needs evidence from an earlier stage reads the latest visit of the stage where that evidence was submitted.
- **Revision:** an opaque string identifying the state of the subject's work, such as a commit SHA in the SDLC kit or a document revision in another. The driver sends the current revision with each transition request and protected-action authorization. The engine checks freshness only by comparing strings.
- **Transition request:** the driver's proposal to take a permitted transition, carrying the revision and an idempotency key. It never changes state by itself.
- **Checkpoint attempt:** one evaluation of a checkpoint for a transition request. Steps run strictly in order, and the first failed required step ends the attempt. Each new attempt re-runs every step.
- **Step result:** the outcome of one step in one attempt: `passed`, `failed`, `not evaluated`, or `overridden`. It holds the raw result, band, points, executor record, and evidence used.
- **Evaluation task:** an engine-issued packet (task ID, step, rendered rubric, evidence references) that a driver-side evaluator fetches and answers directly. A council of N gets N tasks, and the engine takes the median.
- **Human step request:** a human step waiting in the UI inbox for an authorized human to decide.
- **Decision record:** one DecisionEngine answer to a typed question, with its mode, label, distribution, provider, model, and latency.
- **Override:** a human's decision in the UI to pass a failed required step, with a reason. The checkpoint continues from the next step, and the run's score is flagged.
- **Run score:** earned points over possible points on the path the run took, normalized to 100, with deductions as separate line items. Its *classification* is `scored`, `excluded`, or `overridden`; this is separate from the run status.
- **Event:** an append-only record of something that happened, from which run state and score can be replayed.

## Actors and access

- **Driver:** an interactive coding agent on a developer's laptop, plus its provider's driver kit, acting on runs through the driver API.
- **Driver kit:** the per-provider package (MCP configuration, hooks, instructions, evaluator, CLI) that makes an agent a driver.
- **Driver session:** the binding of one agent session (one human, one agent) to one run, recording provider, agent version, model, kit version, and capabilities.
- **Lease:** the right of one driver session to act on a run. A run has at most one active lease. Takeover is explicit and logged.
- **Human (UI user):** an authenticated person in the UI. Only humans decide human steps, override, abandon runs, and publish definition versions.
- **Separation of duties:** a human step can require that its approver never held a driver lease on the run.
- **DecisionEngine:** the engine-side interface to typed decision models. Laya is the first provider.
- **Workspace:** the scope that holds users, workflows, and runs. There is one workspace for now.
