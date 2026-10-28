# Local setup

Draft from spike 014 (`specs/014-local-setup/`). It describes how the whole system runs on one laptop, on a local Kubernetes cluster, with manifests that carry over to GKE. The draft manifests are in `deploy/` (`base/` and the `local` and `gke` overlays). The Phase 0 scaffolding moves this section into the repository README, then implements the scripts it names. Nothing here runs yet: the service images do not exist.

## What runs where

| Where | What |
| --- | --- |
| Namespace `awf` | engine (1 replica), MCP gateway (1 replica), analysis, UI, OTel Collector, PostgreSQL 18 (schemas `engine` and `analysis`), Pub/Sub emulator, Envoy Gateway's Gateway |
| Namespace `awf-runner` | runner, Maven proxy (Reposilite), clone proxy (`github.com` only) |
| Namespace `awf-sandbox` | runner Jobs only: default-deny egress, at most 4 at once |
| The Mac | Claude Code with the driver kit, the browser, Maven builds and Testcontainers; while developing, also the engine and the UI |

The laptop reaches everything through one origin, `http://localhost`: `/` UI, `/mcp` MCP gateway, `/v1` engine (hooks and UI API), `/analysis` analysis API. The collector listens on `http://localhost:4318`.

## Prerequisites

- Docker Desktop 4.97.0 or later, with at least 16 GB of memory and 8 CPUs (Settings → Resources) and the containerd image store on.
- Kubernetes in Docker Desktop: Settings → Kubernetes, provisioner **kind**, one node, version **1.36**. Version 1.37 does not start in kind mode yet (spike 014).
- A `kubectl` that matches the cluster's minor version (1.35–1.37). Docker Desktop's own is at `/Applications/Docker.app/Contents/Resources/bin/kubectl`.
- JDK 25 and the repository's Maven settings (spec 013 Q16); Node for the UI.
- First image pulls through Docker Desktop's VM network can be slow. `scripts/prepull` pulls every image once.

## From a fresh clone to a `status` call

1. **Secrets.** Create the untracked folder `deploy/overlays/local/.local/` with:
   - `postgres.env`, `engine.env`, `analysis.env`: database credentials, and `AWF_JEV_KEY` in `engine.env`.
   - `bootstrap.env`: the owner's and the approver's passwords, and `AWF_DRIVER_TOKEN`, the fixed driver token.
   - `repo-token`: the fine-grained read-only token for `sample-crm`.
   - `setup-token`: the Claude subscription token from `claude setup-token`, used only by evaluator sandboxes.
2. **Gateway controller.** Install Envoy Gateway from its release `install.yaml`.
3. **Images.** `docker build` each service image (`awf/engine:dev`, `awf/mcp-gateway:dev`, `awf/analysis:dev`, `awf/ui:dev`, `awf/runner:dev`). Check images are pushed to GHCR and pinned by digest in `definitions/sdlc-reference.yaml`.
4. **Deploy.** `kubectl apply -k deploy/overlays/local`. The bootstrap Job creates the owner and the approver, stores the driver token's hash, and loads the reference workflow as the initial definition version. It is idempotent.
5. **Driver kit.** In the `sample-crm` checkout:
   ```sh
   claude plugin marketplace add <this repo>/driver-kits
   claude plugin install <kit>@<marketplace> --scope local
   ```
   Enter the driver token from `bootstrap.env` when the kit asks for it. The kit's setup step writes the telemetry settings (endpoint `http://localhost:4318`, the token in `OTEL_EXPORTER_OTLP_HEADERS`).
6. **Check.** Start Claude Code in `sample-crm` and call the engine's `status` tool. In Phase 0 it returns the stub answer.

## Development loop

The cluster runs everything. To work on the engine or the UI, run it from the terminal instead:

- Engine: `mvn spring-boot:run` with devtools, which restarts on class changes. The gateway route for `/v1` and the MCP gateway then point at `host.docker.internal` (to confirm in scaffolding).
- UI: `npm run dev`; the Vite dev server proxies `/v1` and `/analysis` to `http://localhost`.

Other components are rebuilt and redeployed with one script (`scripts/redeploy <component>`).

## Reset

- `scripts/reset` deletes the PostgreSQL PVC and reruns bootstrap. The driver token stays the same, so the kit needs no change. The Maven cache and images stay. The Pub/Sub emulator starts empty anyway, and analysis rebuilds from the engine's event read.
- `scripts/nuke` deletes the whole cluster (Settings → Kubernetes → Reset Kubernetes Cluster).

## Still to confirm in scaffolding

See the spike's Follow-ups: Job classification, token isolation, evaluator authentication behind the `ipBlock` policy, Envoy Gateway on localhost, routing to `host.docker.internal`, and the clone proxy product.
