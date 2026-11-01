# 018 · Tasks: Architecture plant gaps

## Tasks

- [x] **T1** [P] Add the `banned-member` plant: `core.usecases.Planted` with `authorizeMerge()`, `expect` matching the member rule's message · `engine/architecture-plants/banned-member/` · check: `engine/architecture-plants/run.sh banned-member` → AC-1, AC-3
- [x] **T2** [P] Add the `core-cycle` plant: `core.plantedalpha` and `core.plantedbeta` referring to each other, `expect` matching the cycle rule's message · `engine/architecture-plants/core-cycle/` · check: `engine/architecture-plants/run.sh core-cycle` → AC-2, AC-3
- [x] **T3** Pass the JDK toolchain's `java` to PIT: GMavenPlus 5.1.0 with Groovy 5.1.3 in the `mutation` profile, PIT `jvm` from `pit.jvm`; record the before and after run on a copy of JDK 25 as Maven's JDK · `engine/pom.xml` · check: `cd engine && bash -o pipefail -c "./mvnw -B -ntp -Pmutation verify | grep 'PIT test JVM: .*/bin/java'" && scripts/every-module-tested.sh` → AC-4
- [x] **T4** Update the documents: design section 1 bullet on PIT and toolchains, the plant table in `engine/README.md`, and the unnamed-core-packages item in `BACKLOG.md` "Next" · `docs/engine/design.md`, `engine/README.md`, `BACKLOG.md` · check: `sed -n '/^## 1\./,/^## 2\./p' docs/engine/design.md | grep -q 'PIT ignores Maven toolchains' && grep -q 'banned-member' engine/README.md && grep -q 'core-cycle' engine/README.md` → AC-5, AC-6
- [x] **T5** Run the full CI gates and every plant, record the results in Evidence, and open the pull request · `specs/018-*/tasks.md`, `specs/STATUS.md` · check: `cd engine && ./mvnw -B -Pmutation verify && scripts/every-module-tested.sh && architecture-plants/run.sh` → AC-3
- [x] **T6** Fail the mutation build when the chosen toolchain has no executable `java` (verify review); note in the pom that PIT needs the lifecycle for its `jvm` · `engine/pom.xml` · check: `cd engine && ./mvnw -B -ntp -q -Pmutation -pl engine-ports-in initialize && grep -q 'canExecute' pom.xml` → AC-4

## Verification

- [x] **V1** Every acceptance criterion verified, with evidence below
- [x] **V2** PLAN.md and BACKLOG.md updated for this spec (BACKLOG "Next": Unnamed core packages; the PLAN Engine skeleton item stays with spec 017)

## Evidence

| Acceptance criterion | Result | Evidence (test, command output, manual check) |
| --- | --- | --- |
| AC-1 | pass | `architecture-plants/run.sh` (T5 run, 2026-11-01): `ok   banned-member: caught (authorizeMerge()> has the word 'merge' in its name)`. Its log names only `ArchitectureTest.no_banned_names_in_core_or_port_members`. The plant's class `Planted` and package `core.usecases` have no banned word |
| AC-2 | pass | Same run: `ok   core-cycle: caught (Cycle detected: Slice plantedalpha)`. Its log names only `ArchitectureTest.core_packages_are_free_of_cycles` |
| AC-3 | pass | T5 check `./mvnw -B -Pmutation verify && scripts/every-module-tested.sh && architecture-plants/run.sh`: BUILD SUCCESS (44 tests, every mutation killed), `ok every-module-tested`, all 13 plants caught, `ok   clean build passes`, `ok architecture-plants`. A first attempt failed in `EngineIntegrationTest` on a Testcontainers connection timeout (surefire, not PIT); the rerun passed unchanged |
| AC-4 | pass (manual) | Claude, 2026-11-01. Maven on a copy of JDK 25.0.3 at another path, toolchain on the installed JDK 25.0.3, so the two differ: `cp -R <jdk25 home> $S/mavenjdk`; a toolchains file `$S/toolchains.xml` with one `jdk` 25 toolchain at the installed home; then from `engine/`: `JAVA_HOME=$S/mavenjdk ./mvnw -B -ntp -t $S/toolchains.xml -Pmutation -pl engine-ports-in -am verify -Djacoco.skip=true -Dtoolchain.jdk.mode=Never -Dtoolchain.jdk.discover=false -DjvmArgs=-XshowSettings:properties`. Before the change: surefire used the installed JDK, PIT `javaExecutable='null'` and minion `java.home = $S/mavenjdk` (Maven's JDK). After: `PIT test JVM: <installed home>/bin/java` and minion `java.home = <installed home>`. Fail-closed branch: with the script's version temporarily set to expect 24, `./mvnw -Pmutation -pl engine-ports-in initialize` failed with "No JDK 25 toolchain selected and Maven runs on JDK 25; PIT would test on it". T6 guard: with the fallback path temporarily pointed at `bin/nojava`, the same command failed with "No java found in the JDK 25 toolchain: …/bin/nojava". Not checked on a real non-25 JDK (none installed) |
| AC-5 | pass | `docs/engine/design.md` section 1, "Build rules the trial found": the bullet beginning "PIT ignores Maven toolchains" (T4 check greps it inside section 1) |
| AC-6 | pass | `engine/README.md`, Gates: plant table with `banned-member` and `core-cycle` and the rule each proves (T4 check) |
