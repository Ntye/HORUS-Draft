package horus.domain.watchentity;

import horus.domain.shared.EntityVersionId;
import java.util.UUID;

// Provenance only, never matched against -- keep out of the scoring path (§6).
public record WatchExternalSource(UUID externalSourceId, EntityVersionId entityVersionId, String uri) {

    public WatchExternalSource {
        if (externalSourceId == null) {
            throw new IllegalArgumentException("externalSourceId must not be null");
        }
        if (entityVersionId == null) {
            throw new IllegalArgumentException("entityVersionId must not be null");
        }
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("uri must not be blank");
        }
    }
}
