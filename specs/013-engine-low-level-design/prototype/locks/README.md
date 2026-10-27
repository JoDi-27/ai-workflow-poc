# Locks prototype (spike 013)

Throwaway prototype. It checks the engine's row locks, the DueWork claim, idempotency, flush order, per-run event `seq`, and the `jsonb` mapper on PostgreSQL 18. It is not production code.

## Versions

| Component | Version |
| --- | --- |
| Java | 25.0.3 (Homebrew `openjdk@25`) |
| Spring Boot (parent) | 4.1.1 |
| Hibernate ORM | 7.4.5.Final |
| Jackson | 3.1.5 (`tools.jackson`) |
| Flyway | 12.4.0 |
| PostgreSQL JDBC | 42.7.13 |
| Testcontainers | 2.0.5, image `postgres:18-alpine` |
| Maven | 3.9.16; Docker Desktop 29.5.2 |

## How to rerun

```sh
cd specs/013-engine-low-level-design/prototype/locks
JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -B test
```

Docker must be running. The suite has 21 tests and takes about 25 seconds. Each test logs a `T<n>` line with its timings and observations.

## Layout

- `src/main/resources/db/migration/V1__init.sql`: `run`, `event`, `outbox`, `deadline`, `idempotency`. `deadline.claimed_until` is an addition to the brief's schema, for the claim-by-timestamp variant.
- `src/main/java/locks/LocksApplication.java`: entities with UUIDv7 ids (`@UuidGenerator(style = VERSION_7)`), repositories, and the record `StepPayload` stored as `jsonb`.
- `src/main/java/locks/SqlLog.java`: a Hibernate `StatementInspector` that records the SQL, so tests can assert on the lock clause.
- `src/test/java/locks/`: `LockTest` (1 to 4), `IdempotencyTest` (5), `FlushTest` (6), `SeqTest` (7), `JsonMapperTest` (8).

Blocking is measured with `set_config('lock_timeout', '1000ms', true)` in the waiting transaction: SQLSTATE `55P03` after about 1000 ms means it blocked; success in a few ms means it did not.

## Results

