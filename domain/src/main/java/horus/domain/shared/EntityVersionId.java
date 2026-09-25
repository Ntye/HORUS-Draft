package horus.domain.shared;

import java.util.UUID;

public record EntityVersionId(UUID value) {

    public EntityVersionId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }
}
