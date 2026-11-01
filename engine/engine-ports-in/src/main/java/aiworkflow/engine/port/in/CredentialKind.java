package aiworkflow.engine.port.in;

/** What kind of credential a caller presented. */
public enum CredentialKind {
    /** No credential: the stub operations accept it until the Driver API item adds authentication (spec 017 Q3). */
    NONE,
    DRIVER_TOKEN,
    UI_SESSION
}
