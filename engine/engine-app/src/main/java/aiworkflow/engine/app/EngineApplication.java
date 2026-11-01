package aiworkflow.engine.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** The engine's one deployable; it wires every module by component scan (docs/engine/design.md section 1). */
@SpringBootApplication(scanBasePackages = "aiworkflow.engine")
public class EngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(EngineApplication.class, args);
    }
}
