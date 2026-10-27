package locks;

import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import locks.LocksApplication.Run;
import locks.LocksApplication.Runs;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Import(TestBase.Containers.class)
abstract class TestBase {
    protected final Logger log = LoggerFactory.getLogger("locks." + getClass().getSimpleName());
    protected static final ExecutorService POOL = Executors.newCachedThreadPool();

    @TestConfiguration(proxyBeanMethods = false)
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18-alpine");
        }
    }

    @Autowired protected TransactionTemplate tx;
    @Autowired protected JdbcClient jdbc;
    @Autowired protected Runs runs;

    @BeforeEach
    void clean() {
        jdbc.sql("truncate idempotency, event, outbox, deadline, run").update();
        SqlLog.STATEMENTS.clear();
    }

    protected UUID newRun(String subject) {
        return newRun(subject, "active");
    }

    protected UUID newRun(String subject, String status) {
        return tx.execute(s -> runs.save(new Run("wf", subject, status)).id);
    }

    protected <T> CompletableFuture<T> async(Callable<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return work.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, POOL);
    }

    /** Bounds how long the current transaction waits for any lock. */
    protected void lockTimeout(Duration d) {
        jdbc.sql("select set_config('lock_timeout', ?, true)").param(d.toMillis() + "ms").query(String.class).single();
    }

    protected static String sqlState(Throwable t) {
        for (var c = t; c != null; c = c.getCause()) {
            if (c instanceof SQLException e && e.getSQLState() != null) return e.getSQLState();
        }
        return null;
    }

    protected static void await(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("latch timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    protected static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    protected static long msSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** Runs the block and returns its elapsed time, or throws with timing attached. */
    protected record Timed<T>(T value, long ms, Throwable error) {}

    protected static <T> Timed<T> time(Callable<T> work) {
        long t0 = System.nanoTime();
        try {
            return new Timed<>(work.call(), msSince(t0), null);
        } catch (Throwable e) {
            return new Timed<>(null, msSince(t0), e);
        }
    }
}
