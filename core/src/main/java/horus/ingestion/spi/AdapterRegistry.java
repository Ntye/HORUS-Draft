package horus.ingestion.spi;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AdapterRegistry {

    private final Map<String, WatchlistSourceAdapter> adapters = new LinkedHashMap<>();

    public void register(WatchlistSourceAdapter adapter) {
        if (adapter == null) {
            throw new IllegalArgumentException("adapter must not be null");
        }
        if (adapters.containsKey(adapter.sourceId())) {
            throw new IllegalStateException("an adapter is already registered for sourceId "
                    + adapter.sourceId());
        }
        adapters.put(adapter.sourceId(), adapter);
    }

    public WatchlistSourceAdapter resolve(String sourceId) {
        WatchlistSourceAdapter adapter = adapters.get(sourceId);
        if (adapter == null) {
            throw new IllegalArgumentException("no adapter registered for sourceId " + sourceId);
        }
        return adapter;
    }
}
