package aiworkflow.engine.port.in;

import java.util.List;
import java.util.Objects;

/**
 * What a driver needs to choose its next action (docs/driver-api/README.md, Status).
 *
 * <p>Holds only the parts an unbound session has. The run-dependent parts (run, stage, transitions,
 * protected actions, open attempt, last decision, pending work, recommendations) join with the
 * engine core, where they first have values (spec 017, plan D4).
 */
public record StatusView(String contractVersion, Session session, List<StartableWorkflow> startableWorkflows, Next next) {

    public StatusView {
        Objects.requireNonNull(contractVersion, "contractVersion");
        Objects.requireNonNull(session, "session");
        startableWorkflows = List.copyOf(startableWorkflows);
        Objects.requireNonNull(next, "next");
    }

    /** The driver session, and the run it is bound to, if any. */
    public record Session(String id, String boundRunId) {
        public Session {
            Objects.requireNonNull(id, "id");
        }
    }

    /** A workflow the session may start, with the inputs a run needs. */
    public record StartableWorkflow(String workflow, int version, String title, String description, List<RunInput> runInputs) {
        public StartableWorkflow {
            runInputs = List.copyOf(runInputs);
        }
    }

    public record RunInput(String name, String description, boolean required, boolean partOfSubjectKey) {}

    /** The suggested next action and a generic hint. */
    public record Next(NextAction action, String hint) {
        public Next {
            Objects.requireNonNull(action, "action");
        }
    }

    public enum NextAction {
        START_RUN,
        DO_STAGE_TASK,
        REQUEST_TRANSITION,
        WAIT,
        NONE
    }
}
