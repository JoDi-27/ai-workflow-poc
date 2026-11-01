package aiworkflow.engine.core.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import aiworkflow.engine.port.in.Caller;
import aiworkflow.engine.port.in.CredentialKind;
import aiworkflow.engine.port.in.Outcome;
import aiworkflow.engine.port.in.StatusQuery;
import aiworkflow.engine.port.in.StatusView;
import org.junit.jupiter.api.Test;

class DriverOperationsServiceTest {

    private final DriverOperationsService service = new DriverOperationsService();

    @Test
    void statusOfAnUnboundSessionHasNoRunAndNothingToStart() {
        var outcome = service.status(Caller.anonymous(), new StatusQuery("s1"));

        var view = assertInstanceOf(Outcome.Ok.class, outcome).value();
        var status = assertInstanceOf(StatusView.class, view);
        assertEquals("v1", status.contractVersion());
        assertEquals("s1", status.session().id());
        assertNull(status.session().boundRunId());
        assertTrue(status.startableWorkflows().isEmpty());
        assertEquals(StatusView.NextAction.NONE, status.next().action());
        assertFalse(status.next().hint().isBlank());
    }

    @Test
    void statusEchoesTheAskedSession() {
        var outcome = service.status(new Caller("token", CredentialKind.DRIVER_TOKEN), new StatusQuery("other"));

        var status = (StatusView) ((Outcome.Ok<?>) outcome).value();
        assertEquals("other", status.session().id());
    }
}
