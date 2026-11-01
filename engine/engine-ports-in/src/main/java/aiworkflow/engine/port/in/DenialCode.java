package aiworkflow.engine.port.in;

/** The stable denial codes of the driver contract (docs/driver-api/README.md, Denials). */
public enum DenialCode {
    SESSION_UNKNOWN,
    RUN_NOT_FOUND,
    NOT_PERMITTED,
    ATTEMPT_OPEN,
    STALE_PRECONDITION,
    SUBJECT_BUSY,
    LEASE_HELD,
    ACTION_NOT_ALLOWED_IN_STAGE,
    REVISION_NOT_REVIEWED,
    STAGE_TASK_EXHAUSTED,
    MISSING_CAPABILITY,
    FORBIDDEN_SCOPE,
    INVALID_INPUTS,
    VALIDATION_FAILED,
    IDEMPOTENCY_CONFLICT
}
