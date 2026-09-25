package horus.application.ingestion.testsupport;

import horus.application.port.ActiveEntitySnapshot;
import horus.application.port.ActiveEntityVersionLookup;
import horus.domain.watchentity.WatchEntityVersion;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class FakeActiveEntityVersionLookup implements ActiveEntityVersionLookup {

    private final Map<String, ActiveEntitySnapshot> snapshot = new LinkedHashMap<>();
    private final Map<String, WatchEntityVersion> fullVersions = new LinkedHashMap<>();

    public void putActive(String sourceEntityId, WatchEntityVersion version) {
        snapshot.put(sourceEntityId, new ActiveEntitySnapshot(version.entityId(), version.contentHash()));
        fullVersions.put(sourceEntityId, version);
    }

    @Override
    public Map<String, ActiveEntitySnapshot> loadActiveSnapshot(String sourceId) {
        return Map.copyOf(snapshot);
    }

    @Override
    public Optional<WatchEntityVersion> findActiveVersion(String sourceId, String sourceEntityId) {
        return Optional.ofNullable(fullVersions.get(sourceEntityId));
    }
}