| # | Test | Expected | Observed | Conclusion |
| --- | --- | --- | --- | --- |
| 1 | `LockTest.t1` | Two use cases on one run serialize through `@Lock(PESSIMISTIC_WRITE)`, emitted as `FOR NO KEY UPDATE` | B waited 803 to 807 ms while A held the lock for 800 ms, then saw A's change (`next_seq=2`). SQL: `... for no key update of r1_0` | Confirmed. Hibernate 7.4.5 on PostgreSQL emits `FOR NO KEY UPDATE` for `PESSIMISTIC_WRITE`, not `FOR UPDATE` (the comment in spike 012's `jpa-trial` that says `FOR UPDATE` is wrong). |
| 2 | `LockTest.t2` | Under A's `FOR NO KEY UPDATE`, B inserts `event`, `outbox`, `deadline` rows for the run without blocking; B's `UPDATE run` blocks | Three inserts took 2 to 3 ms. `UPDATE run set next_seq = ...` failed with `55P03` after 1008 ms | Confirmed. FK checks take `FOR KEY SHARE`, which does not conflict with `FOR NO KEY UPDATE`. Any write to the run row itself waits. |
| 2b | `LockTest.t2b` (control) | Under `FOR UPDATE`, the same inserts block | Insert failed with `55P03` after 1007 ms | Confirmed. `FOR UPDATE` would serialize every child insert behind the run lock. |
| 3 | `LockTest.t3` | Updating `status` or `subject_key` (partial unique index only) keeps the row lock at No Key Update; PK update or delete escalates | `pgrowlocks`: `status` and `subject_key` updates hold `{"No Key Update"}` and child inserts pass in 1 ms. Updating `id` or deleting the run holds `{Update}` and child inserts block (`55P03`) | Confirmed. Partial unique indexes do not count as keys. `FOR NO KEY UPDATE` is enough for every engine use case. Only a PK change or a run delete (for example a retention purge) takes `FOR UPDATE`, and PostgreSQL takes it on its own. |
| 3b | `LockTest.t3b` | The one-active-run index still enforces when `status` changes under the run lock | A set run 1 to `active` (not committed). B locked run 2 at once (different row), but its flush waited 609 ms on the index, then failed `23505` naming `one_active_run` | Confirmed. The index enforces across rows; the run lock does not protect it and does not need to. |
| 4 | `LockTest.t4` | Path X locks deadline then run, path Y locks run then deadline: deadlock | X failed with `40P01` (`CannotAcquireLockException`) after 1010 ms (`deadlock_timeout` 1 s); Y succeeded | Confirmed. PostgreSQL detects the cycle and aborts one side after 1 s. |
| 4b | `LockTest.t4b` | Claim in a short tx (`FOR NO KEY UPDATE SKIP LOCKED`, set `claimed_until`, commit), call external with no tx, then apply locking run first, then deadline: no deadlock | 20 racing rounds against Y (run then deadline): 0 deadlocks, 20 applied. A second claim right after the first returned no rows | Confirmed. Claim-by-timestamp plus "run first" removes the cycle. |
| 4c | `LockTest.t4c` | The claim never waits on a use case that holds the run and its deadline | Claim returned 0 rows in 5 to 7 ms; after release it returned the deadline | Confirmed. `SKIP LOCKED` skips the held row; the claim never touches the run row. |
| 5 | `IdempotencyTest.t5` | Same `(principal, key)`: B's `INSERT ... ON CONFLICT DO NOTHING` waits on the PK until A commits, then reads A's stored response | B waited about 800 ms, inserted 0 rows, then read `{"result": "A"}`. Same key with another request hash was detected as a mismatch; another principal with the same key was independent | Confirmed. In READ COMMITTED the next statement sees A's committed row. |
| 5b | `IdempotencyTest.t5b` | A rolls back (denial): B proceeds normally | B waited about 800 ms, inserted 1 row, ran, and stored `{"result": "B"}` | Confirmed. Only committed results are replayed. |
| 5c | `IdempotencyTest.t5c` (control) | A plain `INSERT` fails and aborts B's tx | B got `23505`, then its read failed with `25P02` (transaction aborted) | Confirmed. Use `ON CONFLICT DO NOTHING` (or a savepoint); a plain insert needs a new tx to read the stored response. |
| 6 | `FlushTest.t6` | Unflushed JPA changes are invisible to `JdbcClient` in the same tx; a JPQL query on the same entity auto-flushes | Before flush: `status=active`, 0 events. After JPQL on `OutboxRow` (unrelated): still `active, 0`. After JPQL on `Event`: `paused, 1` (the `Run` change was flushed too) | Confirmed. Hibernate auto-flushes only when a JPQL query's tables overlap pending changes, and then flushes the whole session. |
| 6b | `FlushTest.t6b` | Native query via the `EntityManager`, and explicit `em.flush()` | An unrelated native query (`select 1`) via the EM flushed the pending change. After `em.flush()` JDBC saw the uncommitted change; a later rollback undid flushed changes | Confirmed. JPA and `JdbcClient` share one connection and one tx. Native queries through the EM flush everything; `JdbcClient` never triggers a flush. |
| 6c | `FlushTest.t6c` | Mixing JDBC and pending JPA writes fools a JDBC replay check | Mixed (event via JDBC, run via JPA): `max_seq=1`, `next_seq-1=0`, a false mismatch. After `em.flush()`: `2 = 2` | Confirmed. See the write-order recommendation below. |
| 7 | `SeqTest.t7` | `seq = run.next_seq`, then `next_seq++`, under the run lock: 8 threads x 25 appends are gapless | `seq` 1..200, no gaps or duplicates, 237 ms; `next_seq=201`; UUIDv7 ids in the same order as `seq` (0 inversions) | Confirmed. |
| 7b | `SeqTest.t7b` | `seq = max(seq)+1` under the run lock: gapless | `seq` 1..200, 161 ms, 0 inversions | Confirmed. Both work; `next_seq` also gives the replay check something to compare. |
| 7c | `SeqTest.t7c` | A rolled-back append leaves no gap | `seq` 1, 2 | Confirmed. The counter rolls back with the tx, unlike a sequence. |
| 7d | `SeqTest.t7d` (control) | `max(seq)+1` without the run lock collides | 7 of 8 concurrent appends failed `23505` on `event_run_seq` | Confirmed. The run lock is what makes `seq` safe; the unique constraint is the backstop. |
| 8 | `JsonMapperTest.Default` | Report Hibernate's default `FormatMapper` for `jsonb`; round-trip a record | `org.hibernate.type.format.jackson.Jackson3JsonFormatMapper`, picked automatically (Jackson 3 is on the class path, Jackson 2 databind is not). Record round trip equal. `Instant` stored as a number: `"at": 1792658089.993479000` | Confirmed, with a catch: Hibernate builds its own `JsonMapper`, not Spring Boot's, so `Instant` is an epoch number. |
| 8b | `JsonMapperTest.Explicit` | `hibernate.type.json_format_mapper=jackson3` | Same mapper, same output | Works, but changes nothing here. |
| 8c | `JsonMapperTest.SpringBootJsonMapper` | A `HibernatePropertiesCustomizer` passes Boot's `JsonMapper` to `new Jackson3JsonFormatMapper(jsonMapper)` | Same mapper class; `Instant` stored as ISO-8601: `"at": "2026-10-22T08:34:49.971780Z"` | Recommended: one Jackson configuration for `jsonb` and HTTP. |

