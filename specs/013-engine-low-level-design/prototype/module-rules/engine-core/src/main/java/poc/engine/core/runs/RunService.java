package poc.engine.core.runs;

import org.springframework.stereotype.Service;
import poc.engine.core.checkpoints.CheckpointService;
import poc.engine.core.definitions.DefinitionService;
import poc.engine.core.outbox.Outbox;
import poc.engine.core.scoring.Scorer;
import poc.engine.ports.in.RunCommands;
import poc.engine.ports.in.RunView;
import poc.engine.ports.in.StartRun;

@Service
public class RunService implements RunCommands {
    private final DefinitionService definitions;
    private final CheckpointService checkpoints;
    private final Scorer scorer;
    private final Outbox outbox;

    public RunService(DefinitionService definitions, CheckpointService checkpoints, Scorer scorer, Outbox outbox) {
        this.definitions = definitions;
        this.checkpoints = checkpoints;
        this.scorer = scorer;
        this.outbox = outbox;
    }

    @Override
    public RunView startRun(StartRun command) {
        var version = definitions.pin(command.workflowKey());
        checkpoints.open(version);
        var run = new RunEntity(command.subjectKey(), "active:" + scorer.score(0, 0));
        outbox.relay(run.id());
        return new RunView(run.id(), run.state());
    }
}
