package aiworkflow.engine.port.in;

import java.util.Objects;

/** Asks for the status of one driver session. */
public record StatusQuery(String sessionId) {

    public StatusQuery {
        Objects.requireNonNull(sessionId, "sessionId");
    }
}
