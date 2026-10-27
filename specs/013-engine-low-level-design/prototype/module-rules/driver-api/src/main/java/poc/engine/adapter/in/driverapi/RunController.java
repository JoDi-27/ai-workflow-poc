package poc.engine.adapter.in.driverapi;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import poc.engine.ports.in.RunCommands;
import poc.engine.ports.in.RunView;
import poc.engine.ports.in.StartRun;

@RestController
public class RunController {
    private final RunCommands runs;

    public RunController(RunCommands runs) { this.runs = runs; }

    @PostMapping("/runs")
    public RunView start(@RequestBody StartRun command) { return runs.startRun(command); }
}
