package poc.engine.core.definitions;

import org.springframework.stereotype.Service;

@Service
public class DefinitionService {
    public PinnedVersion pin(String workflowKey) { return new PinnedVersion(workflowKey, 1); }

    public record PinnedVersion(String workflowKey, int version) {}
}
