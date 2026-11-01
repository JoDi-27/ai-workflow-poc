---
id: 014
title: "Local setup"
type: spike
status: done
phase: 0
depends_on: [010, 012, 013]
refs: ["PLAN Phase 0 / Spikes / 10. Local setup", "BACKLOG Spikes / Wave 5 / 10. Local setup", "BACKLOG MVP / Local cluster", "BACKLOG MVP / Sandbox technology"]
updated: 2026-10-27
---

# 014 · Local setup

<!--
A spike answers questions and settles decisions; it does not ship production code. Run it with /sdd-spike 014:
the spike-researcher agent prepares a briefing and questions, you answer and add information over as many sessions
as it takes, and everything learned is recorded here. Prototypes are throwaway and live under specs/014-*/prototype/.
Progress counts the checkboxes in Goal questions, Questions for you, and Deliverables.
-->

## Inbox

<!-- Drop notes, links, constraints, or decisions here at any time, in any form. The next /sdd-spike session moves them into the Log and Findings and clears this section. -->

## Why

<!-- The decision this spike unblocks, and the BACKLOG.md MVP items it settles. -->

The Phase 0 scaffolding cannot start until we know how the whole system runs on one laptop. This spike designs that: a local Kubernetes cluster for development and the MVP, set up so the same manifests carry over to GKE later. It settles the BACKLOG.md MVP items Local cluster and Sandbox technology (the rest of it, after spike 012). It builds on the architecture (spec 010, `docs/architecture/`), the stack (spec 012), and the engine design (spec 013, `docs/engine/`). The scaffolding then implements its manifests and README section.

## Goal questions

<!-- What the spike must answer, from BACKLOG.md. Ticked once Findings answers it completely. -->

- [x] **G1** Which local cluster (for example kind, k3d, minikube, or the cluster built into Docker Desktop or OrbStack), and how are manifests packaged (plain manifests, Kustomize, or Helm) so that one set serves the local cluster and GKE?
- [x] **G2** What runs in the cluster and what runs natively? (The Laya service is out of the MVP: spike 010, Q27.)
- [x] **G3** How does the runner start sandbox pods (namespaces, RBAC, network policy, resource limits), and how does it get the repository read token and the `setup-token` subscription token as Kubernetes secrets without exposing them to sandboxes running subject code? (Spec 010 Q10 already accepts that evaluator sandboxes hold the `setup-token`; what is open is keeping both tokens away from check sandboxes, which run subject code, and keeping the repository token away from subject code once the clone step is done.) Includes the Job classification prototype (spec 010 Q13) and the repeated Job name AlreadyExists check (Q16).
- [x] **G4** Networking: how does Claude Code on the host reach the engine's MCP endpoint and HTTP API (ingress or port forwarding), and how does its telemetry exporter reach the collector?
- [x] **G5** How is the driver kit plugin installed from the local repository, and how is its token configured?
- [x] **G6** Bootstrapping: creating the human UI users (an owner and an approver) and a driver token, loading the reference workflow, seeding data, and resetting to a clean state.
- [x] **G7** The development loop: running the engine and UI from the IDE with hot reload while the other components run in the cluster, or a tool that syncs code into the cluster.

## Briefing

<!-- Prepared by the spike-researcher agent: what the repository and current docs already say, with sources. -->

### Draft setup to react to

**[forced]** = settled by an earlier spike; **[open]** = a proposal, confirmed or replaced through the questions below.

- **Runtime:** Docker Desktop on the Mac, also used by Testcontainers. [forced: spec 012 G9, spec 002 Q26]
- **Cluster:** k3d with a pinned k3s image, 1.35 or later. [open: Q1] Alternatives: Docker Desktop's kind mode, or upstream kind 0.33.
- **Packaging:** Kustomize `deploy/base` with overlays `deploy/overlays/local` and `deploy/overlays/gke`. [open: Q13]
- **Namespaces:** [open: Q10]
  - `awf`: engine (1 replica), mcp-gateway (1 replica), analysis, ui, collector, postgres (StatefulSet with a PVC; schemas `engine` and `analysis`), pubsub-emulator.
  - `awf-runner`: runner, maven-proxy.
  - `awf-sandbox`: Jobs only.
  - Namespace and Service names are the same in both overlays, so DNS names carry over to GKE.
- **Engine:** one replica. [forced: spec 013 Q10] **MCP gateway:** one replica. [forced: spec 012]
- **Collector:** `otelcol-contrib` v0.163.1, pinned by digest. [forced: spec 012]
- **Broker:** the Pub/Sub emulator, topics and subscriptions created idempotently on every start. [forced: spec 012 G6]
- **Laya:** no `laya-serve`. [forced: spec 010 Q27]
- **Sandboxes:** one Job per runner job, named after the engine's job ID. [forced: AD-16]
  - Services run as native sidecars; pods are never privileged. [forced: spec 002]
  - The runner's Role is bound only in `awf-sandbox`. [forced: spec 012 G5]
  - Default-deny egress: check pods reach only DNS and maven-proxy, evaluator pods only DNS and Anthropic. [forced: spec 010 Q10, spec 002 Q21]
  - `automountServiceAccountToken: false`, Pod Security `restricted`, a LimitRange and a ResourceQuota. [open: Q12]
