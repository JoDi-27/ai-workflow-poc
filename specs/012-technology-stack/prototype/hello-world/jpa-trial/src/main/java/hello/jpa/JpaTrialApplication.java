package hello.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Spike 012 trial: the engine's storage patterns (spec 010 AD-16, AD-23) through JPA on PostgreSQL 18. */
@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class JpaTrialApplication {
    public static void main(String[] args) {
        SpringApplication.run(JpaTrialApplication.class, args);
    }

    @Entity(name = "Run")
    @Table(name = "run")
    public static class Run {
        @Id public UUID id;
        public String workflow;
        @Column(name = "subject_key") public String subjectKey;
        public String status;
        @JdbcTypeCode(SqlTypes.JSON) public Map<String, Object> state;

        protected Run() {}

        public Run(String workflow, String subjectKey, String status, Map<String, Object> state) {
            this.id = UUID.randomUUID();
            this.workflow = workflow;
            this.subjectKey = subjectKey;
            this.status = status;
            this.state = state;
        }
    }

    @Entity(name = "OutboxRow")
    @Table(name = "outbox")
    public static class OutboxRow {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
        @Column(name = "run_id") public UUID runId;
        @JdbcTypeCode(SqlTypes.JSON) public Map<String, Object> payload;
        @Column(name = "published_at") public Instant publishedAt;

        protected OutboxRow() {}

        public OutboxRow(UUID runId, Map<String, Object> payload) {
            this.runId = runId;
            this.payload = payload;
        }
    }

    public interface Runs extends JpaRepository<Run, UUID> {
        /** The per-run row lock every use case takes: SELECT ... FOR UPDATE. */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select r from Run r where r.id = :id")
        Optional<Run> lockById(UUID id);

        /** Same lock, failing at once instead of waiting: FOR UPDATE NOWAIT. */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
        @Query("select r from Run r where r.id = :id")
        Optional<Run> lockByIdNoWait(UUID id);

        @Query(value = "select * from run where state ->> 'stage' = :stage", nativeQuery = true)
        List<Run> findByStage(String stage);
    }

    public interface Outbox extends JpaRepository<OutboxRow, Long> {
        /** DueWork's claim: FOR UPDATE SKIP LOCKED (lock timeout -2 is Hibernate's SKIP_LOCKED). */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
        @Query("select o from OutboxRow o where o.publishedAt is null order by o.id")
        List<OutboxRow> claimDue(Limit limit);
    }
}
