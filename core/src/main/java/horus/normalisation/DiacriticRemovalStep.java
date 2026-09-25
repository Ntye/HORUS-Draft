package horus.normalisation;

import java.util.regex.Pattern;

// Step 2 of 12 (§15.2): strips the combining marks left behind by NFKD decomposition (step 1).
public final class DiacriticRemovalStep implements NormalisationStep {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    @Override
    public String stepId() {
        return "diacritic-removal";
    }

    @Override
    public String apply(String input) {
        return COMBINING_MARKS.matcher(input).replaceAll("");
    }
}
