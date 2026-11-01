package aiworkflow.engine.core.usecases;

import aiworkflow.engine.port.in.Caller;
import aiworkflow.engine.port.in.DriverOperations;
import aiworkflow.engine.port.in.Outcome;
import aiworkflow.engine.port.in.StatusQuery;
import aiworkflow.engine.port.in.StatusView;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Implements {@link DriverOperations}. A stub until the engine core and the Driver API items: every
 * session is unbound and no workflow can be started (spec 017).
 */
@Service
public class DriverOperationsService implements DriverOperations {

    static final String CONTRACT_VERSION = "v1";

    @Override
    public Outcome<StatusView> status(Caller caller, StatusQuery query) {
        // No authentication yet: the Driver API item authenticates the caller here (spec 017 Q3).
        return new Outcome.Ok<>(new StatusView(
                CONTRACT_VERSION,
                new StatusView.Session(query.sessionId(), null),
                List.of(),
                new StatusView.Next(StatusView.NextAction.NONE, "No workflow can be started yet.")));
    }
}
