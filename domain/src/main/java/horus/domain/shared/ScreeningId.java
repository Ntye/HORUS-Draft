package horus.domain.shared;

import java.util.UUID;

public record ScreeningId(UUID value) {

    public ScreeningId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }
}
