package locks;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import locks.LocksApplication.Event;
import locks.LocksApplication.Events;
import locks.LocksApplication.OutboxRow;
import locks.LocksApplication.Outbox;
import locks.LocksApplication.StepPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Test 6: what JdbcClient sees of pending JPA changes in the same transaction. */
class FlushTest extends TestBase {

    @Autowired EntityManager em;
    @Autowired Events events;
    @Autowired Outbox outbox;

    private String jdbcStatus(java.util.UUID id) {
        return jdbc.sql("select status from run where id = ?").param(id).query(String.class).single();
    }

    private long jdbcEvents(java.util.UUID id) {
        return jdbc.sql("select count(*) from event where run_id = ?").param(id).query(Long.class).single();
    }

    private static StepPayload p() {
        return new StepPayload("s", 1, List.of(), Instant.now());
    }

    @Test
    void t6_unflushedChangesAreInvisibleToJdbcUntilFlush() {
        var id = newRun("t6");
        var seen = tx.execute(s -> {
            var obs = new LinkedHashMap<String, Object>();
            var run = runs.lockById(id).orElseThrow();
            run.status = "paused";
            events.save(new Event(id, run.nextSeq++, "paused", p()));
            obs.put("jdbc before flush (status, events)", jdbcStatus(id) + ", " + jdbcEvents(id));

            // JPQL on an unrelated entity: does Hibernate auto-flush the Run/Event changes?
            em.createQuery("select count(o) from OutboxRow o").getSingleResult();
            obs.put("jdbc after JPQL on OutboxRow", jdbcStatus(id) + ", " + jdbcEvents(id));

            // JPQL touching Event: auto-flush expected
            events.maxSeq(id);
            obs.put("jdbc after JPQL on Event", jdbcStatus(id) + ", " + jdbcEvents(id));
            return obs;
        });
        log.info("T6 auto-flush observations: {}", seen);
        assertThat(seen.get("jdbc before flush (status, events)")).isEqualTo("active, 0");
        assertThat(seen.get("jdbc after JPQL on Event")).isEqualTo("paused, 1");
    }

    @Test
    void t6b_nativeQueryAndExplicitFlush() {
        var id = newRun("t6b");
        var seen = tx.execute(s -> {
            var obs = new LinkedHashMap<String, Object>();
            runs.lockById(id).orElseThrow().status = "paused";
            em.createNativeQuery("select 1").getSingleResult();
            obs.put("jdbc after unrelated native query via EM", jdbcStatus(id));
            return obs;
        });
        var id2 = newRun("t6b-2");
        var flushed = tx.execute(s -> {
            runs.lockById(id2).orElseThrow().status = "paused";
            em.flush();
            return jdbcStatus(id2);
        });
        log.info("T6b {}; after em.flush(): {}", seen, flushed);
        assertThat(flushed).isEqualTo("paused"); // same connection, uncommitted data visible after flush
        // rollback still undoes flushed changes: JDBC and JPA share one transaction
        tx.execute(s -> {
            runs.lockById(id2).orElseThrow().status = "done";
            em.flush();
            s.setRollbackOnly();
            return null;
        });
        assertThat(jdbcStatus(id2)).isEqualTo("paused");
    }

    /** The replay check: run.next_seq - 1 must equal max(event.seq). It reads through JDBC. */
    private Map<String, Integer> replayView(java.util.UUID id) {
        return Map.of(
                "next_seq-1", jdbc.sql("select next_seq - 1 from run where id = ?").param(id).query(Integer.class).single(),
                "max_seq", jdbc.sql("select coalesce(max(seq), 0) from event where run_id = ?").param(id).query(Integer.class).single());
    }

    @Test
    void t6c_mixedWritesFoolTheReplayCheckAndFlushFixesIt() {
        var id = newRun("t6c");
        var views = tx.execute(s -> {
            var obs = new LinkedHashMap<String, Object>();
            var run = runs.lockById(id).orElseThrow();
            // Mixed: state via JPA (pending), event via JDBC (immediate)
            int seq = run.nextSeq++;
            jdbc.sql("insert into event(id, run_id, seq, type, payload) values (gen_random_uuid(), ?, ?, 'x', '{}')")
                    .params(id, seq).update();
            obs.put("mixed, before flush", replayView(id));
            // All via JPA: state + event + outbox pending together
            int seq2 = run.nextSeq++;
            events.save(new Event(id, seq2, "y", p()));
            outbox.save(new OutboxRow(id, "notify", p()));
            obs.put("all JPA, before flush", replayView(id));
            em.flush();
            obs.put("after flush", replayView(id));
            return obs;
        });
        log.info("T6c replay views: {}", views);
        var mixed = (Map<?, ?>) views.get("mixed, before flush");
        assertThat(mixed.get("next_seq-1")).isNotEqualTo(mixed.get("max_seq")); // check would fail falsely
        var after = (Map<?, ?>) views.get("after flush");
        assertThat(after.get("next_seq-1")).isEqualTo(after.get("max_seq"));
    }
}
