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
