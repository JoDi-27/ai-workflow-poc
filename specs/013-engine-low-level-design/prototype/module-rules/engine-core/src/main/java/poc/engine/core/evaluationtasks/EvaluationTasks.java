package poc.engine.core.evaluationtasks;

import org.springframework.stereotype.Service;
import poc.engine.ports.out.runner.RunnerPort;

@Service
public class EvaluationTasks {
    private final RunnerPort runner;

    public EvaluationTasks(RunnerPort runner) { this.runner = runner; }

    public String dispatch(String spec) { return runner.dispatch(spec); }
}
