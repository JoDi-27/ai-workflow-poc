# 018 · Plan: Architecture plant gaps

## Approach

Two new folders in `engine/architecture-plants/`, each breaking exactly one ArchUnit rule, with an `expect` pattern taken from that rule's own failure message. A GMavenPlus script in the `mutation` profile reads the JDK toolchain that `select-jdk-toolchain` stored for the project and passes its `java` to PIT's `jvm` parameter. The design and the engine README record both. All three parts were tried in a prototype before writing this plan (Design, "What the prototype showed").

## Constitution check

| Article | How the plan complies |
| --- | --- |
| 3 The engine is neutral | The member-name plant proves the check that keeps git and SDLC words out of core member names keeps working |
| 5 Fail closed | If no toolchain and no matching JDK is found, `select-jdk-toolchain` already fails the build. The script uses the selected toolchain, or Maven's own JDK only when it is version 25; otherwise (for example with `-Dtoolchain.skip=true` on another JDK) it fails the build |
| 11 Tests prove acceptance criteria | Each AC maps to the plant script, a recorded manual check, or a grep (Test strategy) |
| 12 Small, traceable increments | Four tasks, one commit each |
| 13 Specs are living | The cycle plant could not follow the brief's wording (no two named core packages may call each other); the spec changelog records why |
| 15 Verify current docs | GMavenPlus 5.1.0 (latest on Maven Central, 2026-07-02) and Groovy 5.1.3 (latest 5.x); `execute` goal and its `project`, `session`, `log` bindings from the GMavenPlus execute-goal page and wiki examples, 2026-11-01. PIT 1.30.1's `jvm` parameter from its `plugin.xml`: "Path to java executable to use when running tests. Will default to executable in JAVA_HOME"; it has no toolchain parameter |

## Design

### Member-name plant (`banned-member`)

`engine-core/.../core/usecases/Planted.java`: a package-private class `Planted` with a method `authorizeMerge()`. No class or package name holds a banned word, so only `no_banned_names_in_core_or_port_members` fails. `expect`: `authorizeMerge\(\)> has the word 'merge' in its name`, the member rule's message, which the class rule never prints.

### Cycle plant (`core-cycle`)

The allowed-calls rule lets no two of the nine named core packages call each other: every pair has at most one allowed direction. So any cycle among them also breaks `core_packages_call_only_what_they_may`. The plant uses two core packages the allowed-calls rule does not name, `core.plantedalpha` and `core.plantedbeta`, each with a class holding a field of the other's type. The layered rule considers only dependencies inside its layers, so only `core_packages_are_free_of_cycles` fails. `expect`: `Cycle detected: Slice planted(alpha|beta)`, whichever slice ArchUnit reports first.

### PIT test JVM

PIT forks its test JVMs (minions) from the JDK that runs Maven and ignores the toolchain that compiler and surefire use. In the `mutation` profile, before PIT:

- `gmavenplus-plugin` 5.1.0, goal `execute` in `initialize`, with `groovy` 5.1.3 as a plugin dependency (so `analyze-only` never sees it). The script looks up `ToolchainManager`, takes `getToolchainFromBuildContext('jdk', session)`, and sets the project property `pit.jvm` to its `findTool('java')`. When no toolchain is stored, because the JDK running Maven already matches `[25,26)`, it uses that JDK's `java`, after checking its `java.specification.version` is `25` and failing the build if not. It logs `PIT test JVM: <path>`.
- PIT's configuration sets `<jvm>${pit.jvm}</jvm>`.

### What the prototype showed

Run on a copy of JDK 25 at another path (as `JAVA_HOME`), with a toolchains file pointing at the installed JDK 25, `toolchain.jdk.mode=Never` and discovery off, so that the toolchain and Maven's JDK differ:

- Before the change, surefire used the toolchain, but PIT's minions printed `java.home` = the copy (Maven's JDK). On Maven running a JDK below 25 the minions could not load the classes; above it, the mutation score came from that newer JDK.
- After the change, the minions printed the toolchain's `java.home`.
- `run.sh banned-member core-cycle`: both caught, each failing only its own rule, and the clean build passes.

### Documents

- `docs/engine/design.md` section 1, "Build rules the trial found": a bullet saying PIT ignores toolchains, and how the build hands it the toolchain's `java`.
- `engine/README.md`, Gates: a table of every plant and the rule it proves, including the two new ones.
- `BACKLOG.md` "Next": unnamed core packages escape the allowed-calls rule (spec Out of scope).

## Requirement coverage

| Requirement | Covered by |
| --- | --- |
| R1 | `banned-member` plant |
| R2 | `core-cycle` plant |
| R3 | both plants' `expect` files, run by `run.sh` |
| R4 | GMavenPlus script and PIT `jvm` |
| R5 | design section 1 bullet |
| R6 | README plant table |

## Decisions

| # | Decision | Alternatives | Why |
| --- | --- | --- | --- |
| D1 | Cycle between two core packages the allowed-calls rule does not name | A cycle among named packages, expecting the cycle message while the layered rule fails too | No named pair allows both directions, so that plant would fail two rules; a plant that fails only its own rule proves that rule alone (the brief's intent) |
| D2 | Pass the toolchain's `java` to PIT with a GMavenPlus script | Maven Enforcer `requireJavaVersion [25,26)` in the `mutation` profile (fails closed, but breaks "the build finds JDK 25 whatever `java` is"); only record the behaviour (Q1 says pin when possible); a custom Maven plugin (too much for one property) | Pins the minions to the same JDK as compiler and surefire, with one plugin confined to the `mutation` profile |
| D3 | Groovy 5.1.3, not 6.0.0 | Groovy 6.0.0 (released 2026-09) | GMavenPlus's examples use Groovy 5; Groovy 6 is weeks old |

## Test strategy

| Acceptance criterion | Verified by (unit, integration, e2e, manual) |
| --- | --- |
| AC-1 | `architecture-plants/run.sh banned-member` (its `expect` is unique to the member rule: the class rule's message starts `Class <`); that no other rule fails is a manual reading of its log |
| AC-2 | `architecture-plants/run.sh core-cycle`; that no other rule fails is a manual reading of its log |
| AC-3 | `architecture-plants/run.sh` (every plant, then the clean build) |
| AC-4 | Manual: the prototype's run (Maven on a copy of JDK 25, toolchain on the installed one), minion `java.home` = the toolchain's; plus the normal `-Pmutation verify` logging `PIT test JVM` |
| AC-5 | grep of design section 1 |
| AC-6 | grep of `engine/README.md` |

## Risks and unknowns

- CI runs Maven on JDK 25, so CI always takes the script's fallback branch and never the toolchain branch. AC-4 rests on the recorded manual run; Evidence gives its exact commands and the minion `java.home` values so it can be repeated.

- Groovy runs inside Maven's JVM. A much newer JDK running Maven might not be supported by Groovy 5; then the mutation build fails at the script, which fails closed and names the cause.
- `getToolchainFromBuildContext` is Maven 3 API; Maven 4 may change it. The wrapper pins Maven 3.10.0.

## Deviations

| Date | Change | Why |
| --- | --- | --- |
| 2026-11-01 | The script throws an exception instead of calling `fail()`, which GMavenPlus scripts do not have (found when testing the fail-closed branch) | `fail()` only failed the build by accident, with a confusing message |
| 2026-11-01 | T6: the script also fails when the toolchain has no executable `java` | Verify review: `pit.jvm` would be null and PIT would quietly use Maven's JDK (article 5) |
