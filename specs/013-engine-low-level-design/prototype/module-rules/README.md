# Module-rules prototype (spike 013)

Throwaway. It checks how the engine's dependency rules (architecture README "Dependency rules", AD-20) are enforced in the build. Not production code.

## Versions

| Item | Version |
| --- | --- |
| Java | OpenJDK 25.0.3 (Homebrew), `release 25` |
| Maven | 3.9.16 |
| Spring Boot parent | 4.1.1 (Spring Framework 7.0.9) |
| maven-dependency-plugin | 3.11.0, `analyze-only`, `failOnWarning=true`, bound to `verify` in every module |
| ArchUnit | 1.5.1 (`archunit`, `archunit-junit5-api`, `archunit-junit5-engine`); runs on Java 25 and Boot 4.1's JUnit without trouble |
| maven-compiler-plugin / surefire | 3.15.0 / 3.5.6 (from the Boot parent) |
| google-cloud-pubsub | from `libraries-bom` 26.90.0 |

## Layout

| Module | Packages | Depends on (Maven) |
| --- | --- | --- |
| `engine-ports-in` | `poc.engine.ports.in` | nothing |
| `engine-ports-out` | `poc.engine.ports.out.{runner,decisions,events}` | nothing |
| `engine-core` | `poc.engine.core.{identity,definitions,runs,checkpoints,evaluationtasks,scoring,typeddecisions,outbox}` | ports-in, ports-out, spring-context, jakarta.persistence-api |
| `driver-api` | `poc.engine.adapter.in.driverapi` | ports-in, spring-web |
| `scheduler` | `poc.engine.adapter.in.scheduler` | ports-in, spring-context |
| `runner-client` | `poc.engine.adapter.out.runnerclient` | ports-out, spring-context |
| `systemone` | `poc.engine.adapter.out.systemone` | ports-out, spring-context |
| `broker-client` | `poc.engine.adapter.out.brokerclient` | ports-out, spring-context, google-cloud-pubsub |
| `engine-app` | `poc.engine.app` (Boot main) + `ArchitectureTest` (test scope) | all modules, spring-boot, spring-boot-autoconfigure, spring-context |

The core is one module with one package per core module. `RunService` implements the inbound port `RunCommands`. `RunEntity` is a JPA `@Entity` in `core.runs`.

## Three layers of enforcement

1. **Maven module dependencies (compiler).** A module cannot import a class from a module it does not declare. Catches wrong edges at `compile`, in the module that broke the rule.
2. **`dependency:analyze-only` (failOnWarning).** Declared dependencies must equal used ones. Catches classes used only through a transitive dependency, and dead declarations, at `verify`.
3. **ArchUnit (`engine-app/src/test/.../ArchitectureTest.java`).** Catches what a Maven edge cannot express: a rule broken after someone also adds the forbidden dependency to a pom, a second port inside the one `engine-ports-out` module, banned names, the broker product outside `broker-client`, cycles between core packages (they share one module), and JPA types in ports.

### Where the ArchUnit tests live

In `engine-app` test scope. `engine-app` is the only module whose classpath holds every module, so one `@AnalyzeClasses(packages = "poc.engine")` sees every edge, including adapter-to-adapter and adapter-to-core edges. A test in `engine-core` would see only the core and ports, so it could not check adapters. The cost is that an ArchUnit failure only shows when the reactor reaches `engine-app` (last). A dedicated `architecture-tests` module that depends on everything would work the same way; it is only worth it if `engine-app` grows slow tests of its own.

Rules: inbound adapters use only inbound ports; the core does not use adapters or the app; ports reference no core, adapter, or `jakarta.persistence` types; `@Entity` classes live only in the core; each outbound adapter uses only its own port package (no other port, other outbound adapter, core, or inbound side); no class, package, field, or method name in the core or ports matches `(?i).*(git|claude|sdlc|merge|pullrequest).*`; `com.google.cloud.pubsub..` and `com.google.pubsub..` only in `broker-client`; core package slices are free of cycles.

## How to run

```sh
./run-trial.sh            # every plant, then a clean build that must be green
./run-trial.sh 3b 7       # selected plants
```

The script copies `plants/<id>/` into the tree, runs `mvn clean verify -Dplant=<id>`, and removes the files. `-Dplant=<id>` also turns on a pom profile with the same id where a plant needs a pom change too (1b, 2b, 6, 9b), which stands for someone adding the forbidden dependency. Logs go to `logs/<id>.log`. The script uses `-o` (offline) and `maven-settings.xml`, which points at Maven Central directly; run once with `MVN_OFFLINE= ./run-trial.sh` to download dependencies first.

Clean build: `mvn -s maven-settings.xml verify`, 11 ArchUnit tests pass, about 2 s offline.

## Results

