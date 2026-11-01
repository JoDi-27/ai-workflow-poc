package aiworkflow.engine.port.in;

import java.util.Objects;

/** The result of an inbound operation: a value, or a denial. A broken business rule is a denial, never an exception. */
public sealed interface Outcome<T> permits Outcome.Ok, Outcome.Denied {

    record Ok<T>(T value) implements Outcome<T> {}

    record Denied<T>(Denial denial) implements Outcome<T> {
        public Denied {
            Objects.requireNonNull(denial, "denial");
        }
    }
}
