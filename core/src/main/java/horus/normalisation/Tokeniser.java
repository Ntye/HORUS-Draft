package horus.normalisation;

import java.util.Arrays;
import java.util.List;

// Step 9 of 12 (§15.2): tokenisation.
public final class Tokeniser {

    public List<String> tokenise(String input) {
        String trimmed = input.strip();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(trimmed.split("\\s+")).toList();
    }
}
