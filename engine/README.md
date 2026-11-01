# engine

The engine: the single authority for definitions, runs, state, checkpoints, and scoring. One deployable, built from one Maven reactor with six modules (spec 017).

Read: `docs/engine/design.md` (section 1 for the modules), then `docs/architecture/README.md`. To change engine code, load the `engine-change` skill.

## Modules

| Module | Package | Holds |
| --- | --- | --- |
| `engine-ports-in` | `aiworkflow.engine.port.in` | Inbound ports and their records: `DriverOperations`, `Outcome`, `Denial` |
| `engine-ports-out` | `aiworkflow.engine.port.out` | Outbound ports (empty until the DecisionEngine stub) |
| `engine-core` | `aiworkflow.engine.core` | Every rule and all state; `usecases` implements the inbound ports |
| `adapters-in` | `aiworkflow.engine.adapter.in` | `driverapi`: the HTTP driver API under `/v1` |
| `adapters-out` | `aiworkflow.engine.adapter.out` | Outbound adapters (empty until the DecisionEngine stub) |
| `engine-app` | `aiworkflow.engine.app` | Spring Boot main, `application.yaml`, Flyway migrations, ArchUnit tests |

## Commands

You need a JDK 25 installed (the build finds it, whatever `java` points at) and Docker (for the tests and the local database). Run every command from `engine/`: `.mvn/maven.config` points Maven at the project settings (`.mvn/settings.xml`, Maven Central) by a path relative to the current folder.

| Do | Command |
| --- | --- |
| Build and test | `./mvnw -B verify` |
| Mutation testing too, as CI does | `./mvnw -B -Pmutation verify` |
| Check every module with code has tests | `scripts/every-module-tested.sh` (after a build) |
| Prove the module rules fail when broken | `architecture-plants/run.sh` (about a minute) |
| Start the local database | `docker compose up -d` |
| Run the engine | `./mvnw -pl engine-app -am spring-boot:run` |
| Smoke-test the packaged jar | `./mvnw -B -DskipTests package && scripts/smoke-jar.sh` |
| Stop the local database | `docker compose down` (add `-v` to delete its data) |

The running engine listens on `http://localhost:8080`:

```sh
curl localhost:8080/actuator/health
curl 'localhost:8080/v1/status?session_id=s1'
```

Connection settings come from `AWF_DB_URL`, `AWF_DB_USER`, and `AWF_DB_PASSWORD`; the defaults match `compose.yaml`, a local-only database.

## Gates

`./mvnw -B -Pmutation verify` fails when:

- a module uses a class from a module it does not declare (compiler), or uses a dependency it does not declare (`maven-dependency-plugin` `analyze-only`);
- a rule in `engine-app/src/test/.../ArchitectureTest.java` is broken (design section 1, "How the rules are checked");
- a module with code has under 80% line coverage from its own tests (JaCoCo), or under a 70% mutation score (PIT).

JaCoCo and PIT both pass a module that has no tests, so `scripts/every-module-tested.sh` catches that case. `architecture-plants/run.sh` plants one violation per rule and requires each build to fail at the expected tool. CI runs all of them in the required `engine` job.

| Plant | What it breaks | Caught by |
| --- | --- | --- |
| `adapter-to-adapter` | One inbound adapter uses another | ArchUnit `adapters_do_not_use_each_other` |
| `banned-name` | A core class named with `Git` | ArchUnit `no_banned_names_in_core_or_port_classes` |
| `banned-member` | A core method named with `Merge`, in a class with a clean name | ArchUnit `no_banned_names_in_core_or_port_members` |
| `broker-in-core` | The core uses the Pub/Sub client | ArchUnit `broker_product_only_in_brokerclient` |
| `core-call` | `runs` uses `checkpoints` | ArchUnit `core_packages_call_only_what_they_may` |
| `core-cycle` | Two core packages use each other (packages the allowed-calls rule does not name, so only the cycle rule fails) | ArchUnit `core_packages_are_free_of_cycles` |
| `core-uses-adapter` | The core imports an inbound adapter | compiler (module edge) |
| `impure-rule` | A `rules` class uses Spring or JPA | ArchUnit `rules_code_stays_pure` |
| `in-uses-core` | An inbound adapter imports the core | compiler (module edge) |
| `jpa-in-port` | A port uses a JPA type | ArchUnit `ports_use_no_jpa_types` |
| `out-uses-core` | An outbound adapter imports the core | compiler (module edge) |
| `out-uses-other-port` | `systemone` uses the runner port | ArchUnit `systemone_uses_only_its_port` |
| `transitive` | A module uses a dependency it does not declare | `analyze-only` |

PIT starts its own test JVMs and ignores Maven toolchains, so the `mutation` profile passes it the selected JDK 25's `java` and fails if there is none (design section 1).
