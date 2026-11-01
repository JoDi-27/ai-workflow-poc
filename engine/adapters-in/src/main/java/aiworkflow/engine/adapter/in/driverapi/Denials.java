package aiworkflow.engine.adapter.in.driverapi;

import aiworkflow.engine.port.in.Denial;
import aiworkflow.engine.port.in.DenialCode;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Maps a denial to its HTTP status and RFC 9457 problem body (docs/driver-api/README.md, Denials). */
final class Denials {

    private Denials() {}

    /** Exhaustive, with no default, so a new code is a compile error here (docs/engine/design.md section 8). */
    static HttpStatus httpStatus(DenialCode code) {
        return switch (code) {
            case SESSION_UNKNOWN, RUN_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case NOT_PERMITTED, ATTEMPT_OPEN, STALE_PRECONDITION, SUBJECT_BUSY, LEASE_HELD,
                    ACTION_NOT_ALLOWED_IN_STAGE, REVISION_NOT_REVIEWED, STAGE_TASK_EXHAUSTED -> HttpStatus.CONFLICT;
            case MISSING_CAPABILITY, FORBIDDEN_SCOPE -> HttpStatus.FORBIDDEN;
            case INVALID_INPUTS, VALIDATION_FAILED, IDEMPOTENCY_CONFLICT -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
    }

    static ProblemDetail problem(Denial denial) {
        String code = denial.code().name().toLowerCase();
        var problem = ProblemDetail.forStatusAndDetail(httpStatus(denial.code()), denial.message());
        problem.setType(URI.create("urn:ai-workflow:denial:" + code));
        problem.setProperty("code", code);
        problem.setProperty("message", denial.message());
        problem.setProperty("recovery", denial.recovery());
        problem.setProperty("details", denial.details());
        return problem;
    }
}
