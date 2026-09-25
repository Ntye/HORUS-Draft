package horus.application.port;

import horus.domain.watchentity.WatchEntityVersion;
import java.util.Map;
import java.util.Optional;

// Bulk-fetched once at the start of a load, not queried per record -- see CLAUDE.md §5/§6
// discussion in the Step 5 plan: for the real 5.99M-record feed this snapshot itself is a real
// memory cost (bounded to active-entity-count identifiers and hashes, not full records), a
// known and deliberately deferred scaling concern for the Day 5 full load.
public interface ActiveEntityVersionLookup {

    Map<String, ActiveEntitySnapshot> loadActiveSnapshot(String sourceId);

    // Used only for the (typically much smaller) delisted subset at the end of a load, to get
    // the full previous version WatchEntityVersion.tombstone() needs to carry identity forward.
    Optional<WatchEntityVersion> findActiveVersion(String sourceId, String sourceEntityId);
}