| # | Violation planted | Caught by | Build phase | Error excerpt |
| --- | --- | --- | --- | --- |
| 1 | `driver-api` imports `core.runs.RunService` | Compiler (module deps) | `compile` of `driver-api` | `PlantedCoreUse.java:[3,28] package poc.engine.core.runs does not exist` |
| 1b | Same, and `driver-api` pom declares `engine-core` | ArchUnit | `test` of `engine-app` | `inbound_adapters_use_only_inbound_ports ... Field <...driverapi.PlantedCoreUse.runs> has type <poc.engine.core.runs.RunService>` |
| 2 | Core imports `runnerclient.HttpRunnerClient` | Compiler | `compile` of `engine-core` | `package poc.engine.adapter.out.runnerclient does not exist` |
| 2b | Same, and core pom declares `runner-client` | ArchUnit | `test` of `engine-app` | `core_does_not_depend_on_adapters ... Field <...core.runs.PlantedAdapterUse.client> has type <...runnerclient.HttpRunnerClient>` |
| 3a | `systemone` imports another outbound adapter (`HttpRunnerClient`) | Compiler | `compile` of `systemone` | `package poc.engine.adapter.out.runnerclient does not exist` |
| 3b | `systemone` uses another outbound port (`RunnerPort`) | ArchUnit | `test` of `engine-app` | `Rule 'outbound adapter systemone depends only on port decisions' was violated ... has type <poc.engine.ports.out.runner.RunnerPort>` |
| 4 | `driver-api` uses `org.springframework.util.StringUtils` (spring-core, only transitive through spring-web) | analyze-only | `verify` of `driver-api` | `Used undeclared dependencies found: org.springframework:spring-core:jar:7.0.9:compile` |
| 5a | `core.runs.GitRevision` | ArchUnit | `test` of `engine-app` | `Class <poc.engine.core.runs.GitRevision> has name matching '(?i).*(git\|claude\|sdlc\|merge\|pullrequest).*'` |
| 5b | Package `core.claude` (`HookBridge`) | ArchUnit | `test` of `engine-app` | `Class <poc.engine.core.claude.HookBridge> has name matching ...` |
| 5c | Core members `pullRequestUrl`, `authorizeMerge()` | ArchUnit (`noMembers`) | `test` of `engine-app` | `Field <...PlantedMemberName.pullRequestUrl> has name matching ...`, `Method <...authorizeMerge()> has name matching ...` |
| 6 | Core uses `com.google.cloud.pubsub.v1.Publisher`, core pom declares pubsub | ArchUnit | `test` of `engine-app` | `broker_product_only_in_broker_client ... Field <...core.outbox.PlantedBrokerUse.publisher> has type <com.google.cloud.pubsub.v1.Publisher>` |
| 7 | Cycle: `core.definitions` uses `core.runs.RunEntity` (runs → checkpoints → definitions already) | ArchUnit (slices) | `test` of `engine-app` | `slices matching 'poc.engine.core.(*)..' should be free of cycles' was violated (2 times): Cycle detected: Slice checkpoints -> ...` |
| 8 | String literals `"git"`, `"claude merge "` in core | Not caught (as expected) | build green | ArchUnit checks names and type references, not string constants |
| 9a | Inbound port method returns core `RunEntity` | Compiler | `compile` of `engine-ports-in` | `PlantedEntityPort.java:[3,28] package poc.engine.core.runs does not exist` |
| 9b | `@Entity` class in `ports.in`, ports-in pom declares jakarta.persistence-api | ArchUnit (2 rules) | `test` of `engine-app` | `ports_reference_no_core_adapter_or_jpa_types ... is annotated with <jakarta.persistence.Entity>`; `entities_live_only_in_core ... does not reside in a package 'poc.engine.core..'` |

Without its pom profile, plant 6 fails to compile in `engine-core` (`package com.google.cloud.pubsub.v1 does not exist`), like 1 and 2.

## Findings

- The compiler catches every wrong edge between modules, in the module that broke it and at the earliest phase. ArchUnit is the second line for the same rules once someone adds the dependency to a pom, and the only line for rules inside one module (core cycles, the one ports-out module, names, broker product).
- `analyze-only` with `failOnWarning` passes on every library module with exact declarations. It needs one exception in `engine-app`: the app wires modules by component scan, so no bytecode references them and they show as "Unused declared". The JUnit engine is runtime-only and shows the same way. `engine-app` therefore ignores `spike013:*` and `archunit-junit5-engine` for unused-declared checks; used-undeclared is still checked. The first run also flagged `spring-context` as used-undeclared in `engine-app` (from `@SpringBootApplication(scanBasePackages = ...)`), so it is declared.
- Spring Boot starters do not fit `analyze-only`: a starter has no classes, so it is always "unused declared", and the real jars are "used undeclared". The modules declare the real artifacts (`spring-web`, `spring-context`, `spring-boot`, `spring-boot-autoconfigure`) instead of starters. Same for ArchUnit: declare `archunit` and `archunit-junit5-api`, not the `archunit-junit5` aggregator.
- One `engine-ports-out` module means the compiler cannot stop `systemone` using `RunnerPort` (3b). ArchUnit covers it. One module per outbound port would move that to the compiler, at the cost of three more tiny modules.
- The name rule is a case-insensitive substring match. It would also flag innocent names such as `digit`, `legitimate`, or `emerge`. Splitting camel-case tokens would avoid that if it turns out to matter. Parameter and local variable names are not checked.
- String literals are invisible to ArchUnit (8). Banned words in configuration values or messages need a different check (for example a grep in CI), if wanted.
- Build cost is small: the clean offline build takes about 2 s, and the 11 ArchUnit rules about 0.5 s.
- Environment: the user's `~/.m2/settings.xml` mirrors everything to a corporate Nexus that stalled. `maven-settings.xml` here points at Maven Central directly. The project would need its own `.mvn/` settings or CI settings for the same reason.
