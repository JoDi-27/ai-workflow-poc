package poc.engine.core.checkpoints;

import org.springframework.stereotype.Service;
import poc.engine.core.definitions.DefinitionService.PinnedVersion;
import poc.engine.core.evaluationtasks.EvaluationTasks;

@Service
public class CheckpointService {
    private final EvaluationTasks tasks;

    public CheckpointService(EvaluationTasks tasks) { this.tasks = tasks; }

    public String open(PinnedVersion version) { return tasks.dispatch(version.workflowKey()); }
}