## Emitted lock SQL

```sql
-- Runs.lockById (@Lock(PESSIMISTIC_WRITE))
select r1_0.id,r1_0.next_seq,r1_0.status,r1_0.subject_key,r1_0.version,r1_0.workflow_id
from run r1_0 where r1_0.id=? for no key update of r1_0

-- Deadlines.lockById / lockByRun (@Lock(PESSIMISTIC_WRITE))
select ... from deadline d1_0 where d1_0.id=? for no key update of d1_0

-- Deadlines.claimDue (@Lock(PESSIMISTIC_WRITE), lock timeout hint -2)
select d1_0.id,d1_0.claimed_until,d1_0.due_at,d1_0.kind,d1_0.run_id from deadline d1_0
where d1_0.due_at<=? and (d1_0.claimed_until is null or d1_0.claimed_until<?)
order by d1_0.due_at for no key update of d1_0 skip locked

-- Idempotency (JdbcClient)
insert into idempotency(principal, key, request_hash) values (?, ?, ?)
on conflict (principal, key) do nothing
```

## Recommendations from the results

- **Lock order.** Every use case takes locks in this order: idempotency row, run row, then deadline or outbox rows of that run. DueWork never holds a deadline lock while it waits for a run: it claims in its own short tx (`SKIP LOCKED`, sets `claimed_until`, commits), calls out with no tx open, then applies in a new tx that locks the run first and re-checks the deadline.
- **Lock strength.** `@Lock(PESSIMISTIC_WRITE)` is enough; it already emits `FOR NO KEY UPDATE`. Do not use `FOR UPDATE`: it would block every FK insert of the run's children. Updating `status` or `subject_key` stays at No Key Update; only a PK change or a run delete escalates.
- **Idempotency.** Insert the idempotency row first, with `ON CONFLICT DO NOTHING`, in the use-case tx. Write the response in the same tx. A denial rolls back and stores nothing.
- **Write order for state, events, and outbox.** Write all three through JPA in the same session, in the order: update the run (lock already held), persist events with `seq` from `run.next_seq`, persist outbox rows. Call `em.flush()` before any `JdbcClient` statement in the same tx, and before the replay check. Do not mix JDBC writes with pending JPA changes. Hibernate's auto-flush is not a safe guard: it fires only for JPQL on overlapping tables and for native queries through the EM, never for `JdbcClient`.
- **Event `seq`.** Use a `next_seq` column on `run`, incremented under the run lock. It is gapless, rolls back cleanly, and gives the replay check `next_seq - 1 = max(seq)`.
- **`jsonb`.** Pass Spring Boot's `JsonMapper` to Hibernate through a `HibernatePropertiesCustomizer` (`new Jackson3JsonFormatMapper(jsonMapper)`), so dates are ISO-8601 and `jsonb` matches the HTTP API.
