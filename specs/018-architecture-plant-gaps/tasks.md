# 018 · Tasks: Architecture plant gaps

## Tasks

- [ ] **T1** [P] Add the `banned-member` plant: `core.usecases.Planted` with `authorizeMerge()`, `expect` matching the member rule's message · `engine/architecture-plants/banned-member/` · check: `engine/architecture-plants/run.sh banned-member` → AC-1, AC-3
- [ ] **T2** [P] Add the `core-cycle` plant: `core.plantedalpha` and `core.plantedbeta` referring to each other, `expect` matching the cycle rule's message · `engine/architecture-plants/core-cycle/` · check: `engine/architecture-plants/run.sh core-cycle` → AC-2, AC-3
- [ ] **T3** Pass the JDK toolchain's `java` to PIT: GMavenPlus 5.1.0 with Groovy 5.1.3 in the `mutation` profile, PIT `jvm` from `pit.jvm`; record the before and after run on a copy of JDK 25 as Maven's JDK · `engine/pom.xml` · check: `cd engine && bash -o pipefail -c "./mvnw -B -ntp -Pmutation verify | grep 'PIT test JVM: .*/bin/java'" && scripts/every-module-tested.sh` → AC-4
- [ ] **T4** Update the documents: design section 1 bullet on PIT and toolchains, the plant table in `engine/README.md`, and the unnamed-core-packages item in `BACKLOG.md` "Next" · `docs/engine/design.md`, `engine/README.md`, `BACKLOG.md` · check: `sed -n '/^## 1\./,/^## 2\./p' docs/engine/design.md | grep -q 'PIT ignores Maven toolchains' && grep -q 'banned-member' engine/README.md && grep -q 'core-cycle' engine/README.md` → AC-5, AC-6
- [ ] **T5** Run the full CI gates and every plant, record the results in Evidence, and open the pull request · `specs/018-*/tasks.md`, `specs/STATUS.md` · check: `cd engine && ./mvnw -B -Pmutation verify && scripts/every-module-tested.sh && architecture-plants/run.sh` → AC-3

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
