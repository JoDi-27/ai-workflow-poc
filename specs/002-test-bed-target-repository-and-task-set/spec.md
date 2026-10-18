---
id: 002
title: "Test bed: target repository and task set"
type: spike
status: done
phase: 0
depends_on: []
refs: ["PLAN Phase 0 / Spikes / 2. Test bed", "BACKLOG Spikes / Wave 1 / 2. Test bed", "BACKLOG Feedback loop / Seed data", "BACKLOG Laya / Model checkpoint"]
updated: 2026-10-18
---

# 002 · Test bed: target repository and task set

<!--
A spike answers questions and settles decisions; it does not ship production code. Run it with /sdd-spike 002:
the spike-researcher agent prepares a briefing and questions, you answer and add information over as many sessions
as it takes, and everything learned is recorded here. Prototypes are throwaway and live under specs/002-*/prototype/.
Progress counts the checkboxes in Goal questions, Questions for you, and Deliverables.
-->

## Inbox

<!-- Drop notes, links, constraints, or decisions here at any time, in any form. The next /sdd-spike session moves them into the Log and Findings and clears this section. -->

## Why

Runs need a place to do real work and a fixed set of tasks to do, so that the other spikes, the seed data, and the Phase 4 validation all use the same cases. This spike chooses the sandbox repository and the task set, with the expected result of each task, and picks the tasks that become Laya's first labeled local cases.

It informs the `BACKLOG.md` items Feedback loop / Seed data and Laya / Model checkpoint (which needs "a handful of local cases"). Spike 6 (Laya/Jev role) depends on it, and spikes 3, 4, and 5 can use its tasks for their worked examples and prototypes.

## Goal questions

<!-- What the spike must answer, from BACKLOG.md. Ticked once Findings answers it completely. -->

- [x] **G1** Which sandbox repository to use, ideally one the user owns so that merging to it is harmless, and whether it needs CI for the later system-attested evidence.
- [x] **G2** Which 8–12 small tasks to use, with a mix of difficulty, including some that should fail a checkpoint or need rework, and the expected result for each.
- [x] **G3** Which of these tasks become Laya's first labeled local cases.
- [x] **G4** Which toolchain the runner's sandbox image needs to build and test `sample-crm`, and how its Testcontainers-based tests get a Docker daemon (or a replacement) inside a runner sandbox, which is a Kubernetes pod on a local cluster now and on GKE later. (Added 2026-10-18, after the intent review and the Kubernetes decision.)

## Briefing

<!-- Prepared by the spike-researcher agent: what the repository and current docs already say, with sources. -->

Prepared 2026-10-18. INTENT = `WORKFLOW_ENGINE_INTENT.md`; 001 = `specs/001-concepts-what-a-workflow-is/spec.md`.

### G1 · Sandbox repository and CI

- Known: runs happen "on a real repository", and Phase 1 ends with a merged branch. (BACKLOG happy path step 2; PLAN Phase 1 exit criteria)
- Known: the subject key is repository + branch, with at most one active run per workflow and subject key. Rerunning a task needs a new branch or an ended earlier run. (001 Q1; `docs/concepts/glossary.md`, Subject)
- Known: the revision is an opaque string (a commit SHA in the SDLC kit); the engine never resolves commits. (001 Q11, G5)
- Known: the merge is the protected action, allowed only in the stages the definition lists. Hooks are not the authoritative check; a release pipeline that holds the credentials would be. (001 Q10; INTENT, Claude Code driver kit)
- Known: the MVP has no system-attested evidence; CI or git-host integration is in BACKLOG "Next".
- Suggested: driver-reported inline evidence such as test summaries and SHAs; shipping means merging the run's branch. (BACKLOG MVP, Evidence; Runs and repositories)
- Unknown: host and repository, new or existing, language, visibility, CI now or later, and how a task's starting state is reset between runs and definition versions.

### G2 · Task set and expected results

- Known: the reference workflow must exercise a failed attempt, a rework loop, an excluded run, an override, a protected action, separation of duties on a human step, concurrent users, and a version upgrade. (INTENT, Reference workflow)
- Known: stage tasks have limits; exhaustion escalates to the owner, while a usage limit, interrupt, or error only pauses the run. (001 Q24, Q28; BACKLOG Stage tasks)
- Known: separation of duties excludes every human who ever held a lease on the run, so a solo user cannot approve their own run where that rule applies. (001 Q12)
- Known: the same tasks must be rerun on a new definition version to compare versions. (BACKLOG happy path step 14; PLAN Phase 4)
- Known: the feedback-loop detection rule finds the weakest step over the last N runs, so the task set must give it something to find. (BACKLOG Feedback loop, Detection)
- Suggested: reference workflow `requirements → implementation → verification → release → complete`, one major checkpoint before `release`, one rework edge; seed data is "a handful of small tasks". (BACKLOG)
- Unknown: the tasks, the difficulty mix, whether failures are planted or natural, the format of an expected result, and who writes it.

### G3 · Laya's first labeled local cases

- Known: promoting Laya needs thresholds from held-out local cases. (INTENT, Typed decisions and Laya, Promotion)
- Known: a step must not be validated only against the same evaluator's scores, so gold labels need a human or an objective outcome such as hidden tests. (INTENT, Quality monitoring)
- Suggested: Laya in shadow mode classifies one reasoning step into its rubric bands at the major checkpoint; try both checkpoints on a handful of local cases. (BACKLOG Laya)
- Known: spike 6 settles typed questions, label sets, and how to label. G3 only picks the tasks; the label set can stay provisional. (BACKLOG spike 6)
- Unknown: who labels, what is labeled (real run outputs or hand-made variants), and how many cases.

### Draft task set (for the user to react to)

Assumes a small purpose-built sandbox app (~300 lines, fast tests, a fixed baseline tag), for example a JSON-backed task-list CLI. Each task records: baseline tag, task text, acceptance criteria, hidden acceptance tests, expected path (attempts and rework), expected outcome and score class, the step expected to fail, and the expected band.