- **Secrets:** the repository token only in the clone init container [forced: AD-11]; the `setup-token` only in the evaluator's main container [forced: spec 010 Q10]; both as static Secrets in `awf-sandbox` [open: Q6].
- **Exposure:** one localhost origin through k3d's bundled Traefik, plus port 4318 for the collector. Routes: `/` UI, `/mcp` gateway, `/v1` engine (hooks and UI API), `/analysis` analysis. [open: Q4]
- **Images:** check images on GHCR, pinned by digest [open: Q3]; service images built with Jib or buildpacks into the k3d registry [open].
- **Kit:** a local marketplace at `driver-kits/`, installed at project scope [open]; the driver token from an untracked `.local/driver-token` [open: Q8].
- **Bootstrap:** an engine `bootstrap` profile [open: Q9] that creates the owner and approver [forced: spec 003] and a driver token, and loads `definitions/sdlc-reference.yaml` as the initial version [forced: spec 003].
- **Reset:** deletes the PostgreSQL PVC and keeps the Maven cache. [open: Q11]
- **Dev loop:** engine and UI run from the IDE against the cluster: Spring devtools restart for the engine, the Vite dev server proxying `/v1` and `/analysis` for the UI, the gateway pointed at `host.docker.internal`. [open: Q5]

### G1 · Cluster and packaging

- Kubernetes locally and on GKE later, with the same manifests. (BACKLOG Local setup; user, 2026-10-18)
- Sandboxes are Kubernetes Jobs. The GKE target is GKE Sandbox (gVisor), which needs CPU and memory limits on every container, sidecars included, and a NetworkPolicy blocking the metadata server. (spec 010 Q25; `docs/architecture/README.md` "Runner interface")
- Native sidecars need 1.33 or later; spec 012 assumed 1.35–1.37. (spec 002 G4; spec 012 Researched)
- Docker Desktop runs Testcontainers 2.0.5 on the Mac; the runtime serves both the cluster and Testcontainers. (spec 012 G9)
- The GKE mode (Autopilot or Standard) is undecided. (spec 002 Q20)
- The engine reads operational settings from a ConfigMap and secrets from Kubernetes Secrets. (spec 010 Q21)
- Open: the cluster, the packaging, image build and registry, and the namespaces other than the sandbox one (`docs/architecture/README.md` leaves them to this spike).

### G2 · In the cluster or native

- Workloads: engine, MCP gateway, analysis, UI, Pub/Sub emulator, one PostgreSQL instance with two schemas and separate credentials, runner, Maven proxy, collector, and the sandbox namespace. (spec 010 Q20–Q23)
- No Laya in the MVP. (spec 010 Q27)
- Native on the Mac: Claude Code with the kit, the browser, Testcontainers containers (beside the cluster), and the Maven build. (spec 012 Q12)
- The build uses the project's own Maven settings pointing at Maven Central, and pins JDK 25. (spec 013 Q16)
- Open: what runs natively while developing (G7).

### G3 · Sandboxes, RBAC, network policy, secrets

- The runner holds the repository read token and the `setup-token`; its service account may only create and delete Jobs and pods in the sandbox namespace. (`docs/architecture/README.md` "Trust boundaries")
- The fine-grained read-only repository token is used in a clone step before any subject code runs, for example an init container. (spec 010 Q11, AD-11)
- Evaluator sandboxes hold the `setup-token` (an accepted MVP risk), reach only Anthropic, and run Claude Code with read-only tools and `--restricted --safe-mode --strict-mcp-config`. (spec 010 Q10; spec 005)
- Check sandboxes reach only the Maven proxy, never the collector. (spec 002 Q21; spec 006)
- The check image is `eclipse-temurin:25-jdk` with the subject's wrapper; its Dockerfile lives in this repository, pinned by digest. (spec 002 Q22, Q23)
- PostgreSQL runs as a native sidecar gated by a startup probe; the database variables are `SPRING_DATASOURCE_*`; a check runs 15 minutes at most. (spec 002; spec 004 R13; `docs/definitions/sdlc-reference.yaml`)
- Classification (spec 010 Q13): *not evaluated* for `DisruptionTarget`, image pull failure, unschedulable, clone failure, and a service that never becomes ready; *failed* for OOMKilled during the main command. The runner reads pod and container state, not only the Job condition.
- Job names come from the job ID, so a repeated dispatch should get AlreadyExists; still an inference to confirm. (spec 010 Q16)
- Jobs use `activeDeadlineSeconds`; `ttlSecondsAfterFinished` must wait until results are collected. (spec 010 Researched)
- Open: the clone's network path, where the Secrets live and how they reach pods, quotas and limits, the Maven proxy product, and whether the Maven wrapper's own download goes through the proxy (an assumption in spec 002).

### G4 · Networking

- The laptop reaches three endpoints, each with the driver token: the MCP gateway, the engine's HTTP driver API (hooks), and the collector on OTLP/HTTP JSON port 4318. The browser reaches the UI, the engine UI API, and the analysis API. (`docs/architecture/README.md` "Components")
- Collector config (spec 006; spec 010 Q9): `attributes` deletes `tool_input`, `tool_parameters`, `error`, `full_command`; secret patterns are redacted in prompt and response text; Claude Code names map to neutral names; a `file` exporter; the engine checks the token on each batch.
- Telemetry `env` goes in `~/.claude/settings.json`, written by a kit setup step, with `OTEL_EXPORTER_OTLP_HEADERS` carrying the token. (spec 006 Q1, Q10)
- The engine owns human login with a session cookie (`human_session.token_hash`). (spec 010 Q4; `docs/engine/schema-draft.sql`)
- The engine needs egress to `api.typesafe.ai` for Jev. (BACKLOG spike 10 input from spec 007)
- Open: ingress or port forwarding, and whether the UI and the APIs share one origin (no CORS, no cross-site cookie).

### G5 · Kit install and token

- One plugin with a sensitive `userConfig` token: `${user_config.KEY}` is substituted in the MCP `headers`, and hooks read `CLAUDE_PLUGIN_OPTION_<KEY>`. (spec 005)
- In spec 005's trial, installed from a local marketplace with `--scope local`: setting the token after install reconnected MCP live, but a session started before the token was set stays unregistered, so lazy registration is needed. (spec 005)
- Hooks are POSIX `sh` with `curl` and `jq`. (spec 012 Q11)
- When the engine is unreachable, every tool call is blocked. (spec 005 Q6)
- Open: install scope, the URL form (localhost or a hostname), and where the token is generated.

