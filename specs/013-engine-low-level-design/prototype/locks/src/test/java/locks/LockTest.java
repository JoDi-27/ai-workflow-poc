package locks;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import locks.LocksApplication.Deadline;
import locks.LocksApplication.Deadlines;
import locks.LocksApplication.Event;
import locks.LocksApplication.Events;
import locks.LocksApplication.Run;
import locks.LocksApplication.StepPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Tests 1-4: the run lock, children inserts under it, lock strength, and DueWork lock order. */
class LockTest extends TestBase {

    @Autowired Deadlines deadlines;
    @Autowired Events events;

    private static final Duration WAIT = Duration.ofMillis(1000);

    private void insertChildren(UUID runId) {
        jdbc.sql("insert into event(id, run_id, seq, type, payload) values (gen_random_uuid(), ?, 1, 'x', '{}')")
                .param(runId).update();
        jdbc.sql("insert into outbox(id, run_id, kind, payload) values (gen_random_uuid(), ?, 'x', '{}')")
                .param(runId).update();
        jdbc.sql("insert into deadline(id, run_id, due_at, kind) values (gen_random_uuid(), ?, now(), 'x')")
                .param(runId).update();
    }

    // ---------------------------------------------------------------- 1
    @Test
    void t1_twoUseCasesOnOneRunSerialize() throws Exception {
        var id = newRun("t1");
        var aLocked = new CountDownLatch(1);
        var a = async(() -> tx.execute(s -> {
            var r = runs.lockById(id).orElseThrow();
            aLocked.countDown();
            sleep(800);
            r.nextSeq++;
            return null;
        }));
        await(aLocked);
        long t0 = System.nanoTime();
        int seen = tx.execute(s -> runs.lockById(id).orElseThrow().nextSeq);
        long waited = msSince(t0);
        a.get(10, TimeUnit.SECONDS);

        var lockSql = SqlLog.matching(" for ");
        log.info("T1 B waited {} ms for the run lock and saw next_seq={}", waited, seen);
        lockSql.stream().distinct().forEach(s -> log.info("T1 lock SQL: {}", s));
        assertThat(waited).isGreaterThan(600);
        assertThat(seen).isEqualTo(2);
        assertThat(lockSql).isNotEmpty().allMatch(s -> s.contains("for no key update"));
    }

    // ---------------------------------------------------------------- 2
    @Test
    void t2_childInsertsDoNotBlockOnNoKeyUpdateButRunUpdateDoes() throws Exception {
        var id = newRun("t2");
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = async(() -> tx.execute(s -> {
            runs.lockById(id).orElseThrow();
            locked.countDown();
            await(release);
            return null;
        }));
        await(locked);

        var inserts = time(() -> tx.execute(s -> {
            lockTimeout(WAIT);
            insertChildren(id);
            return null;
        }));
        var update = time(() -> tx.execute(s -> {
            lockTimeout(WAIT);
            return jdbc.sql("update run set next_seq = next_seq + 1 where id = ?").param(id).update();
        }));
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);

