package locks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Spike 013 prototype: row locks, claims, idempotency, and flush order on PostgreSQL 18. */
@SpringBootApplication
@EnableJpaRepositories(considerNestedRepositories = true)
public class LocksApplication {
    public static void main(String[] args) {
        SpringApplication.run(LocksApplication.class, args);
    }

    /** A typed event payload stored as jsonb (test 8). */
    public record StepPayload(String step, int attempt, List<String> tags, Instant at) {}

    @Entity(name = "Run")
    @Table(name = "run")
    public static class Run {
        @Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7) public UUID id;
        @Column(name = "workflow_id") public String workflowId;
        @Column(name = "subject_key") public String subjectKey;
        public String status;
        @Column(name = "next_seq") public int nextSeq = 1;
        @Version public long version;

        protected Run() {}

        public Run(String workflowId, String subjectKey, String status) {
            this.workflowId = workflowId;
            this.subjectKey = subjectKey;
            this.status = status;
        }
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event {
        @Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7) public UUID id;
        @Column(name = "run_id") public UUID runId;
        public int seq;
        public String type;
        @JdbcTypeCode(SqlTypes.JSON) public StepPayload payload;

        protected Event() {}

        public Event(UUID runId, int seq, String type, StepPayload payload) {
            this.runId = runId;
            this.seq = seq;
            this.type = type;
            this.payload = payload;
        }
    }

    @Entity(name = "OutboxRow")
    @Table(name = "outbox")
    public static class OutboxRow {
        @Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7) public UUID id;
        @Column(name = "run_id") public UUID runId;
        public String kind;
        @JdbcTypeCode(SqlTypes.JSON) public StepPayload payload;
        @Column(name = "due_at") public Instant dueAt = Instant.now();
        @Column(name = "claimed_until") public Instant claimedUntil;

        protected OutboxRow() {}

        public OutboxRow(UUID runId, String kind, StepPayload payload) {
            this.runId = runId;
            this.kind = kind;
            this.payload = payload;
        }
    }

    @Entity(name = "Deadline")
    @Table(name = "deadline")
    public static class Deadline {
        @Id @UuidGenerator(style = UuidGenerator.Style.VERSION_7) public UUID id;
        @Column(name = "run_id") public UUID runId;
        @Column(name = "due_at") public Instant dueAt;
        public String kind;
        @Column(name = "claimed_until") public Instant claimedUntil;

        protected Deadline() {}

        public Deadline(UUID runId, Instant dueAt, String kind) {
            this.runId = runId;
            this.dueAt = dueAt;
            this.kind = kind;
        }
    }

    public interface Runs extends JpaRepository<Run, UUID> {
        /** The per-run lock every use case takes first. */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select r from Run r where r.id = :id")
        Optional<Run> lockById(UUID id);
    }

    public interface Events extends JpaRepository<Event, UUID> {
        @Query("select coalesce(max(e.seq), 0) from Event e where e.runId = :runId")
        int maxSeq(UUID runId);

        @Query("select e from Event e where e.runId = :runId order by e.seq")
        List<Event> ofRun(UUID runId);
    }

    public interface Outbox extends JpaRepository<OutboxRow, UUID> {}

    public interface Deadlines extends JpaRepository<Deadline, UUID> {
        /** DueWork's claim: lock due rows, skipping those another transaction holds. */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
        @Query("select d from Deadline d where d.dueAt <= :now and (d.claimedUntil is null or d.claimedUntil < :now) order by d.dueAt")
        List<Deadline> claimDue(Instant now);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select d from Deadline d where d.id = :id")
        Optional<Deadline> lockById(UUID id);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select d from Deadline d where d.runId = :runId")
        List<Deadline> lockByRun(UUID runId);
    }
}
