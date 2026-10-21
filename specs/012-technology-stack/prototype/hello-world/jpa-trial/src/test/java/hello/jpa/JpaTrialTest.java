package hello.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hello.jpa.JpaTrialApplication.Outbox;
import hello.jpa.JpaTrialApplication.OutboxRow;
import hello.jpa.JpaTrialApplication.Run;
import hello.jpa.JpaTrialApplication.Runs;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Limit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class JpaTrialTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired Runs runs;
    @Autowired Outbox outbox;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("truncate outbox, run cascade");
    }

    @Test
    void flywayMigratesAndHibernateValidatesJsonb() {
        assertThat(jdbc.queryForObject("select version()", String.class)).startsWith("PostgreSQL 18");
        assertThat(jdbc.queryForObject(
                "select data_type from information_schema.columns where table_name='run' and column_name='state'",
                String.class)).isEqualTo("jsonb");
    }

    @Test
    void jsonbRoundTripAndQuery() {
        var run = runs.save(new Run("sdlc-reference", "repo/a", "active",
                Map.of("stage", "implementation", "attempts", List.of(1, 2), "nested", Map.of("ok", true))));
        var loaded = runs.findById(run.id).orElseThrow();
        assertThat(loaded.state).containsEntry("stage", "implementation").containsEntry("attempts", List.of(1, 2));
        assertThat(runs.findByStage("implementation")).extracting(r -> r.id).containsExactly(run.id);
    }

    @Test
    void oneActiveRunPerWorkflowAndSubject() {
        runs.saveAndFlush(new Run("sdlc-reference", "repo/a", "active", Map.of()));
        runs.saveAndFlush(new Run("sdlc-reference", "repo/a", "done", Map.of()));   // inactive: allowed
        assertThatThrownBy(() -> runs.saveAndFlush(new Run("sdlc-reference", "repo/a", "active", Map.of())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("one_active_run");
    }

    @Test
    void runRowLockBlocksASecondTransaction() throws Exception {
        var run = runs.save(new Run("sdlc-reference", "repo/b", "active", Map.of()));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            runs.lockById(run.id).orElseThrow();
            locked.countDown();
            await(release);
        }));
        locked.await(10, TimeUnit.SECONDS);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> runs.lockByIdNoWait(run.id)))
                .isInstanceOf(PessimisticLockingFailureException.class);
        release.countDown();
        holder.get(10, TimeUnit.SECONDS);
        tx.executeWithoutResult(s -> assertThat(runs.lockByIdNoWait(run.id)).isPresent());
    }

    @Test
    void outboxClaimSkipsLockedRows() throws Exception {
        var run = runs.save(new Run("sdlc-reference", "repo/c", "active", Map.of()));
        for (int i = 0; i < 4; i++) outbox.save(new OutboxRow(run.id, Map.of("n", i)));
        var claimed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> tx.execute(s -> {
            var rows = outbox.claimDue(Limit.of(2)).stream().map(r -> r.id).toList();
            claimed.countDown();
            await(release);
            return rows;
        }));
        claimed.await(10, TimeUnit.SECONDS);
        var second = tx.execute(s -> outbox.claimDue(Limit.of(2)).stream().map(r -> r.id).toList());
        release.countDown();
        var firstRows = first.get(10, TimeUnit.SECONDS);
        assertThat(firstRows).hasSize(2);
        assertThat(second).hasSize(2).doesNotContainAnyElementsOf(firstRows);
    }

    static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
