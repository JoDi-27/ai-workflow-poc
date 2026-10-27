package poc.engine.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "poc.engine")
public class EngineApplication {
    public static void main(String[] args) { SpringApplication.run(EngineApplication.class, args); }
}
