package horus.application.port;

import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.ListVersionId;
import java.util.Set;

// An ACTIVE list version as screening sees it: its identity (I-12 provenance) and the canonical
// pipeline version its names were normalised with (I-10), and the canonical
// slots the source actually populated, which decide what a configuration may ask of it (I-7).
public record ActiveListVersion(
        ListVersionId listVersionId, String sourceId, String pipelineVersion, Set<CanonicalSlot> capabilities) {

    public ActiveListVersion {
        if (listVersionId == null) {
            throw new IllegalArgumentException("listVersionId must not be null");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (pipelineVersion == null || pipelineVersion.isBlank()) {
            throw new IllegalArgumentException("pipelineVersion must not be blank");
        }
        if (capabilities == null) {
            throw new IllegalArgumentException("capabilities must not be null");
        }
        capabilities = Set.copyOf(capabilities);
    }
}
