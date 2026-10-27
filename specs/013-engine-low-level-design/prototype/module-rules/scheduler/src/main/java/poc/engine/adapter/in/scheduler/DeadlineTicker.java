package poc.engine.adapter.in.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import poc.engine.ports.in.RunCommands;
import poc.engine.ports.in.StartRun;

@Component
public class DeadlineTicker {
    private final RunCommands runs;

    public DeadlineTicker(RunCommands runs) { this.runs = runs; }

    @Scheduled(fixedDelay = 60_000)
    public void tick() { runs.startRun(new StartRun("tick", "tick")); }
}
