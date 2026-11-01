package aiworkflow.engine.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A second start on the same database applies nothing again (spec 017 AC-4). */
@Testcontainers
class MigrationRestartIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private ConfigurableApplicationContext start() {
        // Command-line arguments, which override application.yaml (properties() would only set defaults).
        return new SpringApplicationBuilder(EngineApplication.class).run(
                "--server.port=0",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword());
    }

    private List<Map<String, Object>> history() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()));
        return jdbc.queryForList("select installed_rank, version, installed_on from engine.flyway_schema_history order by 1");
    }

    @Test
    void aSecondStartAppliesNothing() {
        try (var first = start()) {
            // started once
        }
        var afterFirst = history();
        try (var second = start()) {
            // started again
        }

        assertEquals(afterFirst, history());
        assertEquals("1", afterFirst.getLast().get("version"));
    }
}
