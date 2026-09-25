package horus.domain.shared;

import java.util.UUID;

public record ScreeningSubjectId(UUID value) {

    public ScreeningSubjectId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }
}
