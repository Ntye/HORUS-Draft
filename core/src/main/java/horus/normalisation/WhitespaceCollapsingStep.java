package horus.normalisation;

import java.util.regex.Pattern;

// Step 5 of 12 (§15.2): whitespace collapsing and trimming.
public final class WhitespaceCollapsingStep implements NormalisationStep {

    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    @Override
    public String stepId() {
        return "whitespace-collapsing";
    }

    @Override
    public String apply(String input) {
        return WHITESPACE_RUN.matcher(input.strip()).replaceAll(" ");
    }
}
