package horus.application.ingestion.testsupport;

import horus.application.port.WatchEntityRepository;
import horus.domain.shared.EntityId;
import horus.domain.watchentity.WatchEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FakeWatchEntityRepository implements WatchEntityRepository {

    private final List<WatchEntity> saved = new ArrayList<>();

    // Mirrors the real uniqueness rule -- unique on (sourceId, sourceEntityId), CLAUDE.md §5 -- so
    // a second load reaching the same source key gets the FIRST id back, as Postgres would. A fake
    // that simply appended would have let D12 H-9 through: the collision only exists because the
    // row survives the load that wrote it.
    @Override
    public Map<String, EntityId> ensureAll(List<WatchEntity> candidates) {
        Map<String, EntityId> resolved = new LinkedHashMap<>();
        for (WatchEntity candidate : candidates) {
            resolved.put(candidate.sourceEntityId(), saved.stream()
                    .filter(e -> e.sourceId().equals(candidate.sourceId())
                            && e.sourceEntityId().equals(candidate.sourceEntityId()))
                    .findFirst()
                    .map(WatchEntity::entityId)
                    .orElseGet(() -> {
                        saved.add(candidate);
                        return candidate.entityId();
                    }));
        }
        return resolved;
    }

    public List<WatchEntity> saved() {
        return List.copyOf(saved);
    }
}
