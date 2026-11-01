# 017 · Tasks: Engine skeleton

## Tasks

- [x] **T1** Create the reactor: `engine/pom.xml` (Boot 4.1.2 parent, Java 25 release, toolchains `select-jdk-toolchain` for 25), the Maven Wrapper (`only-script`, Maven 3.10.0), `.mvn/maven.config` and `.mvn/settings.xml` (Maven Central), and the six modules, each with a `package-info.java` and no engine dependency yet · `engine/` · check: `cd engine && ./mvnw -B verify` → AC-1, AC-3
- [x] **T2** Add the build gates to the parent: `analyze-only` with `failOnWarning` in every module; JaCoCo `prepare-agent` and `check` (BUNDLE LINE 0.80) in every module; PIT 1.30.1 with the JUnit 5 plugin in a `mutation` profile, `mutationThreshold` 70, `failWhenNoMutations` false, `EngineApplication` and `*IntegrationTest` excluded, no `skip`; `scripts/every-module-tested.sh` fails a module with classes but no test results (plan Deviations) · `engine/pom.xml`, `engine/scripts/` · check: `cd engine && ./mvnw -B -Pmutation verify && scripts/every-module-tested.sh` → AC-9, AC-10
- [x] **T3** Test first, then add the inbound port: `DriverOperations` with `status`, `StatusQuery`, `StatusView` and its parts, `Outcome`, `Denial`, `DenialCode`, `Caller`, `CredentialKind` (design section 2), with tests that build and read each record. First PIT run with JUnit 6 (plan, Risks) · `engine/engine-ports-in` · check: `cd engine && ./mvnw -B -Pmutation -pl engine-ports-in verify` → AC-7, AC-9, AC-10
- [x] **T4** [P] Test first, then add `usecases.DriverOperationsService` returning the fixed unbound Status (contract version `v1`, the session with no bound run, no startable workflows, empty pending and recommendations, `next.action` none) · `engine/engine-core` · check: `cd engine && ./mvnw -B -Pmutation -pl engine-ports-in,engine-core verify` → AC-7
- [x] **T5** [P] Test first (standalone MockMvc with a fake port), then add `driverapi`: `GET /v1/status?session_id=…`, the snake_case contract records with every top-level Status field, `invalid_inputs` (422) for a missing `session_id`, and all 15 `DenialCode` values mapped to the contract's HTTP status by an exhaustive `switch` with no `default`, one test per code · `engine/adapters-in` · check: `cd engine && ./mvnw -B -Pmutation -pl engine-ports-in,adapters-in verify` → AC-7
- [x] **T6** Add `engine-app`: Boot main, `application.yaml` (datasource from env with local defaults, Flyway schema `engine` and location `db/migration/engine`, Actuator exposing only `health` with details), `V1__engine_schema.sql`, `engine/compose.yaml` with PostgreSQL 18, and Testcontainers integration tests on `postgres:18` (`*IntegrationTest`): V1 applied and recorded, not reapplied on restart; health 200 `UP` with `db` `UP`; health 503 `DOWN` after its container stops; `status` over real HTTP · `engine/engine-app`, `engine/compose.yaml` · check: `cd engine && ./mvnw -B -Pmutation verify` → AC-4, AC-5, AC-6, AC-7, AC-8
- [x] **T7** Add `ArchitectureTest` in `engine-app`: one rule per row of design section 1, "How the rules are checked", plus the module-edge rules (ports depend on no engine package; core on no `adapter..` or `app..`; `adapter.in..` only on `port.in..`; `adapter.out..` only on `port.out..`), the camel-case name condition with a unit test that `digit`, `legitimate`, and `emerge` pass while `GitRevision`, `authorizeMerge`, and `pullRequestUrl` fail; `allowEmptyShould` only on rules whose packages do not exist yet · `engine/engine-app/src/test` · check: `cd engine && ./mvnw -B verify` → AC-1, AC-2
- [x] **T8** Add `engine/architecture-plants/`: one folder per plan row (including `out-uses-core`) with the planted files and an `expect` pattern, pom profiles for the plants that need a dependency, and `run.sh` (online, slow gates off, trap cleanup, fails if a plant builds green or fails at another tool, ends with a clean build) · `engine/architecture-plants/`, `engine/*/pom.xml` · check: `engine/architecture-plants/run.sh` → AC-2
- [x] **T9** Add the `engine` CI job, following `ci.yml`'s conventions (checkout with `persist-credentials: false`, actions by SHA, no path filters): `actions/setup-java` v6.1.0 with Temurin 25 and the Maven cache, `./mvnw -B -Pmutation verify`, `scripts/every-module-tested.sh`, and `architecture-plants/run.sh` with `if: ${{ !cancelled() }}`, all with `working-directory: engine`; add `engine` to `.github/rulesets/main.json` · `.github/workflows/ci.yml`, `.github/rulesets/main.json` · check: `grep -q '^  engine:' .github/workflows/ci.yml && jq -e '.rules[] | select(.type=="required_status_checks") | .parameters.required_status_checks[] | select(.context=="engine")' .github/rulesets/main.json` → AC-9, AC-10, AC-11
- [x] **T10** Update the documents: `CLAUDE.md` (current state, a Commands section), `engine/README.md` (commands run from `engine/`, modules, plants), `PLAN.md` (Engine skeleton item wording, new "MCP gateway skeleton" item), `docs/engine/design.md` section 1 (base package settled), and `.claude/skills/engine-change/SKILL.md` (no longer a draft; `status` as the worked example; steps the code proved wrong) · check: `grep -q 'MCP gateway skeleton' PLAN.md && grep -q 'stub .status. on the HTTP driver API' PLAN.md && grep -q 'mvnw' CLAUDE.md && grep -q 'mvnw' engine/README.md` → AC-12, AC-13
- [x] **T13** Add a step to the `engine` CI job that starts the packaged jar against a PostgreSQL 18 service and requires health `UP` and `status` 200, through `engine/scripts/smoke-jar.sh` (plan Deviations: a test-scope declaration dropped a library from the jar while every test passed) · `engine/scripts/smoke-jar.sh`, `.github/workflows/ci.yml` · check: `cd engine && docker compose up -d --wait && ./mvnw -B -q -ntp -DskipTests package && scripts/smoke-jar.sh` → AC-5, AC-7, AC-12
- [x] **T11** Run the manual checks: on a fresh clone, follow `engine/README.md`, then `curl` health and `status` (AC-12); with `JAVA_HOME` on another JDK and a `~/.m2/settings.xml` mirror to an unreachable host, run `./mvnw -B -Pmutation verify` and the run command, and confirm the JDK 25 toolchain in the log (AC-3) · check: manual: both checks pass, with the commands and output recorded in Evidence → AC-3, AC-12 · checked: Claude, 2026-11-01, on a copy of engine/ without target/: (AC-3) with Maven on JDK 26.0.1 (JAVA_HOME) and a throwaway ~/.m2 whose settings mirror everything to https://nexus.invalid with an empty local repository, ./mvnw -B -Pmutation verify passed (44 tests), every download came from central-direct and none from the mirror, compiler and surefire used the JDK 25.0.3 toolchain, and ./mvnw -pl engine-app -am spring-boot:run started the engine with .../openjdk@25/25.0.3/.../bin/java. (AC-12) Following engine/README.md: docker compose up -d, spring-boot:run, curl health gave UP with HTTP 200 and status?session_id=s1 gave 200 with the unbound Status. Not checked: which JVM PIT's forked minions use when Maven runs on another JDK (the tests pass either way). (2026-11-01)
- [ ] **T12** Open the pull request (saying CI gets stricter) and record its CI run: every required check passes, including `engine` · check: manual: the pull request's checks page shows `repo-checks` and `engine` green → AC-11

## Verification

- [ ] **V1** Every acceptance criterion verified, with evidence below
- [ ] **V2** PLAN.md and BACKLOG.md updated for this spec

## Evidence

| Acceptance criterion | Result | Evidence (test, command output, manual check) |
| --- | --- | --- |
| AC-1 | | |
| AC-2 | | |
| AC-3 | | |
| AC-4 | | |
| AC-5 | | |
| AC-6 | | |
| AC-7 | | |
| AC-8 | | |
| AC-9 | | |
| AC-10 | | |
| AC-11 | | |
| AC-12 | | |
| AC-13 | | |
