package horus.blocking;

import java.util.EnumSet;
import java.util.Set;

public record IndexCapabilities(Set<Strategy> supported) {

    public IndexCapabilities {
        if (supported == null || supported.isEmpty()) {
            throw new IllegalArgumentException("an index must support at least one strategy");
        }
        supported = Set.copyOf(EnumSet.copyOf(supported));
    }

    public boolean supports(Strategy strategy) {
        return supported.contains(strategy);
    }
}
