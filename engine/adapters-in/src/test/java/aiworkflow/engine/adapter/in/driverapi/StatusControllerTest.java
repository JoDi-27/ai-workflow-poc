package aiworkflow.engine.adapter.in.driverapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import aiworkflow.engine.port.in.Caller;
import aiworkflow.engine.port.in.CredentialKind;
import aiworkflow.engine.port.in.Denial;
import aiworkflow.engine.port.in.DenialCode;
import aiworkflow.engine.port.in.DriverOperations;
import aiworkflow.engine.port.in.Outcome;
import aiworkflow.engine.port.in.StatusView;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StatusControllerTest {

    private final AtomicReference<Caller> seenCaller = new AtomicReference<>();

    private MockMvc mvc(DriverOperations port) {
        return MockMvcBuilders.standaloneSetup(new StatusController(port)).build();
    }

    private DriverOperations unbound() {
        return (caller, query) -> {
            seenCaller.set(caller);
            var input = new StatusView.RunInput("topic", "What it is about", true, true);
            return new Outcome.Ok<>(new StatusView("v1", new StatusView.Session(query.sessionId(), null),
                    List.of(new StatusView.StartableWorkflow("article", 3, "Article", "Write one", List.of(input))),
                    new StatusView.Next(StatusView.NextAction.START_RUN, "Start a run.")));
        };
    }

    @Test
    void statusWritesEveryTopLevelFieldOfTheContract() throws Exception {
        mvc(unbound()).perform(get("/v1/status").param("session_id", "s1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.contract_version").value("v1"))
                .andExpect(jsonPath("$.session.id").value("s1"))
                .andExpect(jsonPath("$.session.bound_run_id").isEmpty())
                .andExpect(jsonPath("$.startable_workflows[0].workflow").value("article"))
                .andExpect(jsonPath("$.startable_workflows[0].version").value(3))
                .andExpect(jsonPath("$.startable_workflows[0].title").value("Article"))
                .andExpect(jsonPath("$.startable_workflows[0].description").value("Write one"))
                .andExpect(jsonPath("$.startable_workflows[0].run_inputs[0].name").value("topic"))
                .andExpect(jsonPath("$.startable_workflows[0].run_inputs[0].description").value("What it is about"))
                .andExpect(jsonPath("$.startable_workflows[0].run_inputs[0].required").value(true))
                .andExpect(jsonPath("$.startable_workflows[0].run_inputs[0].part_of_subject_key").value(true))
                .andExpect(jsonPath("$.run").isEmpty())
                .andExpect(jsonPath("$.stage").isEmpty())
                .andExpect(jsonPath("$.permitted_transitions").isArray())
                .andExpect(jsonPath("$.permitted_transitions").isEmpty())
                .andExpect(jsonPath("$.protected_actions").isEmpty())
                .andExpect(jsonPath("$.open_attempt").isEmpty())
                .andExpect(jsonPath("$.last_decision").isEmpty())
                .andExpect(jsonPath("$.pending.human_steps").isEmpty())
                .andExpect(jsonPath("$.pending.escalations").isEmpty())
                .andExpect(jsonPath("$.pending.evaluation_tasks").isEmpty())
                .andExpect(jsonPath("$.recommendations").isEmpty())
                .andExpect(jsonPath("$.next.action").value("start_run"))
                .andExpect(jsonPath("$.next.hint").value("Start a run."));
    }

    @Test
    void nullFieldsAreWrittenNotDropped() throws Exception {
        var body = mvc(unbound()).perform(get("/v1/status").param("session_id", "s1"))
                .andReturn().getResponse().getContentAsString();
        for (String field : List.of("\"run\":null", "\"stage\":null", "\"open_attempt\":null", "\"last_decision\":null",
                "\"bound_run_id\":null")) {
            assertEquals(true, body.contains(field), field + " in " + body);
        }
    }

    @Test
    void aBearerTokenReachesThePortAsADriverToken() throws Exception {
        mvc(unbound()).perform(get("/v1/status").param("session_id", "s1").header("Authorization", "Bearer abc"))
                .andExpect(status().isOk());

        assertEquals(new Caller("abc", CredentialKind.DRIVER_TOKEN), seenCaller.get());
    }

    @Test
    void noAuthorizationHeaderIsAnAnonymousCaller() throws Exception {
        mvc(unbound()).perform(get("/v1/status").param("session_id", "s1")).andExpect(status().isOk());

        assertEquals(Caller.anonymous(), seenCaller.get());
    }

    @Test
    void anAuthorizationHeaderThatIsNotBearerIsAnonymous() throws Exception {
        mvc(unbound()).perform(get("/v1/status").param("session_id", "s1").header("Authorization", "Basic xyz"))
                .andExpect(status().isOk());

        assertEquals(Caller.anonymous(), seenCaller.get());
    }

    @Test
    void missingSessionIdIsInvalidInputs() throws Exception {
        mvc(unbound()).perform(get("/v1/status"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("invalid_inputs"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.recovery").isNotEmpty());
    }

    @Test
    void blankSessionIdIsInvalidInputs() throws Exception {
        mvc(unbound()).perform(get("/v1/status").param("session_id", " "))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("invalid_inputs"));
    }

    @Test
    void aDenialFromThePortIsAProblemBody() throws Exception {
        DriverOperations denying = (caller, query) -> new Outcome.Denied<>(new Denial(DenialCode.SESSION_UNKNOWN,
                "Session s1 is not registered.", "Register the session, then retry.", Map.of("session_id", "s1")));

        mvc(denying).perform(get("/v1/status").param("session_id", "s1"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:ai-workflow:denial:session_unknown"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("session_unknown"))
                .andExpect(jsonPath("$.message").value("Session s1 is not registered."))
                .andExpect(jsonPath("$.recovery").value("Register the session, then retry."))
                .andExpect(jsonPath("$.details.session_id").value("s1"));
    }
}
