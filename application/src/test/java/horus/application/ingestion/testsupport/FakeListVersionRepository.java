package horus.application.ingestion.testsupport;

import horus.application.port.ListVersionRepository;
import horus.domain.listversion.ListVersion;
import horus.domain.listversion.LoadStatus;
import horus.domain.listversion.SourceCapabilities;
import horus.domain.shared.ListVersionId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class FakeListVersionRepository implements ListVersionRepository {

    private final Map<ListVersionId, ListVersion> byId = new LinkedHashMap<>();

    @Override
    public void save(ListVersion listVersion) {
        byId.put(listVersion.listVersionId(), listVersion);
    }

    @Override
    public void updateStatus(ListVersionId listVersionId, LoadStatus newStatus) {
        ListVersion current = byId.get(listVersionId);
        if (current == null) {
            throw new IllegalStateException("no such list version: " + listVersionId);
        }
        byId.put(listVersionId, current.withStatus(newStatus));
    }

    @Override
    public void updateMeasuredTotals(ListVersionId listVersionId, long recordCount, SourceCapabilities capabilities) {
        ListVersion current = byId.get(listVersionId);
        if (current == null) {
            throw new IllegalStateException("no such list version: " + listVersionId);
        }
        byId.put(listVersionId, current.withRecordCount(recordCount).withCapabilities(capabilities));
    }

    @Override
    public Optional<ListVersion> findById(ListVersionId listVersionId) {
        return Optional.ofNullable(byId.get(listVersionId));
    }

    @Override
    public Optional<ListVersion> findActive(String sourceId) {
        return byId.values().stream()
                .filter(v -> v.sourceId().equals(sourceId) && v.status() == LoadStatus.ACTIVE)
                .findFirst();
    }

    @Override
    public Optional<ListVersion> findMostRecentSuperseded(String sourceId) {
        return byId.values().stream()
                .filter(v -> v.sourceId().equals(sourceId) && v.status() == LoadStatus.SUPERSEDED)
                .max((a, b) -> a.loadedAt().compareTo(b.loadedAt()));
    }

    public Map<ListVersionId, ListVersion> all() {
        return Map.copyOf(byId);
    }
}
