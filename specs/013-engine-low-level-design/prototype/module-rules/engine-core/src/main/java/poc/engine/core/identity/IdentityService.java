package poc.engine.core.identity;

import org.springframework.stereotype.Service;

@Service
public class IdentityService {
    public Principal driver(String tokenHash) { return new Principal("driver-" + tokenHash); }

    public record Principal(String id) {}
}