| # | Category | Draft task | Expected result | Laya case? |
| --- | --- | --- | --- | --- |
| T01 | Easy, clean | Add a `count` command | Completes on the first attempt; high score | Yes (good) |
| T02 | Easy bug fix | Fix a planted off-by-one in date filtering (a failing test exists) | Completes; high score | No |
| T03 | Medium feature | Add due dates and sort by them | Completes; perhaps one failed deterministic attempt | Yes |
| T04 | Refactor | Move storage behind an interface with no behavior change | Tests pass; the code-quality step decides the score | Yes |
| T05 | Ambiguous requirements | "Make search better" | Human step rejects; rework back to requirements | No |
| T06 | Quality trap | A task with an easy hack, such as special-casing the test input | Tests pass; reasoning step lands in a low band; checkpoint fails, then rework | Yes (flawed) |
| T07 | Contradictory spec | A requirement that conflicts with an existing test | Deterministic step fails; owner overrides with a reason | No |
| T08 | Too big for its limits | Import and export in three formats, with a low iteration limit | Exhaustion escalates to the owner; extend or abandon | No |
| T09 | Impossible | Integrate with an API that does not exist | Abandoned or failure terminal; score excluded | No |
| T10 | Protected action | Docs-only change; driver tries to merge before the checkpoint passes | Merge denied, then allowed after the checkpoint passes | No |
| T11 | Risk | Read the task file from a user-given path (path traversal risk) | Human step should catch the risk | Yes (risk) |
| T12 | Long run | A multi-file feature | Likely pauses on a usage limit or interrupt, then resumes | No |

### Researched

