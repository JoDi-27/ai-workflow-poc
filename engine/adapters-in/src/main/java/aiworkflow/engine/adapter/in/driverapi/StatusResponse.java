package aiworkflow.engine.adapter.in.driverapi;

import aiworkflow.engine.port.in.StatusView;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The contract's Status, field for field (docs/driver-api/README.md, Status). Hand-written, not
 * generated (spec 012 Q13). The run-dependent fields are always null or empty until the engine core
 * puts them on the port (spec 017, plan D4); their item types are left open until then.
 */
record StatusResponse(
        @JsonProperty("contract_version") String contractVersion,
        Session session,
        @JsonProperty("startable_workflows") List<StartableWorkflow> startableWorkflows,
        Object run,
        Object stage,
        @JsonProperty("permitted_transitions") List<Object> permittedTransitions,
        @JsonProperty("protected_actions") List<Object> protectedActions,
        @JsonProperty("open_attempt") Object openAttempt,
        @JsonProperty("last_decision") Object lastDecision,
        Pending pending,
        List<Object> recommendations,
        Next next) {

    record Session(String id, @JsonProperty("bound_run_id") String boundRunId) {}

    record StartableWorkflow(String workflow, int version, String title, String description,
            @JsonProperty("run_inputs") List<RunInput> runInputs) {}

    record RunInput(String name, String description, boolean required,
            @JsonProperty("part_of_subject_key") boolean partOfSubjectKey) {}

    record Pending(@JsonProperty("human_steps") List<Object> humanSteps, List<Object> escalations,
            @JsonProperty("evaluation_tasks") List<Object> evaluationTasks) {}

    record Next(String action, String hint) {}

    static StatusResponse from(StatusView view) {
        return new StatusResponse(
                view.contractVersion(),
                new Session(view.session().id(), view.session().boundRunId()),
                view.startableWorkflows().stream().map(StatusResponse::workflow).toList(),
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                new Pending(List.of(), List.of(), List.of()),
                List.of(),
                new Next(view.next().action().name().toLowerCase(), view.next().hint()));
    }

    private static StartableWorkflow workflow(StatusView.StartableWorkflow w) {
        return new StartableWorkflow(w.workflow(), w.version(), w.title(), w.description(),
                w.runInputs().stream()
                        .map(i -> new RunInput(i.name(), i.description(), i.required(), i.partOfSubjectKey()))
                        .toList());
    }
}
