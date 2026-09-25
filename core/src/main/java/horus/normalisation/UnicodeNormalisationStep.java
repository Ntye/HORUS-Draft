package horus.normalisation;

import java.text.Normalizer;
import java.util.Locale;

// Step 1 of 12 (§15.2): Unicode normalisation (NFKD) and case folding.
public final class UnicodeNormalisationStep implements NormalisationStep {

    @Override
    public String stepId() {
        return "unicode-normalisation";
    }

    @Override
    public String apply(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
    }
}
