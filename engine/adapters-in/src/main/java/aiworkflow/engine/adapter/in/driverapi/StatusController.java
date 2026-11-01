package aiworkflow.engine.adapter.in.driverapi;

import aiworkflow.engine.port.in.Caller;
import aiworkflow.engine.port.in.CredentialKind;
import aiworkflow.engine.port.in.Denial;
import aiworkflow.engine.port.in.DenialCode;
import aiworkflow.engine.port.in.DriverOperations;
import aiworkflow.engine.port.in.Outcome;
import aiworkflow.engine.port.in.StatusQuery;
import aiworkflow.engine.port.in.StatusView;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /v1/status} of the driver API (docs/driver-api/README.md). No authorization logic: the core authenticates. */
@RestController
class StatusController {

    private static final String BEARER = "Bearer ";

    private final DriverOperations driverOperations;

    StatusController(DriverOperations driverOperations) {
        this.driverOperations = driverOperations;
    }

    @GetMapping("/v1/status")
    ResponseEntity<?> status(
            @RequestParam(name = "session_id", required = false) String sessionId,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        if (sessionId == null || sessionId.isBlank()) {
            return problem(new Denial(DenialCode.INVALID_INPUTS, "The session_id query parameter is missing.",
                    "Call status again with session_id set to the session's ID.", Map.of("parameter", "session_id")));
        }
        Outcome<StatusView> outcome = driverOperations.status(caller(authorization), new StatusQuery(sessionId));
        return switch (outcome) {
            case Outcome.Ok<StatusView> ok -> ResponseEntity.ok(StatusResponse.from(ok.value()));
            case Outcome.Denied<StatusView> denied -> problem(denied.denial());
        };
    }

    private static Caller caller(String authorization) {
        if (authorization != null && authorization.startsWith(BEARER)) {
            return new Caller(authorization.substring(BEARER.length()), CredentialKind.DRIVER_TOKEN);
        }
        return Caller.anonymous();
    }

    private static ResponseEntity<?> problem(Denial denial) {
        var problem = Denials.problem(denial);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}
