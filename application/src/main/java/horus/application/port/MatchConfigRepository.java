package horus.application.port;

import java.time.Instant;
import java.util.Optional;

// I-7: thresholds and weights live in versioned, immutable config rows. A screening records the
// configVersionId it used (I-12). Only an APPROVED version, effective at `asOf`, is ever returned:
// Compliance owns the operating point (spec §16.4).
public interface MatchConfigRepository {

    Optional<VersionedMatchConfig> findCurrent(String profileId, Instant asOf);

    void save(VersionedMatchConfig config, String createdBy, Instant createdAt, String approvedBy,
            Instant effectiveFrom);
}
