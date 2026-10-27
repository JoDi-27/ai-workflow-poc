package locks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Test 5: concurrent requests with the same (principal, key). */
class IdempotencyTest extends TestBase {

    /** Returns true when this request owns the key; false when another request already stored it. */
    private boolean claimKey(String principal, String key, String hash) {
        return jdbc.sql("""
                insert into idempotency(principal, key, request_hash) values (?, ?, ?)
                on conflict (principal, key) do nothing""")
                .params(principal, key, hash).update() == 1;
    }

    private Map<String, Object> stored(String principal, String key) {
        return jdbc.sql("select request_hash, response::text as response from idempotency where principal = ? and key = ?")
                .params(principal, key).query().singleRow();
    }

    private void storeResponse(String principal, String key, String json) {
        jdbc.sql("update idempotency set response = cast(? as jsonb) where principal = ? and key = ?")
                .params(json, principal, key).update();
    }

    /** One use case: the idempotency row first, then (in a real engine) the run lock and the work. */
    private String request(String principal, String key, String hash, String result, long workMs,
                           CountDownLatch claimed, boolean deny) {
        return tx.execute(s -> {
            if (!claimKey(principal, key, hash)) {
                var row = stored(principal, key);
                if (!hash.equals(row.get("request_hash"))) return "mismatch";
                return "replayed " + row.get("response");
            }
            if (claimed != null) claimed.countDown();
            sleep(workMs);
            if (deny) {
                s.setRollbackOnly(); // denial: nothing is stored
                return "denied";
            }
            storeResponse(principal, key, "{\"result\": \"" + result + "\"}");
            return "fresh " + result;
        });
    }

    @Test
    void t5_secondWaitsThenReplaysCommittedResponse() throws Exception {
        var claimed = new CountDownLatch(1);
        var a = async(() -> request("alice", "k1", "h", "A", 800, claimed, false));
        await(claimed);
        var b = time(() -> request("alice", "k1", "h", "B", 0, null, false));
        log.info("T5 A={}, B waited {} ms -> {}", a.get(10, TimeUnit.SECONDS), b.ms(), b.value());
        assertThat(a.get()).isEqualTo("fresh A");
        assertThat(b.value()).isEqualTo("replayed {\"result\": \"A\"}");
        assertThat(b.ms()).isGreaterThan(500);
        // a different body under the same key is detectable
        assertThat(request("alice", "k1", "other", "C", 0, null, false)).isEqualTo("mismatch");
        // a different principal with the same key is independent
        assertThat(request("bob", "k1", "h", "D", 0, null, false)).isEqualTo("fresh D");
    }

    @Test
    void t5b_firstRollsBackSoSecondProceeds() throws Exception {
        var claimed = new CountDownLatch(1);
        var a = async(() -> request("alice", "k2", "h", "A", 800, claimed, true));
        await(claimed);
        var b = time(() -> request("alice", "k2", "h", "B", 0, null, false));
        log.info("T5b A={}, B waited {} ms -> {}", a.get(10, TimeUnit.SECONDS), b.ms(), b.value());
        assertThat(a.get()).isEqualTo("denied");
        assertThat(b.value()).isEqualTo("fresh B");
        assertThat(b.ms()).isGreaterThan(500);
        assertThat(stored("alice", "k2").get("response")).isEqualTo("{\"result\": \"B\"}");
    }

    @Test
    void t5c_plainInsertFailsAndAbortsTheTransaction() throws Exception {
        var claimed = new CountDownLatch(1);
        var a = async(() -> request("alice", "k3", "h", "A", 800, claimed, false));
        await(claimed);
        var b = time(() -> tx.execute(s -> {
            String first;
            try {
                jdbc.sql("insert into idempotency(principal, key, request_hash) values ('alice', 'k3', 'h')").update();
                first = "inserted";
            } catch (RuntimeException e) {
                first = "insert failed " + sqlState(e);
            }
            String second;
            try {
                second = "read " + stored("alice", "k3").get("response");
            } catch (RuntimeException e) {
                second = "read failed " + sqlState(e);
            }
            s.setRollbackOnly();
            return first + "; " + second;
        }));
        a.get(10, TimeUnit.SECONDS);
        log.info("T5c plain insert: waited {} ms -> {}", b.ms(), b.value());
        assertThat(b.value()).isEqualTo("insert failed 23505; read failed 25P02");
    }
}
