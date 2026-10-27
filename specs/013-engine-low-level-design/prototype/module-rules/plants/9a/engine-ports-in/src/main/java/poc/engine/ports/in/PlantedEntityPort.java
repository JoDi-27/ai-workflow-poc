package poc.engine.ports.in;

import poc.engine.core.runs.RunEntity;

public interface PlantedEntityPort {
    RunEntity load(String runId);
}
