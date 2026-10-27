package locks;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import locks.LocksApplication.Event;
import locks.LocksApplication.Events;
import locks.LocksApplication.StepPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Test 7: per-run gapless event seq under the run lock. */
class SeqTest extends TestBase {

    @Autowired Events events;

    private static final int THREADS = 8;
    private static final int PER_THREAD = 25;

    private static StepPayload p(int i) {
        return new StepPayload("append", i, List.of(), Instant.now());
    }

    private void appendNextSeq(UUID id) {
        tx.executeWithoutResult(s -> {
            var run = runs.lockById(id).orElseThrow();
            events.save(new Event(id, run.nextSeq++, "append", p(0)));
        });
    }

    private void appendMaxPlusOne(UUID id) {
        tx.executeWithoutResult(s -> {
            runs.lockById(id).orElseThrow();
            events.save(new Event(id, events.maxSeq(id) + 1, "append", p(0)));
        });
    }

    private long concurrently(Runnable append) throws Exception {
        long t0 = System.nanoTime();
        var futures = new ArrayList<CompletableFuture<Object>>();
        for (int t = 0; t < THREADS; t++) {
            futures.add(async(() -> {
                for (int i = 0; i < PER_THREAD; i++) append.run();
                return null;
            }));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(60, TimeUnit.SECONDS);
        return msSince(t0);
    }

    private void assertGapless(UUID id, String label, long ms) {
        var seqs = jdbc.sql("select seq from event where run_id = ? order by seq").param(id).query(Integer.class).list();
        var ids = jdbc.sql("select id from event where run_id = ? order by seq").param(id).query(UUID.class).list();
        int inversions = 0;
        for (int i = 1; i < ids.size(); i++) if (ids.get(i - 1).compareTo(ids.get(i)) > 0) inversions++;
        log.info("T7 {}: {} appends in {} ms, seq {}..{}, UUIDv7 order inversions vs seq: {}",
                label, seqs.size(), ms, seqs.getFirst(), seqs.getLast(), inversions);
        assertThat(seqs).containsExactlyElementsOf(IntStream.rangeClosed(1, THREADS * PER_THREAD).boxed().toList());
    }

    @Test
    void t7_nextSeqColumnUnderRunLockIsGapless() throws Exception {
        var id = newRun("t7a");
        long ms = concurrently(() -> appendNextSeq(id));
        assertGapless(id, "next_seq column", ms);
        assertThat(runs.findById(id).orElseThrow().nextSeq).isEqualTo(THREADS * PER_THREAD + 1);
    }

    @Test
    void t7b_maxPlusOneUnderRunLockIsGapless() throws Exception {
        var id = newRun("t7b");
        long ms = concurrently(() -> appendMaxPlusOne(id));
        assertGapless(id, "max(seq)+1", ms);
    }

    @Test
    void t7c_rolledBackAppendLeavesNoGap() {
        var id = newRun("t7c");
        appendNextSeq(id);
        tx.executeWithoutResult(s -> {
            var run = runs.lockById(id).orElseThrow();
            events.save(new Event(id, run.nextSeq++, "append", p(0)));
            s.setRollbackOnly();
        });
        appendNextSeq(id);
        var seqs = jdbc.sql("select seq from event where run_id = ? order by seq").param(id).query(Integer.class).list();
        assertThat(seqs).containsExactly(1, 2);
    }

    @Test
    void t7d_control_maxPlusOneWithoutLockCollides() throws Exception {
        var id = newRun("t7d");
        var barrier = new CyclicBarrier(THREADS);
        var failures = new AtomicInteger();
        var futures = new ArrayList<CompletableFuture<Object>>();
        for (int t = 0; t < THREADS; t++) {
            futures.add(async(() -> {
                try {
                    tx.executeWithoutResult(s -> {
                        int next = events.maxSeq(id) + 1;
                        try {
                            barrier.await(5, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                        events.save(new Event(id, next, "append", p(0)));
                    });
                } catch (RuntimeException e) {
                    if ("23505".equals(sqlState(e))) failures.incrementAndGet();
                    else throw e;
                }
                return null;
            }));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        log.info("T7d no lock: {} of {} appends failed with unique violation on (run_id, seq)", failures.get(), THREADS);
        assertThat(failures.get()).isEqualTo(THREADS - 1);
    }
}
