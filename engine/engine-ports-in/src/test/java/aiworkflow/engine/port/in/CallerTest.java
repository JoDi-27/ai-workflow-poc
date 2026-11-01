package aiworkflow.engine.port.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CallerTest {

    @Test
    void anonymousCallerHasNoCredential() {
        var caller = Caller.anonymous();

        assertNull(caller.credential());
        assertEquals(CredentialKind.NONE, caller.kind());
    }

    @Test
    void callerNeedsAKind() {
        assertThrows(NullPointerException.class, () -> new Caller("token", null));
    }

    @Test
    void statusQueryNeedsASession() {
        assertThrows(NullPointerException.class, () -> new StatusQuery(null));
        assertEquals("s1", new StatusQuery("s1").sessionId());
    }
}
