package horus.domain.shared;

import java.util.UUID;

public record ListVersionId(UUID value) {

    public ListVersionId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }
}
