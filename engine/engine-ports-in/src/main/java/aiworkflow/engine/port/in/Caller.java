package aiworkflow.engine.port.in;

import java.util.Objects;

/** The raw credential an adapter received; the core authenticates it, adapters never do (docs/engine/design.md section 2). */
public record Caller(String credential, CredentialKind kind) {

    public Caller {
        Objects.requireNonNull(kind, "kind");
    }

    public static Caller anonymous() {
        return new Caller(null, CredentialKind.NONE);
    }
}
