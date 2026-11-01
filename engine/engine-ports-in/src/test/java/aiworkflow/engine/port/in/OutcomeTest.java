package aiworkflow.engine.port.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OutcomeTest {

    @Test
    void okCarriesItsValue() {
        Outcome<String> outcome = new Outcome.Ok<>("value");

        String seen = switch (outcome) {
            case Outcome.Ok<String> ok -> ok.value();
            case Outcome.Denied<String> denied -> denied.denial().message();
        };

        assertEquals("value", seen);
    }

    @Test
    void deniedCarriesItsDenial() {
        var denial = new Denial(DenialCode.SESSION_UNKNOWN, "Session s1 is not registered.", "Register, then retry.",
                Map.of("session_id", "s1"));
        Outcome<String> outcome = new Outcome.Denied<>(denial);

        Denial seen = switch (outcome) {
            case Outcome.Ok<String> ok -> null;
            case Outcome.Denied<String> denied -> denied.denial();
        };

        assertEquals(DenialCode.SESSION_UNKNOWN, seen.code());
        assertEquals("Register, then retry.", seen.recovery());
        assertEquals("s1", seen.details().get("session_id"));
    }

    @Test
    void deniedNeedsADenial() {
        assertThrows(NullPointerException.class, () -> new Outcome.Denied<>(null));
    }

    @Test
    void denialNeedsACodeAndAMessage() {
        assertThrows(NullPointerException.class, () -> new Denial(null, "m", "r", null));
        assertThrows(NullPointerException.class, () -> new Denial(DenialCode.RUN_NOT_FOUND, null, "r", null));
    }

    @Test
    void denialWithoutDetailsHasAnEmptyMap() {
        assertEquals(Map.of(), new Denial(DenialCode.RUN_NOT_FOUND, "m", "r", null).details());
    }

    @Test
    void thereAreFifteenDenialCodes() {
        assertEquals(15, DenialCode.values().length);
    }
}