        log.info("T2 under FOR NO KEY UPDATE: child inserts took {} ms (error={}); run update took {} ms (sqlstate={})",
                inserts.ms(), inserts.error(), update.ms(), sqlState(update.error()));
        assertThat(inserts.error()).isNull();
        assertThat(inserts.ms()).isLessThan(500);
        assertThat(sqlState(update.error())).isEqualTo("55P03"); // lock_not_available: it blocked
        assertThat(update.ms()).isGreaterThanOrEqualTo(900);
    }

    @Test
    void t2b_contrast_childInsertsBlockOnForUpdate() throws Exception {
        var id = newRun("t2b");
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = async(() -> tx.execute(s -> {
            jdbc.sql("select id from run where id = ? for update").param(id).query(UUID.class).single();
            locked.countDown();
            await(release);
            return null;
        }));
        await(locked);
        var inserts = time(() -> tx.execute(s -> {
            lockTimeout(WAIT);
            insertChildren(id);
            return null;
        }));
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        log.info("T2b under FOR UPDATE: child insert took {} ms (sqlstate={})", inserts.ms(), sqlState(inserts.error()));
        assertThat(sqlState(inserts.error())).isEqualTo("55P03");
    }

    // ---------------------------------------------------------------- 3
    /** A holds the run lock and updates the given columns (not committed); B inserts children. */
    private Timed<Object> childInsertWhileHolderUpdates(UUID id, String updateSql, List<Map<String, Object>> rowLocks) throws Exception {
        var updated = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = async(() -> tx.execute(s -> {
            runs.lockById(id).orElseThrow();
            jdbc.sql(updateSql).param(id).update();
            updated.countDown();
            await(release);
            return null;
        }));
        await(updated);
        rowLocks.addAll(jdbc.sql("select modes from pgrowlocks('run')").query().listOfRows());
        var inserts = time(() -> tx.execute(s -> {
            lockTimeout(WAIT);
            jdbc.sql("insert into event(id, run_id, seq, type, payload) values (gen_random_uuid(), ?, 1, 'x', '{}')")
                    .param(id).update();
            return null;
        }));
        release.countDown();
        holder.handle((v, e) -> null).get(10, TimeUnit.SECONDS);
        return inserts;
    }

    @Test
    void t3_noKeyUpdateIsEnoughForStatusAndSubjectKeyUpdates() throws Exception {
        jdbc.sql("create extension if not exists pgrowlocks").update();
        var cases = Map.of(
                "status (partial unique index)", "update run set status = 'done' where id = ?",
                "subject_key (partial unique index)", "update run set subject_key = subject_key || '-x' where id = ?",
                "id (primary key)", "update run set id = gen_random_uuid() where id = ?",
                "delete", "delete from run where id = ?");
        var results = new java.util.TreeMap<String, String>();
        for (var c : cases.entrySet()) {
            var id = newRun("t3-" + c.getKey().hashCode());
            var locks = new ArrayList<Map<String, Object>>();
            var r = childInsertWhileHolderUpdates(id, c.getValue(), locks);
            var outcome = "row lock " + locks.stream().map(m -> String.valueOf(m.get("modes"))).toList()
                    + "; child insert " + (r.error() == null ? "ok in " + r.ms() + " ms" : "blocked, sqlstate " + sqlState(r.error()));
            results.put(c.getKey(), outcome);
            log.info("T3 updating {} under FOR NO KEY UPDATE -> {}", c.getKey(), outcome);
        }
        assertThat(results.get("status (partial unique index)")).contains("No Key Update").contains("child insert ok");
        assertThat(results.get("subject_key (partial unique index)")).contains("No Key Update").contains("child insert ok");
        assertThat(results.get("id (primary key)")).contains("{Update}").contains("blocked");
        assertThat(results.get("delete")).contains("{Update}").contains("blocked");
    }

    @Test
    void t3b_oneActiveRunIndexStillEnforcesUnderNoKeyUpdate() throws Exception {
        var r1 = newRun("t3b", "done");
        var r2 = newRun("t3b", "done");
        var aUpdated = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var a = async(() -> tx.execute(s -> {
            runs.lockById(r1).orElseThrow().status = "active";
            runs.flush();
            aUpdated.countDown();
            await(release);
            return null;
        }));
        await(aUpdated);
        var b = async(() -> time(() -> tx.execute(s -> {
            runs.lockById(r2).orElseThrow().status = "active"; // a different row: the run lock does not wait
            runs.flush();                                       // the unique index waits for A
            return null;
        })));
        sleep(600);
        assertThat(b.isDone()).as("B waits on the partial unique index while A is open").isFalse();
        release.countDown();
        a.get(10, TimeUnit.SECONDS);
        var rb = b.get(10, TimeUnit.SECONDS);
        log.info("T3b B waited {} ms, then failed with sqlstate {} ({})", rb.ms(), sqlState(rb.error()),
                rb.error() == null ? null : rb.error().getClass().getSimpleName());
        assertThat(sqlState(rb.error())).isEqualTo("23505");
        assertThat(rb.error()).hasStackTraceContaining("one_active_run");
        assertThat(rb.ms()).isGreaterThan(500);
    }

    // ---------------------------------------------------------------- 4
    private UUID dueDeadline(UUID runId) {
        return tx.execute(s -> deadlines.save(new Deadline(runId, Instant.now().minusSeconds(5), "timeout")).id);
    }

    @Test
    void t4_deadlineThenRunVersusRunThenDeadlineDeadlocks() throws Exception {
        var runId = newRun("t4");
        var dlId = dueDeadline(runId);
        var xHasDeadline = new CountDownLatch(1);
        var yHasRun = new CountDownLatch(1);
        var x = async(() -> time(() -> tx.execute(s -> {   // bad DueWork apply: deadline, then run
            deadlines.lockById(dlId).orElseThrow();
            xHasDeadline.countDown();
            await(yHasRun);
            runs.lockById(runId).orElseThrow();
            return "x";
        })));
        var y = async(() -> time(() -> tx.execute(s -> {   // a use case: run, then its deadlines
            runs.lockById(runId).orElseThrow();
            yHasRun.countDown();
            await(xHasDeadline);
            deadlines.lockByRun(runId);
            return "y";
        })));
        var rx = x.get(15, TimeUnit.SECONDS);
        var ry = y.get(15, TimeUnit.SECONDS);
        var states = new ArrayList<String>();
        states.add(sqlState(rx.error()));
        states.add(sqlState(ry.error()));
        log.info("T4 bad order: X {} ms sqlstate={} ({}), Y {} ms sqlstate={} ({})",
                rx.ms(), sqlState(rx.error()), rx.error() == null ? "-" : rx.error().getClass().getSimpleName(),
                ry.ms(), sqlState(ry.error()), ry.error() == null ? "-" : ry.error().getClass().getSimpleName());
        assertThat(states).containsOnlyOnce("40P01").containsOnlyOnce((String) null);
    }

    /** DueWork done right: short claim tx (SKIP LOCKED, claimed_until), external call without locks, apply run-first. */
    private List<UUID> claim() {
        return tx.execute(s -> {
            var now = Instant.now();
            var due = deadlines.claimDue(now);
            due.forEach(d -> d.claimedUntil = now.plusSeconds(30));
            return due.stream().map(d -> d.id).toList();
        });
    }

    private String apply(UUID runId, UUID dlId) {
        return tx.execute(s -> {
            var run = runs.lockById(runId).orElseThrow();               // 1. run
            var dl = deadlines.lockById(dlId).orElse(null);             // 2. then the deadline
            if (dl == null || dl.claimedUntil == null) return "gone";
            deadlines.delete(dl);
            events.save(new Event(runId, run.nextSeq++, "deadline.fired", p("deadline")));
            return "applied";
        });
    }

    @Test
    void t4b_claimCommitThenRunFirstApplyNeverDeadlocks() throws Exception {
        int rounds = 20;
        int deadlocks = 0;
        int applied = 0;
        for (int i = 0; i < rounds; i++) {
            var runId = newRun("t4b-" + i);
            var dlId = dueDeadline(runId);
            var claimed = claim();
            assertThat(claimed).containsExactly(dlId);
            assertThat(claim()).as("already claimed rows are not claimed again").isEmpty();
            sleep(20); // the external call, with no transaction open
            var start = new CountDownLatch(1);
            var x = async(() -> {
                await(start);
                return time(() -> apply(runId, dlId));
            });
            var y = async(() -> {
                await(start);
                return time(() -> tx.execute(s -> {
                    runs.lockById(runId).orElseThrow();
                    sleep(30);
                    deadlines.lockByRun(runId);
                    return "y";
                }));
            });
            start.countDown();
            var rx = x.get(15, TimeUnit.SECONDS);
            var ry = y.get(15, TimeUnit.SECONDS);
            if ("40P01".equals(sqlState(rx.error())) || "40P01".equals(sqlState(ry.error()))) deadlocks++;
            if ("applied".equals(rx.value())) applied++;
            assertThat(rx.error()).isNull();
            assertThat(ry.error()).isNull();
        }
        log.info("T4b run-first: {} rounds, {} deadlocks, {} applied", rounds, deadlocks, applied);
        assertThat(deadlocks).isZero();
        assertThat(applied).isEqualTo(rounds);
    }

    @Test
    void t4c_claimSkipsDeadlinesAUseCaseHoldsAndNeverWaitsForTheRun() throws Exception {
        var runId = newRun("t4c");
        var dlId = dueDeadline(runId);
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var y = async(() -> tx.execute(s -> {
            runs.lockById(runId).orElseThrow();
            deadlines.lockByRun(runId);
            held.countDown();
            await(release);
            return null;
        }));
        await(held);
        var c = time(this::claim);
        release.countDown();
        y.get(10, TimeUnit.SECONDS);
        log.info("T4c claim while a use case holds run+deadline: {} rows in {} ms", c.value().size(), c.ms());
        assertThat(c.value()).isEmpty();
        assertThat(c.ms()).isLessThan(300);
        assertThat(claim()).containsExactly(dlId);
    }

    private static StepPayload p(String s) {
        return new StepPayload(s, 1, List.of(), Instant.now());
    }
}
