package horus.matching;

import horus.domain.shared.Provenance;
import java.util.Optional;

// I-3: a narrative-derived identifier carries the rule and span it came from.
public record CandidateIdentifier(String value, Optional<Provenance> provenance) {

    public CandidateIdentifier {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("provenance must not be null (use Optional.empty())");
        }
    }
}
