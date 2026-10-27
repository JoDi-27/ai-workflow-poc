package locks;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import locks.LocksApplication.Event;
import locks.LocksApplication.Events;
import locks.LocksApplication.StepPayload;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.hibernate.type.format.jackson.Jackson3JsonFormatMapper;
import tools.jackson.databind.json.JsonMapper;

/** Test 8: which FormatMapper handles jsonb, and a record round trip through Jackson 3. */
class JsonMapperTest {

    abstract static class Base extends TestBase {
        @Autowired EntityManagerFactory emf;
        @Autowired Events events;

        String mapper() {
            return emf.unwrap(SessionFactoryImplementor.class).getSessionFactoryOptions()
                    .getJsonFormatMapper().getClass().getName();
        }

        void roundTrip(String label) {
            var id = newRun("t8-" + label);
            var at = Instant.now().truncatedTo(ChronoUnit.MICROS);
            var payload = new StepPayload("review", 2, List.of("a", "b"), at);
            var eid = tx.execute(s -> events.save(new Event(id, 1, "step", payload)).id);
            var loaded = tx.execute(s -> events.findById(eid).orElseThrow().payload);
            var raw = jdbc.sql("select payload::text from event where id = ?").param(eid).query(String.class).single();
            log.info("T8 {}: FormatMapper = {}; stored jsonb = {}", label, mapper(), raw);
            assertThat(loaded).isEqualTo(payload);
        }
    }

    @Nested
    class Default extends Base {
        @Test
        void t8_defaultMapper() {
            roundTrip("default");
            assertThat(mapper()).isEqualTo("org.hibernate.type.format.jackson.Jackson3JsonFormatMapper");
        }
    }

    @Nested
    @SpringBootTest(properties = "spring.jpa.properties.hibernate.type.json_format_mapper=jackson3")
    class Explicit extends Base {
        @Test
        void t8b_explicitJackson3() {
            roundTrip("explicit jackson3");
            assertThat(mapper()).isEqualTo("org.hibernate.type.format.jackson.Jackson3JsonFormatMapper");
        }
    }

    /** Hands Hibernate the JsonMapper Spring Boot configured, so jsonb and HTTP share one Jackson setup. */
    @TestConfiguration(proxyBeanMethods = false)
    static class SpringMapper {
        @Bean
        HibernatePropertiesCustomizer jsonFormatMapper(JsonMapper jsonMapper) {
            return props -> props.put("hibernate.type.json_format_mapper", new Jackson3JsonFormatMapper(jsonMapper));
        }
    }

    @Nested
    @Import(SpringMapper.class)
    class SpringBootJsonMapper extends Base {
        @Test
        void t8c_springBootJsonMapper() {
            roundTrip("Spring Boot JsonMapper");
            assertThat(mapper()).isEqualTo("org.hibernate.type.format.jackson.Jackson3JsonFormatMapper");
        }
    }
}
