package horus.domain.shared;

import java.util.UUID;

public record EntityId(UUID value) {

    public EntityId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }
}
