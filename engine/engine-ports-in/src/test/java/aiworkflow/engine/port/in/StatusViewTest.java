package aiworkflow.engine.port.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatusViewTest {

    private static final StatusView.Next NONE = new StatusView.Next(StatusView.NextAction.NONE, "Nothing to do.");

    @Test
    void holdsAnUnboundSession() {
        var view = new StatusView("v1", new StatusView.Session("s1", null), List.of(), NONE);

        assertEquals("v1", view.contractVersion());
        assertEquals("s1", view.session().id());
        assertNull(view.session().boundRunId());
        assertTrue(view.startableWorkflows().isEmpty());
        assertEquals(StatusView.NextAction.NONE, view.next().action());
        assertEquals("Nothing to do.", view.next().hint());
    }

    @Test
    void copiesStartableWorkflowsSoLaterChangesDoNotLeakIn() {
        var input = new StatusView.RunInput("topic", "What the run is about", true, true);
        var workflow = new StatusView.StartableWorkflow("article", 2, "Article", "Write one article", List.of(input));
        var workflows = new ArrayList<>(List.of(workflow));

        var view = new StatusView("v1", new StatusView.Session("s1", null), workflows, NONE);
        workflows.clear();

        assertEquals(List.of(workflow), view.startableWorkflows());
        assertThrows(UnsupportedOperationException.class, () -> view.startableWorkflows().clear());
        assertEquals("topic", workflow.runInputs().getFirst().name());
        assertEquals(2, workflow.version());
        assertTrue(workflow.runInputs().getFirst().partOfSubjectKey());
    }

    @Test
    void copiesRunInputs() {
        var inputs = new ArrayList<StatusView.RunInput>();
        var workflow = new StatusView.StartableWorkflow("article", 1, "Article", "Write one article", inputs);
        inputs.add(new StatusView.RunInput("topic", "d", true, false));

        assertTrue(workflow.runInputs().isEmpty());
    }

    @Test
    void refusesMissingRequiredParts() {
        var session = new StatusView.Session("s1", null);
        assertThrows(NullPointerException.class, () -> new StatusView(null, session, List.of(), NONE));
        assertThrows(NullPointerException.class, () -> new StatusView("v1", null, List.of(), NONE));
        assertThrows(NullPointerException.class, () -> new StatusView("v1", session, null, NONE));
        assertThrows(NullPointerException.class, () -> new StatusView("v1", session, List.of(), null));
        assertThrows(NullPointerException.class, () -> new StatusView.Session(null, null));
        assertThrows(NullPointerException.class, () -> new StatusView.Next(null, "h"));
    }
}
