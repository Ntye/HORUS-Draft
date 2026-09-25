package horus.matching;

import horus.domain.shared.Provenance;
import java.util.Optional;

// One attribute's adjustment to the composite score (I-8: adjust, never eliminate), with the
// reason in words and, where the evidence came from narrative, its provenance (I-3).
// `capabilityGap` is set when the query supplied something the candidate record cannot be
// compared on.
public record AttributeEffect(
        String attribute,
        double delta,
        String reason,
        boolean forcesStrongMatch,
        Optional<Provenance> provenance,
        Optional<String> capabilityGap) {

    public AttributeEffect {
        if (attribute == null || attribute.isBlank()) {
            throw new IllegalArgumentException("attribute must not be blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (provenance == null || capabilityGap == null) {
            throw new IllegalArgumentException("provenance and capabilityGap must not be null");
        }
    }

    public static AttributeEffect of(String attribute, double delta, String reason) {
        return new AttributeEffect(attribute, delta, reason, false, Optional.empty(), Optional.empty());
    }

    public static AttributeEffect neutralWithGap(String attribute, String reason, String gap) {
        return new AttributeEffect(attribute, 0.0, reason, false, Optional.empty(), Optional.of(gap));
    }
}
