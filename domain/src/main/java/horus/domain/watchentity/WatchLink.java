package horus.domain.watchentity;

import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import java.util.Optional;
import java.util.UUID;

// 13.07M edges, 2.14M targets, 100% resolution -- but no role or percentage on the feed itself.
public record WatchLink(
        UUID linkId,
        EntityVersionId entityVersionId,
        EntityId targetEntityId,
        Optional<String> role,
        Optional<Double> percent) {

    public WatchLink {
        if (linkId == null) {
            throw new IllegalArgumentException("linkId must not be null");
        }
        if (entityVersionId == null) {
            throw new IllegalArgumentException("entityVersionId must not be null");
        }
        if (targetEntityId == null) {
            throw new IllegalArgumentException("targetEntityId must not be null");
        }
        if (role == null) {
            throw new IllegalArgumentException("role must not be null (use Optional.empty())");
        }
        if (percent == null) {
            throw new IllegalArgumentException("percent must not be null (use Optional.empty())");
        }
    }
}
