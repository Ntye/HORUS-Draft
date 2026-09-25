package horus.domain.watchentity;

import horus.domain.shared.EntityId;

public record WatchEntity(EntityId entityId, String sourceId, String sourceEntityId) {

    public WatchEntity {
        if (entityId == null) {
            throw new IllegalArgumentException("entityId must not be null");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (sourceEntityId == null || sourceEntityId.isBlank()) {
            throw new IllegalArgumentException("sourceEntityId must not be blank");
        }
    }
}
