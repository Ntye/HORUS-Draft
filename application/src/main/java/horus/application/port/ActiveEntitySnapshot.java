package horus.application.port;

import horus.domain.shared.EntityId;

// One row of a source's current entities, keyed by the source's natural key (sourceEntityId) so a
// streaming load can decide ADD / AMEND / UNCHANGED / DELIST per record without a database round
// trip per record. "Current" spans every non-failed load up to the active one, because an
// unchanged entity keeps the version row written by the load that last changed it (I-6).
// `delisted` marks a tombstone: it carries the previous contentHash, so the flag -- not the
// hash -- is what tells reconciliation the entity is not presently designated.
public record ActiveEntitySnapshot(EntityId entityId, String contentHash, boolean delisted) {

    public ActiveEntitySnapshot {
        if (entityId == null) {
            throw new IllegalArgumentException("entityId must not be null");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
    }

    public ActiveEntitySnapshot(EntityId entityId, String contentHash) {
        this(entityId, contentHash, false);
    }
}
