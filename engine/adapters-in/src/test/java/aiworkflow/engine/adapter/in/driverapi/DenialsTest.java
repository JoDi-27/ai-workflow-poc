package aiworkflow.engine.adapter.in.driverapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import aiworkflow.engine.port.in.Denial;
import aiworkflow.engine.port.in.DenialCode;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class DenialsTest {

    /** The table in docs/driver-api/README.md, Denials. */
    @ParameterizedTest
    @CsvSource({
        "SESSION_UNKNOWN, 404", "RUN_NOT_FOUND, 404",
        "NOT_PERMITTED, 409", "ATTEMPT_OPEN, 409", "STALE_PRECONDITION, 409", "SUBJECT_BUSY, 409", "LEASE_HELD, 409",
        "ACTION_NOT_ALLOWED_IN_STAGE, 409", "REVISION_NOT_REVIEWED, 409", "STAGE_TASK_EXHAUSTED, 409",
        "MISSING_CAPABILITY, 403", "FORBIDDEN_SCOPE, 403",
        "INVALID_INPUTS, 422", "VALIDATION_FAILED, 422", "IDEMPOTENCY_CONFLICT, 422"
    })
    void eachCodeMapsToTheContractStatus(DenialCode code, int status) {
        assertEquals(status, Denials.httpStatus(code).value());
    }

    @ParameterizedTest
    @EnumSource(DenialCode.class)
    void everyCodeBecomesAProblemWithItsSnakeCaseName(DenialCode code) {
        var problem = Denials.problem(new Denial(code, "m", "r", Map.of()));

        String name = code.name().toLowerCase();
        assertEquals("urn:ai-workflow:denial:" + name, problem.getType().toString());
        assertEquals(name, problem.getProperties().get("code"));
        assertEquals(Denials.httpStatus(code).value(), problem.getStatus());
    }
}
