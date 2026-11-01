package aiworkflow.engine.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The SDLC, git, and Claude Code words the core and ports may not use in names (docs/engine/design.md
 * section 1, Q15). Matches whole camel-case words, not substrings, so {@code digit} and {@code emerge} pass.
 */
final class BannedNames {

    static final Set<String> WORDS = Set.of("git", "claude", "sdlc", "merge", "pullrequest");

    private BannedNames() {}

    /** Splits a Java identifier or a dotted name into lower-case words: {@code SDLCStage} is {@code sdlc, stage}. */
    static List<String> words(String name) {
        List<String> words = new ArrayList<>();
        for (String part : name.split("[.$_\\d]+")) {
            for (String word : part.split("(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")) {
                if (!word.isEmpty()) {
                    words.add(word.toLowerCase(Locale.ROOT));
                }
            }
        }
        return words;
    }

    /** The first banned word in the name, also across two words ({@code pullRequest}), or null. */
    static String bannedWordIn(String name) {
        List<String> words = words(name);
        for (int i = 0; i < words.size(); i++) {
            if (WORDS.contains(words.get(i))) {
                return words.get(i);
            }
            if (i + 1 < words.size() && WORDS.contains(words.get(i) + words.get(i + 1))) {
                return words.get(i) + words.get(i + 1);
            }
        }
        return null;
    }
}
