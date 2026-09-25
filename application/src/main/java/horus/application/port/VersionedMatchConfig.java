package horus.application.port;

import horus.matching.MatchConfig;
import java.util.UUID;

public record VersionedMatchConfig(UUID configVersionId, String profileId, MatchConfig config) {

    public VersionedMatchConfig {
        if (configVersionId == null) {
            throw new IllegalArgumentException("configVersionId must not be null");
        }
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId must not be blank");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
    }
}
