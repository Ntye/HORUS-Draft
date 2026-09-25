package horus.ingestion.spi;

import horus.domain.shared.Provenance;
import horus.domain.watchentity.ActionType;

// Narrative-derived (§6: no status/de-listing flag exists in the structured record), so this
// carries real offset-based Provenance rather than a source-path string.
public record CanonicalDesignation(
        String listSource, ActionType action, String rawVerb, int year, Provenance provenance) {

    public CanonicalDesignation {
        if (listSource == null || listSource.isBlank()) {
            throw new IllegalArgumentException("listSource must not be blank");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        if (rawVerb == null || rawVerb.isBlank()) {
            throw new IllegalArgumentException("rawVerb must not be blank");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("provenance must not be null");
        }
    }
}
