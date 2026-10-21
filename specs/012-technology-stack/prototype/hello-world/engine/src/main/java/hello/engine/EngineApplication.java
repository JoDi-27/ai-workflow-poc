package hello.engine;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Stub engine: the HTTP driver API's status call and a token check, nothing else. */
@SpringBootApplication
@RestController
public class EngineApplication {

    @Value("${engine.driver-token:dev-token}")
    String driverToken;

    public static void main(String[] args) {
        SpringApplication.run(EngineApplication.class, args);
    }

    @GetMapping("/v1/whoami")
    Map<String, Object> whoami(HttpServletRequest req) {
        authenticate(req);
        return Map.of("user", "owner", "scopes", "driver");
    }

    @GetMapping("/v1/status")
    Map<String, Object> status(HttpServletRequest req, @RequestParam String subject) {
        authenticate(req);
        return Map.of("subject", subject, "run", "none", "message", "hello from the stub engine",
                "at", Instant.now().toString());
    }

    void authenticate(HttpServletRequest req) {
        if (!("Bearer " + driverToken).equals(req.getHeader("Authorization"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad or missing driver token");
        }
    }
}
