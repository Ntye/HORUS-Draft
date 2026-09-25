package horus.domain.shared;

import java.util.UUID;

public record ScreeningCandidateId(UUID value) {

    public ScreeningCandidateId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }
}
