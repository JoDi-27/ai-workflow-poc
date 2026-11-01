package aiworkflow.engine.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BannedNamesTest {

    @ParameterizedTest
    @ValueSource(strings = {"digit", "legitimate", "emerge", "Digitizer", "emergency", "submergedTotal", "pull", "request",
        "pullOrder", "aiworkflow.engine.core.runs.RunService"})
    void namesThatOnlyContainABannedWordPass(String name) {
        assertNull(BannedNames.bannedWordIn(name));
    }

    @ParameterizedTest
    @CsvSource({
        "GitRevision, git", "authorizeMerge, merge", "pullRequestUrl, pullrequest", "SDLCStage, sdlc",
        "aiworkflow.engine.core.claude.HookBridge, claude", "aiworkflow.engine.core.pullrequest.Thing, pullrequest",
        "MERGE_WINDOW, merge", "Outer$GitInner, git", "lambda$merge$0, merge"
    })
    void namesWithABannedWordFail(String name, String word) {
        assertEquals(word, BannedNames.bannedWordIn(name));
    }

    @Test
    void splitsCamelCaseAndAcronyms() {
        assertEquals(List.of("sdlc", "stage", "view"), BannedNames.words("SDLCStageView"));
        assertEquals(List.of("authorize", "merge"), BannedNames.words("authorizeMerge"));
    }
}