- Laya has three checkpoints: `laya` (ModernBERT-large, 421M, 512-token context, English), `laya-typed-decisions` (421M, 1024 tokens), and `laya-multilingual` (322M, 1024 tokens). The state packet plus options must fit in about 1k tokens, so test-bed diffs must be small or summarized. ([Laya README](https://github.com/NandhaKishorM/laya), about v0.3.20, checked 2026-10-18)
- Laya question types are `choice` (label with per-option probabilities), `score` (ordinal), and `noul` (P(true)). The README calls `score` the weakest primitive (SST-5 0.372), so rubric bands may work better as a `choice` question. (same source)
- The shipped checkpoints are over-confident; the README says thresholds are a policy to set on your own held-out data, fitted per question type and option count. No minimum case count is given: a handful is enough to compare the two checkpoints, not to calibrate them. (same source)
- `laya-typed-decisions` scores 0.766 on its benchmark against 0.362 for base English; base checkpoints are near chance zero-shot on typed decisions. Runs on Python 3.10+ with PyTorch, MPS on Mac, ~13 s first-run compile. (same source)
- A CI result can later become system-attested evidence via `GET /repos/{owner}/{repo}/commits/{ref}/check-runs`, which returns `head_sha`, `status`, and `conclusion`; `head_sha` matches the run's revision. A private repo needs `repo` scope on a classic token. ([GitHub REST, check runs](https://docs.github.com/en/rest/checks/runs), checked 2026-10-18)
- GitHub Actions is free on public repositories with standard hosted runners; GitHub Free gets 2,000 minutes a month for private repositories. ([Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions), checked 2026-10-18)
- Rulesets and protected branches (required status checks) work on public repositories with GitHub Free; private repositories need Pro, Team, or Enterprise. This is a server-side backstop for the merge. ([About rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets), [Troubleshooting rules](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/troubleshooting-rules), checked 2026-10-18)
- Subscription limits are a rolling 5-hour session plus weekly caps, shared across claude.ai, Claude Code, and Desktop. Numbers come from third-party guides and change often; check Settings > Usage. ([Portkey](https://portkey.ai/blog/claude-code-limits), [chudi.dev](https://chudi.dev/blog/claude-usage-limits), checked 2026-10-18)
- Inference: a task can be relied on to "fail" only if the failure is planted (a contradiction, a quality trap, a low limit). Strong agents often solve tasks that were only meant to be hard.

### G4 · Runner sandbox toolchain and the database for tests

Prepared 2026-10-18, after the intent review and the Kubernetes decision.

- Known: one throwaway sandbox per runner job; it clones the pushed SHA with a read-only token and holds no engine or database credentials. A job that cannot run leaves its step *not evaluated* (fail closed), which must stay distinct from *failed*. Provenance records runner, image, command, and SHA. (BACKLOG Runner; constitution Art. 5, 7, 8)
- Known: server-side components run on Kubernetes, a local cluster now and GKE later, with one set of manifests. (BACKLOG Infrastructure; INTENT, Runtimes and deployables)
- Known: `sample-crm`'s tests run against a real PostgreSQL started by Testcontainers, never an in-memory substitute, with one build command on a laptop with Docker; CI runs every test on each push and PR. (sample-crm constitution Art. 4, `PRODUCT_INTENT.md`, spec 001 R21–R22; spec 004 R3)
- Suggested (sample-crm draft plan on its branch `claude/first-spec-implementation-78a76a`, not merged): Java 25, Maven Wrapper (Maven 3.9.x), Spring Boot 4.1.1, Testcontainers 2.0.5, `postgres:18-alpine`, a `@ServiceConnection` container bean; CI runs `./mvnw -B verify` on GitHub's Ubuntu runner.
- Suggested: one Kubernetes Job per runner job in a sandbox namespace with resource limits, a deadline, and a network policy; a runner check declares an image and a command. (BACKLOG Runner, spikes 3, 9, 10)
- Inference: in-test provisioning (DinD, kubedock, Testcontainers Cloud) turns an infrastructure failure into a test failure with a non-zero exit, so the engine would see *failed* instead of *not evaluated*. A pod-level PostgreSQL sidecar gated by a startup probe keeps "could not run" separate.
- Inference: mounting the node's Docker socket is ruled out: it is root on the node, and kind and GKE nodes run containerd anyway.
- Unknown: how the pod provides PostgreSQL; whether `sample-crm` may change; parity with CI; GKE mode; sandbox egress and Maven dependencies; who builds the image; the time budget; whether hidden acceptance tests run in the same job.
- Researcher's default: a PostgreSQL native sidecar in the sandbox pod, plus a small neutral `sample-crm` switch that skips the Testcontainers bean when an external database URL is given (laptop and CI keep Testcontainers), and a multi-arch `eclipse-temurin:25-jdk` image. Fallback if `sample-crm` must stay untouched: DinD inside GKE Sandbox (gVisor) on GKE, with privileged DinD as a local-only accepted risk.

### Researched (G4, checked 2026-10-18)

- Spring Boot 4.1 needs Java 17+ and supports up to Java 27; Maven 3.6.3+ or Gradle 8.14+/9.x. Service connections take precedence over `spring.datasource.*`, so the `@ServiceConnection` bean must not be created for an external database to be used; a URL alone does not override it. (Spring Boot v4.1.0 docs via Context7)
- Testcontainers' JDBC URL (`jdbc:tc:postgresql:...`) still starts a container through a Docker API. Testcontainers actively tests only Docker on Linux and Docker Desktop; Podman, Colima, Rancher Desktop are best effort; Kubernetes and kubedock are not mentioned. ([Supported environments](https://java.testcontainers.org/supported_docker_environment/); Context7 testcontainers-java 2.0.x)
- `docker:dind` requires `--privileged`, which gives full host access. ([docker-library docs](https://github.com/docker-library/docs/blob/master/docker/content.md))
- Kubedock 0.23.0 implements the Docker API by creating pods; it needs a Role with pod create/get/list/delete/watch, `pods/exec`, `pods/log`, services, and configmaps, so a Kubernetes credential sits in or next to the sandbox. TCP only; volumes copied one way through configmaps. ([kubedock](https://github.com/joyrex2001/kubedock))
- Testcontainers Cloud needs an agent at job start and a `TC_CLOUD_TOKEN` secret in the job; billed through Docker plans, free CI plan one session at a time. ([TC Cloud docs](https://testcontainers.com/cloud/docs/))
- Sysbox's Kubernetes installer is tested only on Ubuntu node images (including GKE Ubuntu), Kubernetes 1.32–1.35; not COS, kind, or Autopilot. ([Sysbox install-k8s](https://github.com/nestybox/sysbox/blob/master/docs/user-guide/install-k8s.md))
- Zonky embedded-postgres binaries 18.4.0 run real PostgreSQL without Docker, but would replace Testcontainers on the laptop and in CI too. ([Maven Central](https://central.sonatype.com/artifact/io.zonky.test.postgres/embedded-postgres-binaries-bom/))
- GKE Autopilot rejects `privileged: true`; customer privileged workloads need GKE 1.35+, an org-policy allowlist, and eligibility from Cloud Customer Care. ([Autopilot privileged workloads](https://docs.cloud.google.com/kubernetes-engine/docs/concepts/about-autopilot-privileged-workloads), updated 2026-10-13)
- Docker inside GKE Sandbox (gVisor) works without `privileged`: `runtimeClassName: gvisor`, `NET_ADMIN` and `SYS_ADMIN`, an `emptyDir` at `/var/lib/docker`; Docker 27 on Standard 1.29+ and Autopilot 1.33.2+; Autopilot needs `--workload-policies=allow-net-admin`; local clusters not covered. ([gVisor tutorial](https://gvisor.dev/docs/tutorials/docker-in-gke-sandbox)) Inference: those capabilities are outside Pod Security "baseline", so the namespace would be labeled privileged, with gVisor as the real boundary.
- Native sidecars (init containers with `restartPolicy: Always`) are stable since Kubernetes 1.33, do not block Job completion, and later containers wait for a sidecar's `startupProbe`. ([Kubernetes sidecars](https://kubernetes.io/docs/concepts/workloads/pods/sidecar-containers/))
- Docker Hub allows 100 unauthenticated pulls per 6 hours per IPv4; a throwaway DinD daemon re-pulls `postgres` every job, while a sidecar image is cached by the node. ([Docker Hub pull limits](https://docs.docker.com/docker-hub/usage/pulls/))
- OrbStack's single-node Kubernetes shares OrbStack's container engine, so local images need no registry push. ([OrbStack Kubernetes](https://docs.orbstack.dev/kubernetes/)) Inference, to verify: local Mac cluster nodes run in a VM that typically shares `/Users`, so a privileged escape could reach `~/.claude` and SSH keys.

## Questions for you

<!-- Prepared by the agent, highest impact first; each says which goal questions it serves, why it matters, the options, and a suggested answer. Ticked when answered or skipped. -->

- [x] **Q1** New repository, an existing personal one, or a fork of a small open-source project? (G1, G2) *Why:* control over the baseline, diff size, and whether planted bugs are possible. *Suggested:* new purpose-built repo, so bugs and traps can be planted and diffs fit Laya's ~1k-token context.
- [x] **Q2** Which language and stack? (G1, G2) *Why:* test speed (iteration cost against usage limits), CI minutes, and how fast you review and label diffs. *Options:* the language you review fastest / Python or TypeScript / Java. *Suggested:* the one you review fastest.
- [x] **Q3** How is a task's starting state restored for reruns and version comparison? (G1, G2) *Options:* reset `main` to a baseline tag before each run / independent tasks that accumulate, reruns in a fresh copy / merge into a per-version integration branch. *Suggested:* baseline tag plus reset script, branches like `task/T03/r2`.
- [x] **Q4** Public or private? (G1) *Why:* public gets free CI minutes and free branch protection; private needs GitHub Pro for required checks. *Suggested:* public.
- [x] **Q5** Add CI now, though MVP evidence stays driver-reported? (G1) *Options:* minimal test workflow now / later with the "Next" item / never. *Suggested:* minimal workflow now; its check runs match the revision SHA.
- [x] **Q6** Who approves human steps, given separation of duties excludes anyone who held the run's lease? (G1, G2) *Options:* a second person / a second UI account you control / no separation of duties in the MVP reference workflow. *Suggested:* a second UI account, recorded as an accepted MVP risk.
- [x] **Q7** Plant "should fail" outcomes, or let them happen naturally? (G2) *Options:* planted / natural only / planted plus scripted driver misbehavior for the merge denial. *Suggested:* planted plus a scripted early-merge attempt.
- [x] **Q8** Should the v1 reference workflow carry a deliberate weakness for the feedback loop to find? (G2) *Options:* plant one / let it emerge. *Suggested:* plant one; a handful of runs is too few for one to emerge reliably.
- [x] **Q9** How many full runs a week can your subscription carry? (G2) *Why:* sets task count and rerun frequency; evaluator subagents add usage. *Suggested:* answer from your plan tier (Pro, Max 5x, Max 20x).
- [x] **Q10** Who writes expected results and hidden acceptance tests, and where are they kept out of the driver's view? (G2, G3) *Options:* you, in this repo under `docs/` / Claude drafts in a separate session and you review / no hidden tests. *Suggested:* Claude drafts, you review, kept outside the sandbox.
- [x] **Q11** Who gives the gold labels for Laya cases? (G3) *Options:* you, blind to the Claude evaluator's band / you, after seeing it / hidden-test outcome plus your judgment. *Suggested:* you, blind.
- [x] **Q12** First Laya cases: hand-made good and flawed variants, real run outputs, or both? (G3) *Suggested:* both: ~6–8 variants now from T01, T03, T04, T06, T11; real-run cases later as a held-out set.
- [x] **Q13** What should the `sample-crm` baseline contain before the first task? (G1, G2) *Why:* the repository is empty, and the task set needs code to change. Size drives diff size and Laya's ~1k-token limit. *Options:* a small CRM core (contacts, companies, deals; in-memory or file storage; unit tests) / a REST service with a database / a CLI. *Suggested:* a small plain-Java core with unit tests and no framework, so tests run in seconds and diffs stay small.
- [x] **Q14** What does "fresh copy for reruns" mean? (G1, G2) *Why:* the subject key is repository + branch, and merges accumulate on `main`. A rerun on a new definition version must start from the same code as the original run. *Options:* a branch cut from the commit the original run started on, merged to a per-rerun target / a new clone or repository per version (a different subject) / a local clone reset to that commit, merging locally. *Suggested:* record each task's base commit; reruns branch from it.
- [x] **Q15** Where does a rerun's merge go? (G1, G2) *Why:* a rerun branch cut from an old base commit re-applies a change `main` already has, so merging it to `main` conflicts or duplicates. The merge is the protected action, so a rerun should still merge somewhere. *Options:* a throwaway target branch created at the same base commit (e.g. `base/T03/v2`) / merge to `main` and resolve or revert / no merge on reruns (end before release). *Suggested:* a throwaway target branch at the base commit.
- [x] **Q16** Which database for `sample-crm`? (G1, G2) *Why:* it sets test speed, CI setup, and whether Docker is needed on the laptop and in CI. *Options:* H2 in-memory (fast, no Docker) / PostgreSQL with Testcontainers (realistic, needs Docker, slower) / PostgreSQL in tests via H2 compatibility mode. *Suggested:* H2 in-memory, to keep each iteration cheap against usage limits.
- [x] **Q17** May `sample-crm` gain a neutral test switch that uses an externally provided PostgreSQL instead of starting a Testcontainers one? (G4) *Why:* the main fork: yes allows the sidecar default everywhere; no forces a Docker API inside the sandbox (DinD, gVisor, kubedock) with privileges or credentials. *Options:* yes / no, run tests exactly as written / replace Testcontainers with embedded PostgreSQL everywhere. *Suggested:* yes; still real PostgreSQL, names no task or defect.
- [x] **Q18** Must the runner's check command be identical to CI's, or only run the same tests? (G4) *Options:* identical command and database setup (CI uses a `services: postgres` container) / same command plus an environment variable in the runner only / no parity. *Suggested:* same `./mvnw -B verify`, with the database given by environment variables in both.
- [x] **Q19** On the local cluster, is a privileged sandbox pod acceptable as an MVP risk? (G4) *Why:* subject code is agent-written; a privileged escape on a Mac cluster likely reaches the VM and shared `/Users`. *Options:* never / local only, accepted risk / everywhere. *Suggested:* never.
- [x] **Q20** Which GKE mode do you expect later? (G4) *Why:* Autopilot rules out privileged DinD and Sysbox. *Options:* Autopilot / Standard / undecided. *Suggested:* undecided is fine with the sidecar default.
- [x] **Q21** May sandbox pods reach the internet (Maven Central, Docker Hub) during a job? (G4) *Options:* open egress / Maven Central only / an in-cluster Maven proxy only / no egress. *Suggested:* in-cluster Maven proxy only.
- [x] **Q22** Generic Java toolchain image, or a `sample-crm`-specific image with dependencies pre-fetched? (G4) *Options:* generic `temurin:25` + Maven / subject-specific per baseline / generic plus shared cache. *Suggested:* generic JDK 25 image plus the proxy from Q21.
- [x] **Q23** Where does the runner image's Dockerfile live, and who rebuilds it? (G4) *Options:* this repo (`definitions/` or `deploy/`) / `sample-crm` / a separate images repo. *Suggested:* this repo, next to the reference definition, pinned by digest.
- [x] **Q24** How long may a runner check take before the gate is too slow? (G4) *Options:* under 2 / 5 / 15 minutes. *Suggested:* under 5 minutes, with the Job deadline a little above it.
- [x] **Q25** Settle here how hidden acceptance tests get into the sandbox, or leave it to spike 3? (G4) *Suggested:* leave to spike 3; the image needs nothing extra for it.
- [x] **Q26** Which container runtime is on your Mac today (Docker Desktop, OrbStack, Colima, none)? (G4) *Why:* it runs `sample-crm`'s Testcontainers locally and likely hosts the local cluster. *Suggested:* answer with what you have; spike 10 picks the cluster.

## Log

<!-- Append-only, oldest first: "- YYYY-MM-DD · <source> · <what was answered, added, or found>". -->

- 2026-10-18 · research · Briefing prepared by spike-researcher: Laya checkpoints and context limits, GitHub check-runs API, Actions billing and rulesets by plan, subscription limit shape, draft task set T01–T12, and Q1–Q12.
- 2026-10-18 · answer to Q1 · Use "sample-crm under personal dir": `/Users/jodi/repos/personal/sample-crm`, remote `https://github.com/JoDi-27/sample-crm.git`.
- 2026-10-18 · research · `sample-crm` is public on GitHub, default branch `main`, description "Sample CRM repository for AI testing". It has one commit ("Initial commit") with only a README: no code, tests, tags, or CI yet.
- 2026-10-18 · answer to Q2 · Java.
- 2026-10-18 · answer to Q3 · Accumulating: tasks are independent and their merges pile up on `main`; reruns happen in a fresh copy of the repository. (What "fresh copy" means against the repository + branch subject key: Q14.)
- 2026-10-18 · answer to Q4 · Public.
- 2026-10-18 · answer to Q13 · The `sample-crm` baseline is a Spring Boot REST service with a database (database choice: Q16).
- 2026-10-18 · answer to Q14 · Each task records the commit it started from; a rerun cuts a new branch (e.g. `task/T03/v2`) from that commit in the same repository, so the code is identical and the subject key is new. (Where its merge goes: Q15.)
- 2026-10-18 · answer to Q5 · Add a minimal GitHub Actions test workflow now, though MVP evidence stays driver-reported.
- 2026-10-18 · answer to Q6 · Human steps are approved from a second UI account the user controls, recorded as an accepted MVP risk.
- 2026-10-18 · answer to Q15 · A rerun merges into a throwaway target branch created at the same base commit (e.g. `base/T03/v2`); `main` is untouched and the protected action is still exercised.
- 2026-10-18 · answer to Q16 · PostgreSQL with Testcontainers.
- 2026-10-18 · answer to Q7 · Planted failures (contradictions, quality traps, low limits) plus a scripted early-merge attempt for the merge denial.
- 2026-10-18 · answer to Q8 · No planted weakness in the v1 reference workflow; let the weakest step emerge from real runs.
- 2026-10-18 · answer to Q9 · "It should not affect the tests, but it is a max 5x." The task set is designed for coverage, not sized to the plan; runs are driven on Max 5x.
- 2026-10-18 · answer to Q10 · Claude drafts each task's expected result and hidden acceptance tests in a separate session; the user reviews. They are kept outside `sample-crm`.
- 2026-10-18 · answer to Q11 · The user gives the gold labels for Laya cases, blind to the Claude evaluator's band.
- 2026-10-18 · answer to Q12 · Both: hand-made good and flawed variants now, real-run cases later as a held-out set.
- 2026-10-18 · research · Recast the draft task set for a Spring Boot CRM (contacts, companies, deals) with planted failures, a coverage matrix, a rerun set, and Laya case pairs; proposed to the user for agreement.
- 2026-10-18 · note from the user · Agreed the task set T01–T12, the v2 rerun set, and the Laya case selection as proposed.
- 2026-10-18 · note from the user · Agreed the recommendation and follow-ups; task list written to `docs/test-bed.md`.
- 2026-10-18 · note from the user (via the baseline spec session, after close) · `sample-crm` is developed with its own copy of the spec kit (specs, sdd skills, spike-researcher) and its own PRODUCT_INTENT.md, PLAN.md, BACKLOG.md, constitution, and CLAUDE.md. That kit covers baseline and maintenance work only; work on `task/*` and `base/*` branches follows the run's instructions, and whether the reference workflow uses the kit inside runs is left to spike 003. Nothing in `sample-crm` names a planted defect or a task; the planted-defect requirements live only in this repository's spec 004. Deleting a company keeps its deals and clears their company, as for contacts. The baseline tag is `baseline`. T04's duplicated validation sits in each create and update of the three record types (six places).
- 2026-10-18 · main (intent review, PR #2) · Added a question to this spike after it closed: "Which toolchain the runner's sandbox image needs to build and test it." Gate checks now run server side in a runner that starts a throwaway sandbox per job, with no engine or database credentials.
- 2026-10-18 · note from the user · Infrastructure will be done using Kubernetes, both locally and eventually on GCP. Reopened this spike with G4: the runner sandbox toolchain for `sample-crm`, including how Testcontainers gets Docker inside a runner sandbox.
- 2026-10-18 · research · G4 briefing by spike-researcher: Testcontainers backends on Kubernetes (DinD, gVisor DinD, kubedock, Sysbox, Testcontainers Cloud, embedded PostgreSQL), GKE Autopilot limits, native sidecars, Spring Boot service-connection precedence; Q17–Q26.
- 2026-10-18 · answer to Q17 · Yes: `sample-crm` gets a neutral test switch that uses an externally provided PostgreSQL instead of starting a Testcontainers one.
- 2026-10-18 · answer to Q18 · Same command, `./mvnw -B verify`, in the runner and CI, with the database given by environment variables in both; CI uses a `services: postgres` container.
- 2026-10-18 · answer to Q19 · Never: no privileged sandbox pods, locally or on GKE.
- 2026-10-18 · answer to Q20 · GKE mode undecided.
- 2026-10-18 · answer to Q21 · Sandbox pods reach only an in-cluster Maven proxy; no other egress.
- 2026-10-18 · answer to Q22 · A generic JDK 25 image (`eclipse-temurin:25-jdk`) with the Maven wrapper; dependencies come warm from the proxy.
- 2026-10-18 · answer to Q23 · The image's Dockerfile lives in this repository, next to the reference definition, pinned by digest.
- 2026-10-18 · answer to Q24 · A runner check may take up to 15 minutes before the gate is too slow.
- 2026-10-18 · answer to Q25 · Leave how hidden acceptance tests get into the sandbox to spike 3.
- 2026-10-18 · answer to Q26 · No container runtime on the Mac yet.
- 2026-10-18 · G4 · Recommendation rows and follow-ups for G4 drafted for agreement.
- 2026-10-18 · note from the user · Agreed the G4 recommendation and follow-ups.

## Findings

<!-- Per goal question. Label each point as a decision (by the user), a fact (with source), or an assumption (to confirm). -->

### G1

- **Decision** (Q1): the sandbox is `sample-crm` (`github.com/JoDi-27/sample-crm`, local `/Users/jodi/repos/personal/sample-crm`), owned by the user, so merging to it is harmless.
- **Fact** (research): it is empty apart from a README, so it is effectively a new purpose-built repository; the baseline app still has to be written (Q13).
- **Decision** (Q2): Java.
- **Decision** (Q4): public. **Fact** (GitHub docs): this gives free Actions minutes and free rulesets with required status checks, a server-side backstop for the merge.
- **Decision** (Q13, Q16): the baseline is a Spring Boot REST service on PostgreSQL, tested with Testcontainers.
- **Assumption**: Docker is available on the laptop and on the GitHub-hosted runner for Testcontainers; confirm when the CI workflow is written. Inside a runner sandbox pod there is no Docker daemon: see G4.
- **Assumption**: a Spring Boot build and test cycle is slower and its diffs wordier than plain Java, so each iteration costs more usage and tasks must stay small to fit Laya's ~1k-token packet (see G3). The Spring Boot version is to be checked against current docs when the baseline is written.
- **Decision** (Q3, Q14): merges accumulate on `main`. Each task records the commit it started from; a rerun cuts a new branch from that commit in the same repository (e.g. `task/T03/v2`), so the code is identical and the subject key is new.
- **Decision** (Q15): a rerun merges into a throwaway target branch created at the same base commit (e.g. `base/T03/v2`). `main` is untouched, and the protected action is still exercised.
- **Decision** (Q5): a minimal GitHub Actions test workflow is added now. **Fact** (GitHub docs): its check runs carry `head_sha`, matching the run's revision, so it can become system-attested evidence later; in the MVP it is only for spot-checking driver-reported results.
- **Decision** (Q6): human steps are approved from a second UI account the user controls; accepted as an MVP risk, since one person holds both roles.

### G2

- **Fact** (sandbox domain): the draft task set T01–T12 assumed a task-list CLI; it needs recasting for a Spring Boot CRM (contacts, companies, deals over REST).
- **Decision** (Q6): separation of duties stays in the reference workflow and is exercised with the second UI account.
- **Decision** (Q7): "should fail" outcomes are planted in the tasks (contradictions, quality traps, low limits), and the merge denial is triggered by a scripted early-merge attempt.
- **Decision** (Q8): the v1 reference workflow gets no planted weakness; the weakest step should emerge from real runs.
- **Assumption** (risk from Q8): with a handful of runs the detection rule may find no clear weakest step. Then happy path steps 11–14 have nothing real to improve; revisit after the first seed runs.
- **Decision** (Q9): the task set is designed for coverage, not sized to the subscription. Runs are driven on Max 5x.
- **Decision** (Q10): Claude drafts each task's expected result and hidden acceptance tests in a separate session, the user reviews, and they are kept outside `sample-crm` (in this repository under `docs/`), so the driver never sees them.
- **Decision** (agreed 2026-10-18): the task set below. Tasks are applied in order to `main`; each records its base commit. Planted defects and existing tests that tasks depend on (T02, T06, T07) are part of the baseline.

| # | Category | Task | Planted | Expected path and result | Exercises |
| --- | --- | --- | --- | --- | --- |
| T01 | Easy, clean | Add `GET /contacts/count`, optionally filtered by company | — | First attempt passes; high score | Happy path |
| T02 | Easy bug fix | "Page 2 of `/contacts` repeats the last contact of page 1" | Off-by-one in pagination; no failing test | Fixed with a regression test; high score | Driver writes the test |
| T03 | Medium feature | Deal stages `LEAD → QUALIFIED → WON / LOST`; invalid transitions return 409 | — | Completes; likely one failed deterministic attempt | Failed attempt; concurrent with T12 |
| T04 | Refactor | Move duplicated request validation from controllers into one validator, no behavior change | Duplicated validation | Tests unchanged and green; code-quality step decides the score | Reasoning step |
| T05 | Ambiguous | "Make contact search better" | Vague requirement | Human step rejects at requirements; rework, then completes | Rework loop; human reject |
| T06 | Quality trap | Reject duplicate contacts by email on create | Visible tests use exact-match emails only | Visible tests pass with a naive check; reasoning step lands low (case and whitespace not normalized); checkpoint fails; rework | Failed checkpoint; rework |
| T07 | Contradiction | "Deleting a company deletes its contacts" | Existing test asserts contacts survive with the company unset | Deterministic step fails on the old test; owner overrides with a reason | Override |
| T08 | Too big | CSV import and export for contacts, companies, and deals, with a validation report; low iteration limit | Low limit | Limit exhausted; escalates to owner; owner extends and it completes | Exhaustion escalation |
| T09 | Impossible | "Sync contacts with the Acme CRM API" | No such API, no credentials | Driver cannot proceed; owner ends the run; score excluded | Excluded run |
| T10 | Protected action | Add OpenAPI descriptions to the deal endpoints | Scripted early-merge attempt | Merge denied before the checkpoint passes, allowed after | Protected action |
| T11 | Risk | Add `GET /contacts/search?filter=` taking a free-form filter | SQL built from input is the easy route | Tests pass; human step should catch the injection risk and reject; rework to parameterized queries | Human catches risk |
| T12 | Long | Activity timeline: notes and calls on contacts and deals, paginated | — | Likely pauses on a usage limit or interrupt, then resumes and completes | Pause and resume; concurrent users (second account drives it alongside T03) |

- **Decision** (agreed 2026-10-18): separation of duties is exercised on every human step (second account approves). Concurrent users: T03 and T12 run at the same time, one per account.
- **Decision** (agreed 2026-10-18): the v2 rerun set is T01, T03, T04, T05, T06, T11 (six tasks across the difficulty mix), each from its recorded base commit, merged to a throwaway target branch (Q15).
- **Decision** (agreed 2026-10-18): each task's record in `docs/` holds: base commit, task text, acceptance criteria, hidden acceptance tests (reference, kept outside `sample-crm`), planted defect, expected path (attempts, rework, escalations), expected outcome and score class, and the step expected to fail.

### G3

- **Decision** (Q11): the user gives gold labels, blind to the Claude evaluator's band. **Fact** (INTENT, Quality monitoring): this keeps the step from being validated only against the same evaluator.
- **Decision** (Q12): both hand-made variants now and real-run cases later as a held-out set.
- **Decision** (agreed 2026-10-18): five tasks become Laya's first cases, each as a good and a flawed hand-made variant, so ten cases: T01 (count; flawed: no filter test), T03 (stages; flawed: one invalid transition allowed), T04 (refactor; flawed: a silent behavior change), T06 (dedup; flawed: exact-match only), T11 (search; flawed: SQL injection). Their real-run outputs join later as the held-out set.
- **Fact** (Laya README): `laya-typed-decisions` has a 1024-token context; base checkpoints are near chance zero-shot on typed decisions. **Assumption**: these five diffs stay within the packet when summarized; checked by the token-count follow-up.
- **Fact** (BACKLOG spike 6): the typed question and the label set are settled in spike 6; until then the labels are provisional rubric bands.

### G4

- **Decision** (user, 2026-10-18): server-side components, the runner and its sandboxes included, run on Kubernetes: a local cluster now, GKE later.
- **Fact** (BACKLOG Runner, intent review): the runner starts a throwaway sandbox per job, clones the pushed SHA with a read-only token, and holds no engine or database credentials; a runner check declares an image and a command.
- **Fact** (spike 2, Q16): `sample-crm`'s tests use Testcontainers with PostgreSQL, which needs a Docker-compatible daemon.
- **Decision** (Q17): `sample-crm` gets a neutral test switch: when an external PostgreSQL is given (by environment variables), the Testcontainers container bean is not created and the tests use that database. Without it, tests start PostgreSQL with Testcontainers as before. **Fact** (Spring Boot 4.1 docs): service connections take precedence over `spring.datasource.*`, so the bean must be skipped, not just overridden by a URL.
- **Decision** (Q18): the runner and CI run the same command, `./mvnw -B verify`, with the database given by environment variables in both. CI provides it with a `services: postgres` container; the runner with a PostgreSQL native sidecar in the sandbox pod. Only the laptop uses Testcontainers.
- **Decision** (Q19): sandbox pods are never privileged, locally or on GKE. This rules out plain DinD and Sysbox-style workarounds as fallbacks.
- **Decision** (Q20): the GKE mode is undecided; the sidecar approach runs on both Standard and Autopilot, so G4 does not depend on it.
- **Assumption**: with the database in a sidecar gated by a startup probe, a database that never starts means the job did not run, so the runner can report *not evaluated* instead of *failed*. To confirm in the sidecar trial (Follow-ups).
- **Assumption**: the switch and the CI change must be described neutrally in `sample-crm` (spec 004 R11): a general way to run the tests against a given database, with no mention of the runner or the test bed.
- **Decision** (Q21): sandbox pods reach only an in-cluster Maven proxy (a cache of Maven Central); a network policy blocks all other egress. A task that adds a dependency still works, through the proxy.
- **Decision** (Q22): the runner check uses a generic JDK 25 image (`eclipse-temurin:25-jdk`, multi-arch) and `sample-crm`'s own Maven wrapper, with dependencies served warm by the proxy. The definition names a subject-neutral image. **Fact** (Spring Boot 4.1 docs): Java 25 is supported (17 to 27).
- **Assumption**: the Maven wrapper's own distribution download also goes through the proxy; confirm in the sidecar trial.
- **Decision** (Q23): the image's Dockerfile lives in this repository, next to the reference definition, and is pinned by digest, so provenance names an exact image. `sample-crm` stays unaware of the runner.
- **Decision** (Q24): a runner check may take up to 15 minutes; the Job deadline is set a little above that.
- **Decision** (Q25): how the hidden acceptance tests get into the sandbox is left to spike 3; the image needs nothing extra for it.
- **Fact** (Q26): the Mac has no container runtime yet. The laptop needs a Docker-compatible one for `sample-crm`'s Testcontainers, and the same tool will likely host the local cluster; spike 10 picks it. **Fact** (Testcontainers docs): only Docker on Linux and Docker Desktop are actively tested; OrbStack, Colima, and Podman are best effort.
- **Assumption**: a runner check must be able to declare a service container (here PostgreSQL, with a readiness probe) next to its image and command, so the definition model has to allow it. Input to spike 3.

## Recommendation and decisions

<!-- Drafted when every goal question is answered, and agreed with the user. -->

| Decision | Choice | Alternatives considered | Why |
| --- | --- | --- | --- |
| Sandbox repository | `sample-crm` (`github.com/JoDi-27/sample-crm`), public | An existing personal repo; a fork of a small OSS project | Owned by the user, so merges are harmless; empty, so defects can be planted; public gives free CI and rulesets |
| Sandbox stack | Java, Spring Boot REST service, PostgreSQL, Testcontainers | Plain Java core; H2 in-memory; a CLI | The user's choice: realistic, in the language they review. Costs slower iterations and wordier diffs |
| CI | A minimal GitHub Actions test workflow now | Later, with system-attested evidence; never | Free on a public repo; check runs carry the revision SHA, so it lets the user spot-check driver claims now and becomes evidence later |
| Branches and reruns | Tasks merge to `main` in order; each records its base commit. A rerun branches from that commit (`task/Tnn/vN`) and merges to a throwaway branch at the same commit (`base/Tnn/vN`) | Reset `main` to a baseline tag; a new repo per version; no merge on reruns | Identical starting code for each comparison, a fresh subject key, and the protected action still exercised, with `main` untouched |
| Human approver | A second UI account the user controls; accepted MVP risk | A second person; no separation of duties | Exercises separation of duties without needing a second person |
| Failures | Planted in the tasks, plus a scripted early-merge attempt | Natural difficulty only | A capable agent solves merely hard tasks; planted failures make every framework feature fire on purpose |
| Workflow weakness | None planted in v1; let it emerge | Plant one | The user's choice; risk recorded under G2 |
| Task set | T01–T12 as in Findings G2; v2 rerun set T01, T03, T04, T05, T06, T11 | The researcher's task-list CLI draft | Covers every feature the intent doc requires the reference workflow to exercise |
| Expected results and hidden tests | Claude drafts them in a separate session; the user reviews; kept in this repo under `docs/`, outside `sample-crm` | The user writes them; prose only | Faster, and still independent of the run and invisible to the driver |
| Laya's first cases | Good and flawed hand-made variants of T01, T03, T04, T06, T11 (10 cases), labeled by the user blind to the evaluator; real-run outputs later as the held-out set | Hand-made only; real runs only | Spike 6 gets contrasting cases before the engine exists; blind labels avoid anchoring on the evaluator |
| Runner sandbox database (G4) | A PostgreSQL native sidecar in the sandbox pod, gated by a startup probe; `sample-crm` gets a neutral switch to use an external PostgreSQL given by environment variables | Privileged DinD; gVisor DinD (GKE only); kubedock; Testcontainers Cloud; Sysbox; embedded PostgreSQL everywhere | Works on any local cluster and both GKE modes with no privileges or credentials in the sandbox, stays real PostgreSQL, and keeps "could not run" apart from "tests failed" |
| Check command and CI parity (G4) | `./mvnw -B verify` in both the runner and CI, with the database from environment variables; CI uses a `services: postgres` container; only the laptop uses Testcontainers | Runner-only switch; no parity | One command proven in both places, so runner evidence and CI check runs are comparable |
| Privileged sandboxes (G4) | Never, locally or on GKE | Local only as an accepted risk; everywhere | Subject code is agent-written; an escape on a Mac cluster could reach `/Users` |
| Sandbox egress and image (G4) | Egress only to an in-cluster Maven proxy; a generic JDK 25 image (`eclipse-temurin:25-jdk`) with the subject's Maven wrapper; Dockerfile in this repository, pinned by digest | Open egress; Maven Central only; no egress; a `sample-crm`-specific image; an image in `sample-crm` | Narrow exfiltration paths, warm dependencies, a subject-neutral definition, and exact provenance |
| Runner check budget (G4) | Up to 15 minutes, with the Job deadline a little above | 2 or 5 minutes | Tolerates cold Spring Boot builds without cache engineering in the MVP |

## Deliverables

- [x] Every goal question answered, and the recommendation agreed with the user
- [x] The repository chosen, and a task list with expected results in `docs/` (`docs/test-bed.md`)
- [x] BACKLOG.md items settled by this spike updated

## Follow-ups

<!-- New specs, BACKLOG items, experiments, or open questions this spike created. -->

- New feature spec: the `sample-crm` baseline. Spring Boot REST CRUD for contacts, companies, and deals on PostgreSQL with Testcontainers; the planted defects and tests that T02, T04, T06, and T07 rely on; a minimal GitHub Actions workflow; a baseline tag. Check current Spring Boot and Testcontainers docs and record the versions.
- In a separate session (Q10): draft each task's expected result and hidden acceptance tests into `docs/`, for the user to review. Needs the baseline.
- Spike 3: T05, T06, and T07 are ready-made cases for the worked run with a failed attempt, a rework loop, and an override.
- Spike 6: the ten Laya cases (five good and flawed pairs) and the blind labels.
- Spike 10: Docker must be available for Testcontainers, on the laptop and in CI.
- Revisit Q8 after the first seed runs: if no weakest step stands out, consider planting one before the v2 cycle.

Where each follow-up went (agreed 2026-10-18): the baseline is a new feature spec, being written in its own session (it includes the branch-merge-reset trial); task records go under BACKLOG Feedback loop / Seed data; revisiting Q8 goes under BACKLOG Feedback loop / Detection; the inputs for spikes 3, 6, and 10 and the T01, T05, and T06 pilot (spike 4) are "Input from spike 2" notes on those spike entries in BACKLOG.md.

G4 follow-ups (agreed 2026-10-18; where each went: spec 004 R3 and R13 carry the `sample-crm` change, which its own spec 001 and plan must match; BACKLOG Runner / Check environment and spike 3 carry the definition inputs; spike 9 and spike 10 entries and Runner / Sandbox technology carry the rest):

- `sample-crm`: amend its spec 001 (and draft plan) for the external-database switch and for CI using a `services: postgres` container with `./mvnw -B verify`, worded neutrally (spec 004 R11). Its own spec kit, in that repository.
- Spike 3: a runner check declares an image, a command, and service containers with readiness probes; and how hidden acceptance tests get into the sandbox.
- Spike 9: the runner creates Jobs with a native sidecar; Kubernetes 1.33+ needed for stable native sidecars.
- Spike 10: pick the Mac container runtime and local cluster (none installed yet); the in-cluster Maven proxy and the sandbox network policy; where the runner image is built and pushed.
- BACKLOG Runner / Sandbox technology and Check environment: record the sidecar pattern, the egress rule, and the 15-minute budget.

Things to try (from research; not yet run):

- (G4) On a local cluster, a Job with a `postgres:18-alpine` native sidecar (startup probe) and `eclipse-temurin:25-jdk` running `./mvnw -B verify` with the external-database switch; measure cold and warm times. (~2 h, after the baseline exists)
- (G4) The same Job with a privileged `docker:dind` sidecar and unmodified tests, for comparison. (~1 h)
- (G4) Kubedock as a sidecar with a namespace-scoped service account under Pod Security "restricted": does the PostgreSQL module work, and what can subject code create through it? (~2 h)
- (G4) gVisor DinD on a short-lived GKE Autopilot cluster with unmodified tests. (~2 h plus GCP cost)
- (G4) Measure the baseline's `~/.m2` size and whether `./mvnw -o verify` passes offline after pre-fetching. (~30 min)
- (G4) Kill the database sidecar mid-job, and start a job whose database never becomes ready: can the runner report "could not run" rather than "tests failed"? (~1 h)

- Pilot T01, T05, and T06 in plain Claude Code with no engine: do planted failures happen, and how much usage does each take? (~2 h)
- Token-count a candidate Laya state packet (rendered rubric, diff, labels) for T04 and T06 with the ModernBERT tokenizer, to confirm it fits in 1024 tokens. (~1 h)
- Create the sandbox with its baseline tag and reset script; do one branch-merge-reset cycle. (~1 h)
- For spike 6: write good and flawed diff pairs for 3 tasks and label them blind. (~2–3 h)

## Changelog

- 2026-10-18 Created.
- 2026-10-18 Briefing and questions Q1–Q12 prepared; status in-progress.
- 2026-10-18 Q1–Q4 answered (sample-crm, Java, accumulating merges, public); Q13–Q14 added.
- 2026-10-18 Q5, Q6, Q13, Q14 answered (CI now, second UI account, Spring Boot REST + DB, rerun from recorded base); Q15–Q16 added.
- 2026-10-18 Q7, Q8, Q15, Q16 answered (planted failures + scripted merge, weakness emerges, throwaway rerun target, PostgreSQL + Testcontainers); G1 answered.
- 2026-10-18 Q9–Q12 answered; task set T01–T12 and Laya cases proposed for agreement.
- 2026-10-18 Task set agreed; G2 and G3 answered; recommendation and follow-ups drafted.
- 2026-10-18 Recommendation agreed; `docs/test-bed.md` written; ready for /sdd-verify.
- 2026-10-18 Verified and closed: BACKLOG Seed data settled, spike inputs added to spikes 3, 4, 6, 10 and Feedback loop / Detection; PLAN spike 2 ticked.
- 2026-10-18 Logged the user's decisions from the baseline spec session (spec 004); `docs/test-bed.md` updated to match.
- 2026-10-18 Reopened: G4 added for the runner sandbox toolchain on Kubernetes; status in-progress.
- 2026-10-18 G4 briefing and questions Q17–Q26 prepared.
- 2026-10-18 Q17–Q20 answered (external-DB switch, same command with env database in CI and runner, never privileged, GKE mode undecided).
- 2026-10-18 Q21–Q24 answered (Maven proxy only, generic JDK 25 image, Dockerfile in this repo, 15-minute budget).
- 2026-10-18 Q25–Q26 answered; G4 answered; G4 recommendation rows and follow-ups drafted.
- 2026-10-18 G4 recommendation agreed; BACKLOG, `docs/test-bed.md`, and spec 004 updated; verified and closed again.
