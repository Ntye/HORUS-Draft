package horus.application.ingestion;

import horus.application.port.ActiveEntitySnapshot;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

// I-6: a WatchEntityVersion is only ever created when contentHash changes; an entity absent
// from the new load becomes a tombstone, never a delete.
public final class ReconcileVersion {

    public enum Decision {
        ADD,
        AMEND,
        UNCHANGED
    }

    public Decision decide(Optional<ActiveEntitySnapshot> existing, String newContentHash) {
        if (existing == null) {
            throw new IllegalArgumentException("existing must not be null (use Optional.empty())");
        }
        if (newContentHash == null || newContentHash.isBlank()) {
            throw new IllegalArgumentException("newContentHash must not be blank");
        }
        if (existing.isEmpty()) {
            return Decision.ADD;
        }
        // I-6: a tombstoned entity present again in the feed gets a new ACTIVE version, whatever
        // its hash -- otherwise a re-listed entity would stay delisted.
        if (existing.get().delisted()) {
            return Decision.AMEND;
        }
        return existing.get().contentHash().equals(newContentHash) ? Decision.UNCHANGED : Decision.AMEND;
    }

    // I-4: a TreeSet keeps delisting order deterministic regardless of map iteration order.
    public Set<String> delistedKeys(Map<String, ActiveEntitySnapshot> activeSnapshot, Set<String> seenSourceEntityIds) {
        if (activeSnapshot == null) {
            throw new IllegalArgumentException("activeSnapshot must not be null");
        }
        if (seenSourceEntityIds == null) {
            throw new IllegalArgumentException("seenSourceEntityIds must not be null");
        }
        Set<String> delisted = new TreeSet<>();
        activeSnapshot.forEach((key, snapshot) -> {
            // An entity already tombstoned is not tombstoned again on every subsequent load.
            if (!snapshot.delisted()) {
                delisted.add(key);
            }
        });
        delisted.removeAll(seenSourceEntityIds);
        return delisted;
    }
}
