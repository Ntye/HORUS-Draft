package horus.normalisation;

import java.util.ArrayList;
import java.util.List;

// Step 12 of 12 (§15.2): n-gram generation -- character trigrams for indexing (pg_trgm's GIN
// index, Step 7). A plain sliding window, not a reimplementation of pg_trgm's exact
// word-padding algorithm -- sufficient for candidate generation.
public final class TrigramGenerator {

    private static final int WINDOW_SIZE = 3;

    public List<String> generate(String input) {
        if (input.length() < WINDOW_SIZE) {
            return List.of();
        }
        List<String> trigrams = new ArrayList<>();
        for (int i = 0; i <= input.length() - WINDOW_SIZE; i++) {
            trigrams.add(input.substring(i, i + WINDOW_SIZE));
        }
        return trigrams;
    }
}
