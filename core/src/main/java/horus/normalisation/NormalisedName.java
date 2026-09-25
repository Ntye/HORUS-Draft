package horus.normalisation;

import java.util.List;

public record NormalisedName(
        String raw,
        String normalised,
        List<String> tokens,
        List<String> sortedTokens,
        List<String> phoneticCodes,
        List<String> trigrams,
        String script) {

    public NormalisedName {
        if (raw == null) {
            throw new IllegalArgumentException("raw must not be null");
        }
        if (normalised == null) {
            throw new IllegalArgumentException("normalised must not be null");
        }
        if (tokens == null || sortedTokens == null || phoneticCodes == null || trigrams == null) {
            throw new IllegalArgumentException(
                    "tokens, sortedTokens, phoneticCodes and trigrams must not be null");
        }
        if (script == null) {
            throw new IllegalArgumentException("script must not be null");
        }
        tokens = List.copyOf(tokens);
        sortedTokens = List.copyOf(sortedTokens);
        phoneticCodes = List.copyOf(phoneticCodes);
        trigrams = List.copyOf(trigrams);
    }
}
