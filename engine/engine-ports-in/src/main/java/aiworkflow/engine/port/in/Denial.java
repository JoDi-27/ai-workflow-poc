package aiworkflow.engine.port.in;

import java.util.Map;
import java.util.Objects;

/** Why an operation was refused, and the generic next step (docs/driver-api/README.md, Denials). */
public record Denial(DenialCode code, String message, String recovery, Map<String, Object> details) {

    public Denial {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        details = details == null ? Map.of() : Map.copyOf(details);
    }
}
