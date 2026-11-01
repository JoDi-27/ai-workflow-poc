package aiworkflow.engine.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/** Health fails closed when the database goes away after start (spec 017 AC-6, constitution article 5). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.hikari.connection-timeout=2000")
@Testcontainers
class HealthDownIntegrationTest {

    // Its own container, since this test stops it.
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Value("${local.server.port}")
    int port;

    @Test
    void healthIsDownWhenTheDatabaseIsUnreachable() throws Exception {
        POSTGRES.stop();

        HttpResponse<String> response;
        try (var client = HttpClient.newHttpClient()) {
            response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).build(),
                    HttpResponse.BodyHandlers.ofString());
        }

        var health = JsonMapper.shared().readTree(response.body());
        assertEquals(503, response.statusCode());
        assertEquals("DOWN", health.path("status").asString(), response.body());
        assertEquals("DOWN", health.path("components").path("db").path("status").asString(), response.body());
    }
}
