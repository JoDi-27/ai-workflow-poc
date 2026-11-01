package aiworkflow.engine.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** The running engine on PostgreSQL 18: migrations, health, and the stub status over real HTTP (spec 017). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class EngineIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Value("${local.server.port}")
    int port;

    @Autowired
    DataSource dataSource;

    private HttpResponse<String> get(String path) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void runsOnPostgres18() {
        String version = new JdbcTemplate(dataSource).queryForObject("show server_version", String.class);
        assertTrue(version.startsWith("18."), version);
    }

    @Test
    void appliesTheFirstMigrationToTheEngineSchema() {
        var jdbc = new JdbcTemplate(dataSource);
        Integer applied = jdbc.queryForObject(
                "select count(*) from engine.flyway_schema_history where version = '1' and success", Integer.class);
        String comment = jdbc.queryForObject("select obj_description('engine'::regnamespace)", String.class);

        assertEquals(1, applied);
        assertTrue(comment.startsWith("The engine's state"), comment);
    }

    @Test
    void healthIsUpWithTheDatabaseUp() throws Exception {
        var response = get("/actuator/health");

        var health = JsonMapper.shared().readTree(response.body());
        assertEquals(200, response.statusCode());
        assertEquals("UP", health.path("status").asString(), response.body());
        assertEquals("UP", health.path("components").path("db").path("status").asString(), response.body());
    }

    @Test
    void statusAnswersAnUnboundSessionOverHttp() throws Exception {
        var response = get("/v1/status?session_id=s1");

        assertEquals(200, response.statusCode());
        String body = response.body();
        for (String part : new String[] {"\"contract_version\":\"v1\"", "\"session\":{\"id\":\"s1\",\"bound_run_id\":null}",
                "\"startable_workflows\":[]", "\"run\":null", "\"next\":{\"action\":\"none\""}) {
            assertTrue(body.contains(part), part + " in " + body);
        }
    }

    @Test
    void statusWithoutASessionIsAProblem() throws Exception {
        var response = get("/v1/status");

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("\"code\":\"invalid_inputs\""), response.body());
    }
}
