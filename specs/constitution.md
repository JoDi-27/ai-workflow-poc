# Constitution

The non-negotiable rules every spec and plan is checked against. Each plan has a "Constitution check" that names the articles it touches.

`WORKFLOW_ENGINE_INTENT.md` stays the source of product intent; the product articles below distill the parts of it that constrain day-to-day design. To change an article, record the decision in the amendments log at the bottom, and update the intent doc if the product intent changed too.

## Product articles

1. **The engine is the sole authority.** Only the engine commits state, applies pass conditions, computes scores, and writes to persistence. The UI and drivers are API clients and never touch storage.
2. **Humans decide human steps.** Human decisions and definition approvals are accepted only from human-authenticated UI sessions. Driver credentials never carry approval scopes. A model output (driver agent or Laya) may inform a human step but never satisfies or removes one.
3. **The engine is neutral.** No SDLC concepts and no driver-provider concepts in engine code. They live in workflow definitions and driver kits.
4. **Drivers stay subscription-compatible.** Nothing depends on API-key orchestration, unattended model invocation, or the engine invoking a driver's model. Engine-side intelligence comes only from DecisionEngine providers and deterministic code.
5. **Fail closed.** An unreachable engine, stale state, or an unavailable evaluator or decision provider never lets a protected action or a required step pass.
6. **Evidence over prose.** Deterministic steps are checked by code against evidence with recorded provenance. Reasoning steps are scored by a fresh-context evaluator against the step's rubric. Evaluators report raw results; the engine alone applies thresholds and computes points.
7. **Pinned and replayable.** Every run is pinned to a definition version. State changes and their events are written in the same transaction. *Not evaluated*, *failed*, and *passed* stay distinct, and so do *excluded* and *low-scoring* runs.
8. **Record what cannot be enforced.** The developer's machine is trust-based: record what the driver does so it can be audited, rather than pretending to enforce it.

## Engineering articles

9. **Happy path first.** Build the simplest version that moves a `BACKLOG.md` happy path step forward. Anything else goes to `BACKLOG.md` "Next", not into the current spec.
10. **Tests prove acceptance criteria.** Every acceptance criterion maps to an automated test or a recorded manual check. A task is done only when its check passes.
11. **Small, traceable increments.** One spec is one reviewable slice (a few days at most; split it otherwise). One task is about one commit, and commit messages start with the spec and task ID, for example `[003] T2 Add run state table`.
12. **Specs are living.** When implementation shows the spec or plan is wrong, update them first, then the code. Code never silently diverges from its spec.
13. **Decisions are written down.** Every choice between real alternatives gets a row in the plan's Decisions table (or a spike's Recommendation). Choices that settle a `BACKLOG.md` item also update it in place.
14. **Verify current docs.** Before using a library, SDK, or tool API, check its current documentation rather than relying on memory, and note the version in the plan.

## Amendments

| Date | Article | Change | Why |
| --- | --- | --- | --- |
