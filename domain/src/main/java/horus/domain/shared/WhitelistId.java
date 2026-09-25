package horus.domain.shared;

import java.util.UUID;

public record WhitelistId(UUID value) {

    public WhitelistId {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
    }
}
