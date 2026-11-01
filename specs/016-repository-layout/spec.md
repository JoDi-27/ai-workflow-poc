---
id: 016
title: "Repository layout"
type: feature
status: done
phase: 0
depends_on: [015]
refs: ["PLAN Phase 0 / Scaffolding / Repository layout"]
updated: 2026-11-01
---

# 016 · Repository layout

## Problem

Spike 010 settled the system's components and a planned repository layout: one top-level folder per component. None of these folders exists yet. The layout was once listed in `CLAUDE.md`, but that line was removed in a doc refresh, so today it lives only in `PLAN.md`, and only as an example.

The next scaffolding specs (engine, UI, driver kit, runner, and the rest) each put code in one of these folders. If each spec picks its own folder, the names drift away from the design docs, the deploy manifests, and each other. Settling the layout first, in one small reviewed change, gives every later spec a fixed place to build in and a short note saying what belongs there.

It moves PLAN Phase 0 scaffolding forward and lands through the CI pipeline from spec 015.

## Scope

**In:**

- One top-level folder for each component that will have code or configuration in this repository: engine, MCP gateway, analysis service, UI, runner, the Claude Code driver kit, and workflow definitions. `deploy/` and `docs/` already exist and stay where they are.
- A short README in each new folder: what it holds, which design document describes it, and which PLAN item or spec fills it.
- A subfolder of the Claude Code kit for the OTel Collector's pipeline configuration. The architecture calls this configuration kit-owned, because it maps Claude Code names to neutral ones, so it lives with the kit (Q1).
- A repository check in the CI pipeline that keeps the root `README.md` and the top-level folders in step (Q2).
- Updating the documents that describe the repository: the root `README.md` "Read first" table, `CLAUDE.md` "Where things live", and `PLAN.md`, so they name the real folders instead of an example.

**Out:**

- Any build file, source code, wrapper, or toolchain pin. The engine skeleton spec adds the Maven reactor, the project Maven settings, and the JDK pin; the other skeleton specs add their own.
- New CI checks for code. Each skeleton spec adds its build and test checks (spec 015, "how to add a check").
- Moving the spike prototypes under `specs/*/prototype/`. They stay with their spike as history.
- The JSON Schema file under the definitions folder and the reference workflow copy: PLAN Phase 1, Definition model.
- A folder for `laya-serve`: Laya is out of the MVP (spike 010, Q27).
- Moving the draft collector configuration out of `deploy/base/collector.yaml` into the kit's collector folder: the OTel Collector item, together with the Kubernetes manifests item, which wires it into the deployment.

## User scenarios

### S1 · A later spec picks where its code goes

A session planning the engine skeleton needs to know where the engine's code lives.

1. It reads the root `README.md` or `CLAUDE.md` and finds the engine folder.
2. It opens that folder's README, which points it at `docs/engine/design.md` and names the engine skeleton item as the one that fills it.
3. It plans the code in that folder, with no layout choice left to make.

### S2 · A newcomer finds their way

Someone new to the repository wants to know what each top-level folder is for.

1. They read the root `README.md` "Read first" table, which lists every top-level folder with one line each.
2. Each folder's README says whether it holds code yet, and which document to read.

## Requirements

- **R1** The repository shall have one top-level folder for each of: the engine, the MCP gateway, the analysis service, the UI, the runner, the driver kits (with the Claude Code kit in its own subfolder), and workflow definitions, named as in spike 010's planned layout.
- **R2** Each folder that R1 adds shall hold a README that states what the folder will hold, the design document to read for it, and the PLAN item that fills it.
- **R3** The Claude Code kit folder shall have a subfolder for the OTel Collector's pipeline configuration, with a README that states it is the one place for that configuration.
- **R4** The root `README.md` "Read first" table shall list every top-level folder whose name does not start with a dot, with no such folder missing and none listed that does not exist.
- **R5** `CLAUDE.md` and `PLAN.md` shall name the layout as created, not as an example.
- **R6** The folders added by R1 shall hold no build file, source code, or toolchain configuration.
- **R7** When the layout change is in a pull request, the required CI checks from spec 015 shall pass.
- **R8** If a top-level folder whose name does not start with a dot has no README, or is missing from the root `README.md` "Read first" table, or that table lists a folder that does not exist, then the CI shall fail the layout check.
- **R9** The layout check shall be part of a required check for merging into `main`.

## Acceptance criteria

- **AC-1** Given the repository root, when its folders are listed, then each folder named in R1 exists, the Claude Code kit has its own subfolder under the driver kits folder, and `deploy/` and `docs/` stay where they are. (R1)
- **AC-2** Given each folder added by R1, when its README is read, then it names what the folder will hold, a design document that exists, and a PLAN item that exists. (R2)
- **AC-3** Given the Claude Code kit folder, when its contents are listed, then it has a collector subfolder whose README names it as the one place for the collector's pipeline configuration; and `docs/architecture/README.md` and `docs/local-setup/README.md` point to that place. (R3)
- **AC-4** Given the root `README.md` "Read first" table and the list of top-level folders, when they are compared, then every folder whose name does not start with a dot appears in the table, and every path in the table exists. (R4)
- **AC-5** Given `CLAUDE.md` and `PLAN.md`, when they are read, then each names every folder R1 creates, and `PLAN.md`'s Repository layout item no longer says "for example". (R5)
- **AC-6** Given the folders added by R1, when their contents are listed, then each holds only READMEs and the subfolders R1 and R3 require. (R6)
- **AC-7** Given the pull request that lands this spec, when its checks run, then every required check passes. (R7)
- **AC-8** Given a pull request that adds a top-level folder with no README, when the checks run, then the layout check fails; after adding a README but no "Read first" row, it still fails; after adding the row, it passes. Given a "Read first" row for a folder that does not exist, the layout check fails. (R8)
- **AC-9** Given `main`'s merge rules, when the required checks are listed, then the layout check runs as part of one of them, and a pull request failing it cannot be merged. (R8, R9)

## Open questions

- Q1: Where does the OTel Collector's pipeline configuration live? A: In the Claude Code kit, in a collector subfolder, because the architecture calls it kit-owned. (owner, 2026-11-01)
- Q2: Should CI check that every top-level folder has a README and is listed in the root `README.md`? A: Yes, as a required check, so a later spec cannot add a folder without documenting it. (owner, 2026-11-01)

## Changelog

- 2026-11-01 Created.
- 2026-11-01 Q1–Q2 answered: collector configuration in the kit; a required CI layout check (R8, R9, AC-8, AC-9).
- 2026-11-01 Approved (`/sdd-plan`).
- 2026-11-01 Planning: AC-1 says `deploy/` and `docs/` stay where they are, not that they are unchanged, since the layout check needs a README in `deploy/` and `scripts/`; R9 and AC-9 allow the layout check to run inside an existing required check.
- 2026-11-01 Plan review: R4, R8, and AC-4 cover top-level folders whose name does not start with a dot; tooling folders such as `.github/` are described in `CONTRIBUTING.md`. AC-5 asks `CLAUDE.md` and `PLAN.md` to name each folder.
- 2026-11-01 Planned: `plan.md` and `tasks.md` (5 tasks).
- 2026-11-01 Implementation started (`/sdd-implement`).
- 2026-11-01 Verified and closed (`/sdd-verify`): all 9 ACs pass. T6 added from the verify review.
