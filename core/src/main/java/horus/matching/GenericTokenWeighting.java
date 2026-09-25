package horus.matching;

import java.util.Set;

public record GenericTokenWeighting(Set<String> genericTokens, double genericWeight) implements TokenWeighting {

    public GenericTokenWeighting {
        if (genericTokens == null) {
            throw new IllegalArgumentException("genericTokens must not be null");
        }
        if (!(genericWeight > 0.0 && genericWeight <= 1.0)) {
            throw new IllegalArgumentException("genericWeight must be in (0, 1]");
        }
        genericTokens = Set.copyOf(genericTokens);
    }

    @Override
    public double weight(String token) {
        return isGeneric(token) ? genericWeight : 1.0;
    }

    @Override
    public boolean isGeneric(String token) {
        return genericTokens.contains(token);
    }
}
