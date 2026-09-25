package horus.normalisation;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

// Step 7 of 12 (§15.2): corporate legal-form normalisation. Folds spelling variants of a legal
// form to one canonical token; forms already in canonical shorthand (plc, llc, gmbh, sarl) pass
// through unchanged. The token stays in place -- down-weighting it happens at scoring time
// (Step 8's EntityTypeStrategy), not here.
public final class LegalFormNormalisationStep implements NormalisationStep {

    private static final Map<String, String> CANONICAL_FORMS = Map.of(
            "limited", "ltd",
            "incorporated", "inc",
            "corporation", "corp",
            "company", "co");

    @Override
    public String stepId() {
        return "legal-form-normalisation";
    }

    @Override
    public String apply(String input) {
        return Arrays.stream(input.split("\\s+"))
                .map(word -> CANONICAL_FORMS.getOrDefault(word, word))
                .collect(Collectors.joining(" "));
    }
}
