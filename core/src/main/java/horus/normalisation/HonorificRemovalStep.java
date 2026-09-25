package horus.normalisation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Step 6 of 12 (§15.2): titles and honorifics removal. Matches whole leading/trailing words
// only -- "drake" must never be treated as "dr" + "ake". Not an exhaustive vocabulary; closed
// and extendable.
public final class HonorificRemovalStep implements NormalisationStep {

    private static final Set<String> PREFIX_HONORIFICS =
            Set.of("dr", "mr", "mrs", "ms", "miss", "prof", "sheikh", "sheikha", "sayyid", "hajji");

    private static final Set<String> SUFFIX_HONORIFICS =
            Set.of("bey", "pasha", "jr", "sr", "ii", "iii");

    @Override
    public String stepId() {
        return "honorific-removal";
    }

    @Override
    public String apply(String input) {
        List<String> words = new ArrayList<>(List.of(input.split("\\s+")));
        words.removeIf(String::isBlank);

        if (!words.isEmpty() && PREFIX_HONORIFICS.contains(words.get(0))) {
            words.remove(0);
        }
        if (!words.isEmpty() && SUFFIX_HONORIFICS.contains(words.get(words.size() - 1))) {
            words.remove(words.size() - 1);
        }

        return String.join(" ", words);
    }
}
