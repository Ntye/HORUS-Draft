package horus.normalisation;

import java.util.regex.Pattern;

// Step 4 of 12 (§15.2): punctuation and separator handling.
//
// Deliberately excludes "&" from what counts as punctuation here: step 8 (connector-word
// handling) spells it out as "and", and it can only do that if this step leaves it alone.
public final class PunctuationHandlingStep implements NormalisationStep {

    private static final Pattern NON_ALNUM_SPACE_AMPERSAND = Pattern.compile("[^\\p{Alnum}\\s&]");

    @Override
    public String stepId() {
        return "punctuation-handling";
    }

    @Override
    public String apply(String input) {
        return NON_ALNUM_SPACE_AMPERSAND.matcher(input).replaceAll(" ");
    }
}