### G6 · Bootstrapping and reset

- Two human users, an owner and an approver, for separation of duties. (spec 003)
- One static driver token per user, stored hashed; at most one live token per user. (`docs/engine/schema-draft.sql`, `one_live_driver_token`)
- Roles: `owner`, `approver`, `operator`, `admin`. (`docs/engine/schema-draft.sql`)
- The initial definition version is loaded from a file. (BACKLOG Definition format)
- Build the `sample-crm` check image and write its digest and the PostgreSQL digest into `sdlc-reference.yaml`. (BACKLOG spike 10)
- The Pub/Sub emulator loses all state on restart (spec 012 G6); analysis rebuilds from the engine's paged event read (AD-24).
- `sample-crm` must allow merge commits; still to confirm. (spec 010 Q26)
- Open: the bootstrap mechanism, how credentials are delivered, whether tokens survive a reset, and what a reset wipes.

### G7 · Development loop

- Spring Boot 4.1 starts in about 1 s; the build is Maven multi-module. (spec 012 hello-world)
- The UI is a static React 19.2 / Vite 8 single-page app. (spec 012 Q9)
- Open: IDE-native, redeploy into the cluster, or a traffic-mirroring tool, and which IDE.

### Researched

All checked 2026-10-27.

- **Docker Desktop versions:** the app is at 4.97.0 (2026-10-26); 4.96.1 bundles Engine 29.9.2. The "29.5.2" in spec 012 is probably the Engine version, not the app. ([release notes](https://docs.docker.com/desktop/release-notes/))
- **Docker Desktop Kubernetes:** kubeadm mode is single node with a version set by Docker Desktop; kind mode is multi-node with a selectable version, needs 4.38 or later and the containerd image store. The docs say nothing about the CNI, NetworkPolicy, or local images. ([docs](https://docs.docker.com/desktop/use-desktop/kubernetes/)) Kind-mode LoadBalancers go through `desktop-cloud-provider-kind`, an alpha fork. ([docker/desktop-cloud-provider](https://github.com/docker/desktop-cloud-provider))
- **kind:** v0.33.0, default node image v1.37.0; NetworkPolicy works out of the box since v0.24.0 (kube-network-policies in kindnet). ([releases](https://github.com/kubernetes-sigs/kind/releases); [kindnet](https://kindnet.sigs.k8s.io/docs/user/network-policies/))
- **k3s / k3d:** k3s has an embedded NetworkPolicy controller on by default, and bundles Traefik and ServiceLB. ([k3s networking](https://docs.k3s.io/networking/networking-services)) k3d v5.9.0 defaults to k3s 1.32, so `rancher/k3s` must be pinned to 1.35 or later; it can create a registry. ([releases](https://github.com/k3d-io/k3d/releases))
- **minikube:** its default kindnet CNI does not enforce NetworkPolicy; it needs `--cni=calico`. ([docs](https://minikube.sigs.k8s.io/docs/handbook/network_policy/))
- **OrbStack:** single node on Flannel, CNI replacement unsupported, NetworkPolicy undocumented, no ingress controller, Testcontainers best effort. ([docs](https://docs.orbstack.dev/kubernetes/))
- **ingress-nginx:** retired in March 2026; Gateway API is the recommended path and the Ingress API is feature-frozen. ([k8s blog](https://kubernetes.io/blog/2025/11/11/ingress-nginx-retirement/))
- **Helm:** Helm 4 since November 2025; Helm 3 security fixes until 2027-02-10. ([Helm blog](https://blog.helm.sh/blog/helm-v3-end-of-life)) Kustomize ships inside kubectl.
- **Claude Code local marketplace:** `claude plugin marketplace add ./dir`, then `claude plugin install <plugin>@<marketplace>`; relative-path sources are read in place, so edits apply at the next session or `/reload-plugins`. ([docs](https://code.claude.com/docs/en/plugin-marketplaces))
- **Plugin `userConfig`:** `sensitive` values go to the OS credential store; `${user_config.KEY}` works in an http MCP server's `url` and `headers`; `headersHelper` and shell-form hooks cannot read `user_config`; `claude plugin validate` warns on `http://` URLs to non-loopback hosts. ([docs](https://code.claude.com/docs/en/plugins-reference))
- **Claude Code network:** `api.anthropic.com` for API calls and `platform.claude.com` for OAuth exchange and refresh; `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC` turns off the optional hosts; `HTTPS_PROXY` is honoured, SOCKS is not. ([docs](https://code.claude.com/docs/en/network-config))
- **Anthropic API addresses:** IPv4 `160.79.104.0/23`, IPv6 `2607:6bc0::/48`, "will not change without notice", so evaluator egress can be a NetworkPolicy `ipBlock`. ([docs](https://platform.claude.com/docs/en/api/ip-addresses))
- **Spring Boot devtools (4.1.2):** restarts on classpath changes; off under `java -jar`; remote devtools is opt-in; LiveReload is deprecated as of 4.1.0. ([docs](https://docs.spring.io/spring-boot/reference/using/devtools.html))
- **Vite 8.3.4 `server.proxy`:** `target`, `changeOrigin`, `rewrite`, `ws`. ([docs](https://vite.dev/config/server-options))
- **Dev-loop tools:** Tilt v0.37.8 and Skaffold v2.25.0 (2026-09-16), both active; mirrord and Telepresence not checked.
- **Maven proxy:** Reposilite 3.5.x (`dzikoysk/reposilite`, about 20 MB of memory, official Helm chart); Nexus Community Edition is capped at 40,000 components or 100,000 requests a day since 3.87.0, per a forum post (Sonatype's page still shows older limits). ([Reposilite](https://reposilite.com/); [Nexus 3.87.0](https://community.sonatype.com/t/sonatype-nexus-repository-3-87-0-released/15852))
- **Kubernetes, recalled and not re-fetched (to confirm in Follow-ups):** a pod can reference Secrets only in its own namespace; `secretKeyRef` env is per container; NetworkPolicy selects pods, not containers, so a clone init container that can reach GitHub means the subject's code can too; image pulls are made by the kubelet and are not subject to NetworkPolicy.
- **Image pinning:** `sdlc-reference.yaml` pins images by full reference and digest, and a published version cannot change, so a local-registry reference would tie a version to the local cluster (Q3).

## Questions for you

<!-- Prepared by the agent, highest impact first; each says which goal questions it serves, why it matters, the options, and a suggested answer. Ticked when answered or skipped. -->

- [x] **Q1** (G1, G3, G4) Which local cluster hosts the MVP? Sandbox egress depends on NetworkPolicy being enforced, and the cluster decides how images load and ports are exposed. Options: Docker Desktop kind mode (no extra tool; CNI and NetworkPolicy undocumented), Docker Desktop kubeadm mode (same gaps, single node), k3d 5.9 with k3s pinned to 1.35 or later, upstream kind 0.33, OrbStack. *Suggested:* k3d, the only one whose docs state NetworkPolicy enforcement, a registry, an ingress controller, and localhost LoadBalancers out of the box; run the enforcement test first.
- [x] **Q2** (G2, G3, G7) How much memory and CPU can Docker Desktop's VM have? Four JVMs, PostgreSQL, the emulator, the collector, and the Maven proxy run next to a council of 3 evaluators and a check with a PostgreSQL sidecar. *Suggested:* about 12 GB and 6 CPUs.
- [x] **Q3** (G1, G3, G6) Where are check images published, given that the definition pins them by reference and digest and a published version cannot change? Options: GHCR (one reference everywhere), a local registry now and Artifact Registry later (a new definition version on GKE), or both with a mirror. *Suggested:* GHCR under your account.
- [x] **Q4** (G4) How does the laptop reach the cluster? A dropped `kubectl port-forward` blocks every Claude Code tool call (spec 005 Q6), and the UI's session cookie is simplest on one origin. Options: one localhost origin through an ingress or Gateway with path routes, plus 4318 for the collector; a LoadBalancer Service per component on its own port; port forwarding. *Suggested:* one origin.
- [x] **Q5** (G7) What is the inner dev loop, and which IDE do you use? Options: engine and UI from the IDE against the cluster (devtools restart, Vite proxy, gateway pointed at `host.docker.internal`); rebuild and redeploy with Skaffold or Tilt; mirrord or Telepresence. *Suggested:* engine and UI from the IDE, everything else redeployed by one script.
- [x] **Q6** (G3) Where do the `setup-token` and the repository token live? Options: static Secrets in `awf-sandbox`, referenced only by the evaluator container and the clone init container (the runner needs no Secret rights); or the runner keeps them in `awf-runner` and creates a per-job Secret owned by the Job (needs Secret create and delete in the sandbox namespace). *Suggested:* static Secrets in the sandbox namespace, with `automountServiceAccountToken: false`.
- [x] **Q7** (G3) How may the clone step reach GitHub, given that NetworkPolicy covers the whole pod? Options: allow GitHub for sandbox pods (accepted risk; no token after the init step); clone through an in-cluster forward proxy that allows only `github.com`; a separate clone pod writing to a volume the sandbox mounts. *Suggested:* the forward proxy, next to the Maven proxy.
- [x] **Q8** (G5, G6) Does the driver token stay the same across resets? It is stored in the plugin's keychain entry and in `~/.claude/settings.json`. Options: supplied from an untracked `.local/driver-token`, with bootstrap storing its hash; generated and printed once per bootstrap. *Suggested:* the local file, so a reset needs no kit reconfiguration.
- [x] **Q9** (G6) How does bootstrap create the users, the driver token, and the initial definition version? Options: a Kubernetes Job running the engine image in a `bootstrap` profile, idempotent, reading a Secret; a CLI module; an admin HTTP endpoint. *Suggested:* the bootstrap Job.
- [x] **Q10** (G1, G3) Which namespaces? Options: `awf`, `awf-runner`, `awf-sandbox`; one per component; two (platform and sandbox). *Suggested:* three.
- [x] **Q11** (G6) What does a reset wipe? Options: only the database PVC (keeping the Maven cache and images); the whole cluster. *Suggested:* the database only, plus a separate command that deletes the cluster.
- [x] **Q12** (G3) How many sandbox jobs may run at once? This sets the namespace quota and the limits GKE Sandbox requires anyway. *Suggested:* 4 (a council of 3 plus 1 check); 2 CPUs and 3 GiB for a check's main container, 512 MiB for its PostgreSQL sidecar, 1 CPU and 1 GiB for an evaluator.
- [x] **Q13** (G1) Kustomize or Helm? *Suggested:* a Kustomize base with local and GKE overlays: built into kubectl, no templating, and the overlays differ only in images, ingress, and the broker.
- [x] **Q14** (G3) Reposilite or Nexus Community Edition for the Maven proxy? *Suggested:* Reposilite: light and uncapped.
- [x] **Q15** (G1, G4) Which ingress or Gateway controller serves the one localhost origin on Docker Desktop's kind mode, which bundles none? ingress-nginx is retired (March 2026). Options: Traefik (Ingress and Gateway API), Envoy Gateway (Gateway API only; also a fit for GKE Gateway), or Contour. *Suggested:* Envoy Gateway with Gateway API resources, since the GKE overlay would also use Gateway API.
- [x] **Q16** (G1, G7) How are the service images (engine, gateway, analysis, runner, UI) built and loaded into the cluster? Options: Jib to the local Docker daemon, assuming kind mode sees images in Docker's containerd store (to confirm); Jib pushed to GHCR, like the check images; Dockerfiles with `docker build`. *Suggested:* Jib to the local daemon for development, GHCR for anything a definition pins.
- [x] **Q17** (G1, G3) Run the prototypes now, inside this spike, or leave them to the Phase 0 scaffolding? G3 includes the Job classification prototype and the AlreadyExists check, and Q1's choice depends on the NetworkPolicy enforcement test. *Suggested:* run the enforcement test and the AlreadyExists check now (short, and they can overturn decisions); leave classification, token isolation, and evaluator egress to scaffolding tasks.
- [x] **Q18** (G5) Where is the driver kit installed? The driver works in the `sample-crm` checkout, not this repository. Options: user scope (every project on the laptop); local scope in the `sample-crm` checkout (spec 005's trial); project scope committed to `sample-crm`. *Suggested:* local scope in the `sample-crm` checkout, as in spec 005.

## Log

<!-- Append-only, oldest first: "- YYYY-MM-DD · <source> · <what was answered, added, or found>". -->

- 2026-10-27 · research · Briefing and Q1–Q14 prepared by the spike-researcher. G3 reworded: spec 010 Q10 already accepts the `setup-token` in evaluator sandboxes, so the open point is keeping both tokens from check sandboxes and the repository token from subject code. k3d defaults to k3s 1.32 and must be pinned to 1.35 or later. Spec 012's "Docker Desktop 29.5.2" is probably the Engine version, not the app (now 4.97.0).
- 2026-10-27 · answer to Q1 · Docker Desktop kind mode (not the suggested k3d).
- 2026-10-27 · answer to Q3 · GHCR under the owner's account.
- 2026-10-27 · answer to Q4 · One localhost origin with path routes, plus 4318 for the collector.
- 2026-10-27 · answer to Q5 (part) · Engine and UI run from the IDE against the cluster; everything else redeployed by one script. Which IDE is still open.
- 2026-10-27 · answer to Q2 · Docker Desktop's VM gets 16 GB and 8 CPUs or more.
- 2026-10-27 · answer to Q6 · Static Secrets in `awf-sandbox`, referenced only by the evaluator container and the clone init container; the runner gets no Secret rights; sandbox pods get no service account token.
- 2026-10-27 · answer to Q7 · The clone reaches GitHub through an in-cluster forward proxy that allows only `github.com`, next to the Maven proxy.
- 2026-10-27 · answer to Q15 · Envoy Gateway, with Gateway API resources.
- 2026-10-27 · answer to Q8 · The driver token is fixed across resets, read from an untracked `.local/driver-token`; bootstrap stores its hash.
- 2026-10-27 · answer to Q9 · Bootstrap is an idempotent Kubernetes Job running the engine image in a `bootstrap` profile, reading a Secret; no admin API.
- 2026-10-27 · answer to Q10 · Three namespaces: `awf`, `awf-runner`, `awf-sandbox`.
- 2026-10-27 · answer to Q13 · Kustomize: a base plus local and GKE overlays.
- 2026-10-27 · answer to Q5 (rest) · No IDE: the engine and UI run from the terminal (`mvn spring-boot:run`, `npm run dev`).
- 2026-10-27 · answer to Q11 · A reset deletes only the PostgreSQL PVC and reruns bootstrap, keeping the Maven cache and images; a separate command deletes the whole cluster.
- 2026-10-27 · answer to Q12 · 4 sandbox jobs at once (a council of 3 plus 1 check); check main container 2 CPUs and 3 GiB, PostgreSQL sidecar 512 MiB, evaluator 1 CPU and 1 GiB.
- 2026-10-27 · answer to Q14 · Reposilite as the in-cluster Maven proxy.
- 2026-10-27 · answer to Q16 · Service images are built from plain Dockerfiles with `docker build` (not Jib).
- 2026-10-27 · answer to Q17 · Run the short prototypes now (NetworkPolicy enforcement on kind mode, AlreadyExists); Job classification, token isolation, and evaluator egress go to scaffolding tasks.
- 2026-10-27 · answer to Q18 · The driver kit is installed at local scope in the `sample-crm` checkout, as in spec 005.
- 2026-10-27 · research (machine check) · Docker Engine client and server 29.5.2 (so spec 012's "29.5.2" is the Engine version); containerd image store on (`io.containerd.snapshotter.v1`); the VM has about 7.7 GiB and 18 CPUs, below Q2's 16 GB; no Kubernetes context, so Kubernetes is not enabled yet; `kubectl` client v1.34.1 with Kustomize v5.7.1, which is outside the one-minor skew for a 1.36 or 1.37 cluster, so a newer kubectl is needed.
- 2026-10-27 · research (first cluster start) · Docker Desktop app 4.75.0, VM now 16 GiB and 18 CPUs, Kubernetes enabled in kind mode with 1.37.0, one node. The cluster fails to start: Docker Desktop's kubeadm config uses `kubeadm.k8s.io/v1beta3`, which kubeadm 1.37 rejects as an old API spec ("Could not initialize cluster", `com.docker.backend.log`). So Docker Desktop 4.75.0's kind mode cannot run 1.37; the app needs an update (latest 4.97.0) or an older Kubernetes version.
- 2026-10-27 · research (second cluster start) · After updating to Docker Desktop 4.97.0 (Engine 29.9.2), kind mode with `kindest/node:v1.37.0` fails the same way: the kubeadm config it writes is `v1beta3`, and kubeadm 1.37 says to use kubeadm 1.36 (`kind.log`). So Docker Desktop's kind mode cannot run 1.37 yet, even at the latest app version; 1.36 is the newest version it can start.
- 2026-10-27 · research (third cluster start) · With Kubernetes 1.36.1 the cluster starts (kindnet `v20260528-9350166c` with `kube-network-policies`). The first `kindest/node:v1.36.1` pull timed out on a TLS handshake to `auth.docker.io` and then took over an hour through Docker Desktop's VM network, while the host downloads at about 11 MB/s.
- 2026-10-27 · prototype · NetworkPolicy enforcement: with default-deny egress plus DNS and proxy allows, a check pod reaches the proxy and is blocked from `example.com` and `api.anthropic.com`; an evaluator pod reaches `api.anthropic.com` (`160.79.104.10`) through the `ipBlock` rule and is blocked from `example.com`. Repeated Job name: the second create fails with `AlreadyExists`. Details in `prototype/RESULTS.md`.
- 2026-10-27 · user · Agreed the recommendation, including the collector on a 4318 listener of the same Envoy Gateway and no egress restriction on `awf` in the MVP.

## Findings

<!-- Per goal question. Label each point as a decision (by the user), a fact (with source), or an assumption (to confirm). -->

### G1

- **Decision:** the local cluster is Docker Desktop's built-in Kubernetes in kind mode (Q1).
- **Fact:** kind mode needs Docker Desktop 4.38 or later and the containerd image store; version and node count are selectable. ([docs](https://docs.docker.com/desktop/use-desktop/kubernetes/))
- **Fact (prototype, `prototype/RESULTS.md`):** kind mode enforces NetworkPolicy. Its CNI is kindnet with the `kube-network-policies` controller; default-deny egress, namespace selectors, and `ipBlock` rules all held. Q1 stands.
- **Fact:** kind mode bundles no ingress controller, and its LoadBalancers go through `desktop-cloud-provider-kind`, an alpha fork. So an ingress or Gateway controller must be installed (Q15).
- **Decision:** check images are published on GHCR under the owner's account, so the reference and digest in a definition version are the same locally and on GKE (Q3).
- **Decision:** the one localhost origin is served by Envoy Gateway with Gateway API resources, the same resource kinds the GKE overlay uses with GKE Gateway (Q15).
- **Decision:** manifests are a Kustomize base (`deploy/base`) with overlays `deploy/overlays/local` and `deploy/overlays/gke`; the overlays differ in images, the gateway, and the broker (Q13).
- **Assumption, to confirm:** Envoy Gateway can be installed from its release `install.yaml` and referenced from the local overlay, without Helm.
- **Decision:** three namespaces (Q10): `awf` (engine, MCP gateway, analysis, UI, collector, PostgreSQL, Pub/Sub emulator), `awf-runner` (runner, Maven proxy, clone proxy), and `awf-sandbox` (Jobs only). Namespace and Service names are the same in both overlays.
- **Decision:** service images (engine, MCP gateway, analysis, runner, UI) are built from plain Dockerfiles with `docker build` (Q16). Images a definition pins by digest (check images) are pushed to GHCR (Q3).
- **Assumption, to confirm:** kind mode can run images built into Docker Desktop's containerd store without a push; otherwise they go through a local registry.
- **Decision:** the NetworkPolicy enforcement test and the AlreadyExists check run inside this spike; the Job classification prototype, token isolation, and evaluator egress tests become scaffolding tasks (Q17).
- **Fact (machine check, 2026-10-27):** Docker Engine 29.5.2 with the containerd image store on, so kind mode's prerequisite is met if the app is 4.38 or later. Kubernetes is not enabled yet. The `kubectl` client is v1.34.1 (Kustomize v5.7.1); kubectl supports one minor version of skew, so the setup must install a kubectl that matches the cluster.
- **Fact (first start, 2026-10-27):** Docker Desktop 4.75.0's kind mode fails on Kubernetes 1.37.0, because its generated kubeadm config is `v1beta3`, which kubeadm 1.37 rejects. Docker Desktop 4.97.0 fails the same way on 1.37.0, so kind mode cannot run 1.37 yet; the local cluster pins Kubernetes 1.36 (1.36.1 tested, inside spec 012's 1.35–1.37 range) on Docker Desktop 4.97.0 or later.

### G2

- **Decision:** Docker Desktop's VM gets 16 GB of memory and 8 CPUs or more (Q2). That leaves room for the full workload set plus a council of 3 evaluators and a check with a PostgreSQL sidecar at once.

### G3
- **Decision:** the `setup-token` and the repository read token are static Secrets in `awf-sandbox`, referenced by `secretKeyRef` only from the evaluator's main container and the clone init container (Q6). The runner's Role needs no rights on Secrets; sandbox pods set `automountServiceAccountToken: false`.
- **Assumption, to confirm (token isolation test):** an init container's `secretKeyRef` env is not visible to the main container, and the clone leaves no token in `.git/config` on the shared volume.
- **Decision:** the clone reaches GitHub through an in-cluster forward proxy that allows only `github.com`, next to the Maven proxy in the runner namespace (Q7). Sandbox egress stays limited to DNS and the proxies (plus Anthropic for evaluators), and the same holds on GKE.
- **Decision:** at most 4 sandbox jobs run at once, a council of 3 plus 1 check (Q12). Defaults, set as a LimitRange and a ResourceQuota in `awf-sandbox`: a check's main container 2 CPUs and 3 GiB, its PostgreSQL sidecar 512 MiB, an evaluator 1 CPU and 1 GiB. Every container has limits, as GKE Sandbox requires.
- **Decision:** the Maven proxy is Reposilite (Q14).
- **Fact (prototype):** the sandbox egress rules work as designed on kind mode: check pods reach only DNS and the proxy, evaluator pods also reach Anthropic's `160.79.104.0/23` on 443, and everything else is blocked.
- **Fact (prototype):** creating a Job whose name already exists fails with `AlreadyExists`, so a Job name derived from the runner job ID detects a repeated dispatch (spec 010 Q16). The fabric8 409 mapping is checked in the runner's scaffolding tasks.
- **Deferred (Q17):** the Job classification prototype (spec 010 Q13), token isolation, and evaluator egress without `platform.claude.com` become scaffolding tasks.
- **Fact:** first image pulls through Docker Desktop's VM network can be very slow (over an hour for the node image) even when the host is fast; pod images go through Docker Desktop's registry mirror. The setup README should pre-pull images and warn about it.

### G4

- **Decision:** the laptop reaches everything through one localhost origin with path routes (`/` UI, `/mcp` gateway, `/v1` engine, `/analysis` analysis), plus port 4318 for the collector (Q4). No port forwarding, no CORS, one cookie origin.
- **Decision (agreed with the recommendation):** the collector is a second listener on the same Envoy Gateway, on port 4318, so the telemetry exporter uses `http://localhost:4318`; the engine needs egress to `api.typesafe.ai`, and the `awf` namespace gets no egress restriction in the MVP.
- **Assumption, to confirm in scaffolding:** Envoy Gateway's LoadBalancer Service is published on localhost through Docker Desktop's `desktop-cloud-provider-kind`.

### G5
- **Decision:** the kit is installed from the local marketplace in this repository (`driver-kits/`) at local scope in the `sample-crm` checkout, as in spec 005 (Q18). Its MCP URL is `http://localhost/mcp` (one origin, G4), and the token in its sensitive `userConfig` is the one in `.local/driver-token` (G6).

### G6
- **Decision:** bootstrap is an idempotent Kubernetes Job that runs the engine image in a `bootstrap` profile and reads a Secret (Q9). It creates the owner and the approver, the owner's driver token, and the initial definition version from `definitions/sdlc-reference.yaml`. No admin API is exposed.
- **Decision:** the driver token survives resets: it is read from an untracked `.local/driver-token`, and bootstrap stores only its hash (Q8). A reset then needs no change to the kit's keychain entry or `~/.claude/settings.json`.
- **Decision:** a reset deletes only the PostgreSQL PVC and reruns bootstrap; the Maven cache and images stay. A separate command deletes the whole cluster (Q11). The Pub/Sub emulator restarts empty anyway, and analysis rebuilds from the engine's event read (AD-24).

### G7

- **Decision:** the engine and UI run from the IDE against the cluster (Spring devtools restart, Vite dev server proxy); everything else is redeployed into the cluster by one script (Q5). No IDE: both run from the terminal, `mvn spring-boot:run` with devtools for the engine and `npm run dev` for the UI (Q5).

## Recommendation and decisions

<!-- Drafted when every goal question is answered, and agreed with the user. -->

| Decision | Choice | Alternatives considered | Why |
| --- | --- | --- | --- |
| Local cluster | Docker Desktop's Kubernetes in kind mode, one node, Kubernetes 1.36 (1.36.1 tested) on Docker Desktop 4.97.0 or later | k3d with pinned k3s, upstream kind 0.33, OrbStack, Docker Desktop kubeadm mode | No extra tool; enforces NetworkPolicy (prototype); 1.37 does not start in kind mode yet |
| Packaging | Kustomize: `deploy/base`, overlays `deploy/overlays/local` and `deploy/overlays/gke` | Helm 4 | Built into kubectl, no templating; overlays differ only in images, gateway, and broker |
| Namespaces | `awf`, `awf-runner`, `awf-sandbox` | Two (platform and sandbox), one per component | Runner and proxies sit apart from the engine's credentials; small |
| In the cluster or native | Every workload in the cluster; engine and UI also run natively from the terminal while developing (`mvn spring-boot:run` with devtools, `npm run dev`) | Skaffold or Tilt, mirrord or Telepresence | Fastest loop, no extra tool |
| Exposure | One localhost origin through Envoy Gateway (Gateway API): `/` UI, `/mcp` gateway, `/v1` engine, `/analysis`; the collector on a second listener at 4318 | LoadBalancer per component, port forwarding, Traefik, Contour | No CORS, one cookie origin, nothing to drop; maps to GKE Gateway; ingress-nginx is retired |
| Egress | Sandboxes default-deny: check pods reach DNS and the proxies, evaluator pods also `160.79.104.0/23:443`; `awf` has no egress restriction in the MVP (the engine reaches `api.typesafe.ai`) | FQDN policies, a restricted `awf` | Spec 010 Q10 rules, shown to hold on kind mode |
| Sandboxes | One Job per runner job, named after the job ID; at most 4 at once; check main container 2 CPUs and 3 GiB, PostgreSQL sidecar 512 MiB, evaluator 1 CPU and 1 GiB, as a LimitRange and ResourceQuota | 2 or 6 at once | A council of 3 plus 1 check; limits on every container as GKE Sandbox needs; AlreadyExists detects a repeated dispatch (prototype) |
| Sandbox secrets | Static Secrets in `awf-sandbox`, referenced only by the clone init container (repository token) and the evaluator container (`setup-token`); `automountServiceAccountToken: false` | Per-job Secrets created by the runner | The runner needs no rights on Secrets |
| Clone path | An in-cluster forward proxy allowing only `github.com`, in `awf-runner` | Allow GitHub for sandbox pods, a separate clone pod | Subject code cannot reach GitHub; same on GKE |
| Maven proxy | Reposilite | Nexus Community Edition | Light, no usage caps |
| Images | Service images from Dockerfiles with `docker build`; check images on GHCR under the owner's account, pinned by digest | Jib, a local registry, both | One digest reference works locally and on GKE |
| VM size | Docker Desktop VM 16 GB and 8 CPUs or more | 12 GB / 6, 8 GB / 4 | Full workload set plus 4 sandbox jobs |
| Kit | Local marketplace `driver-kits/`, installed at local scope in the `sample-crm` checkout, MCP at `http://localhost/mcp` | User scope, project scope | As in spec 005; nothing committed to `sample-crm` |
| Bootstrap and reset | An idempotent bootstrap Job in the engine's `bootstrap` profile creates the owner, the approver, the driver token, and the initial definition version; the driver token is fixed, in an untracked `.local/` folder of the local overlay (Kustomize reads only files under its root); a reset deletes only the PostgreSQL PVC; a separate command deletes the cluster | A CLI, an admin endpoint; new token per bootstrap; wipe the whole cluster | No admin API, no kit reconfiguration after a reset, warm Maven cache |

## Deliverables

- [x] Every goal question answered, and the recommendation agreed with the user
- [x] Draft Kubernetes manifests and a README section that take a fresh clone to a stub `status` call from Claude Code (`docs/local-setup/`)
- [x] BACKLOG.md items settled by this spike updated

## Follow-ups

<!-- New specs, BACKLOG items, experiments, or open questions this spike created. -->

Scaffolding tasks created by this spike (recorded in PLAN.md under the Runner stub and Kubernetes manifests items):

- The Job classification prototype (spec 010 Q13), with fabric8 8.x; also check that fabric8 surfaces a repeated Job name as HTTP 409.
- Token isolation: an init container's `secretKeyRef` env and the clone leave nothing the main container can read.
- Evaluator authentication with `CLAUDE_CODE_OAUTH_TOKEN` behind the `ipBlock` policy, without `platform.claude.com`.
- Envoy Gateway's LoadBalancer published on localhost through `desktop-cloud-provider-kind`, and installing Envoy Gateway from its release `install.yaml` without Helm.
- Routing `/v1` and the MCP gateway to `host.docker.internal` while the engine runs natively.
- Choose the clone proxy product (a forward proxy that allows only `github.com`).
- Confirm that kind mode runs images built into Docker Desktop's containerd store without a push.
- Install a `kubectl` matching the cluster (the owner's is v1.34.1); pre-pull images, since first pulls through Docker Desktop's VM network can take over an hour.
- Confirm that `sample-crm` allows merge commits (spec 010 Q26).

BACKLOG.md updates for `/sdd-verify`: Local cluster and Sandbox technology settled; spec 012's "Docker Desktop 29.5.2" is the Docker Engine version (the app was 4.75.0, now 4.97.0); the supported Kubernetes range for kind mode is 1.36, not 1.37.

Things to try, proposed by the researcher (the NetworkPolicy and AlreadyExists items were run; see `prototype/RESULTS.md`):

- **Machine facts (5 min):** Docker Desktop app version, whether the containerd image store is on, VM memory and CPU, `kubectl version --client`, and whether `sample-crm` allows merge commits (spec 010 Q26).
- **NetworkPolicy enforcement (30 min per cluster):** default-deny egress in `awf-sandbox`, allow DNS and maven-proxy; `curl` the proxy (passes), `example.com` (fails), and `160.79.104.0/23` from an evaluator-labelled pod (passes).
- **Native sidecar Job (1 h):** `postgres:18-alpine` sidecar with a startup probe next to `eclipse-temurin:25-jdk` running `./mvnw -B verify`; cold and warm times through the Maven proxy; confirm the wrapper download goes through it.
- **Job classification prototype (spec 010 Q13; half a day):** fabric8 8.x Jobs that pull a missing image, request 64 CPUs, OOM the main container, leave a sidecar never ready, exit 1, exceed `activeDeadlineSeconds`, and get evicted; record the pod and container state the runner would read.
- **AlreadyExists (spec 010 Q16; 30 min):** create the same Job name twice with fabric8 and confirm a 409 `AlreadyExists`.
- **Token isolation (1 h):** clone in an init container with the token from `secretKeyRef` and `git -c http.extraHeader`; confirm the main container cannot find it in env, `/proc`, `.git/config`, or through the API.
- **Evaluator egress (1 h):** `claude -p` with `CLAUDE_CODE_OAUTH_TOKEN` and `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1` behind an `ipBlock` policy allowing only Anthropic; does it authenticate without `platform.claude.com`?
- **Digest pinning (30 min):** push the check image and run a Job pinned to `image@sha256:…`.
- **Emulator restart (30 min):** recreate topics and subscriptions on pod start; restart it and check that the engine and analysis recover.
- **One origin (1 h):** UI, engine, gateway, and analysis behind one localhost ingress; log in, check the cookie reaches `/v1`, and connect Claude Code to `http://localhost/mcp` without a validate warning.

## Changelog

- 2026-10-27 Created.
- 2026-10-27 Goal questions filled from BACKLOG.md; claimed; status in-progress.
- 2026-10-27 Briefing, Q1–Q14, and Things to try added from the spike-researcher.
- 2026-10-27 Q1, Q3, Q4 answered, Q5 in part; Q15 added.
- 2026-10-27 Q2, Q6, Q7, Q15 answered.
- 2026-10-27 Q8, Q9, Q10, Q13 answered.
- 2026-10-27 Q5, Q11, Q12, Q14 answered; Q16–Q18 added.
- 2026-10-27 Q16–Q18 answered.
- 2026-10-27 Prototypes run; goal questions answered; recommendation agreed with the user.
- 2026-10-27 Draft manifests and README section written in `docs/local-setup/`; both overlays build with `kubectl kustomize`.
- 2026-10-27 Verified and closed: BACKLOG.md Local cluster, Sandbox technology, and spike 10 ticked; PLAN.md spike 10 ticked and the follow-ups added to the Runner stub and Kubernetes manifests scaffolding items.
