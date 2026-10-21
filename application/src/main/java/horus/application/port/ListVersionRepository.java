package horus.application.port;

import horus.domain.listversion.ListVersion;
import horus.domain.listversion.LoadStatus;
import horus.domain.listversion.ReviewState;
import horus.domain.listversion.SourceCapabilities;
import horus.domain.shared.ListVersionId;
import java.util.Optional;

public interface ListVersionRepository {

    // Inserted once, as STAGED, with recordCount = 0 and capabilities empty -- both are only
    // knowable once the file has fully streamed through (see updateMeasuredTotals).
    void save(ListVersion listVersion);

    // I-6-adjacent for loads: the only columns horus_ingest may ever update on a list_version
    // row are status, capabilities and record_count (V3, V4) -- never source identity or checksum.
    void updateStatus(ListVersionId listVersionId, LoadStatus newStatus);

    // The only other column horus_ingest may update (V8's column-level grant). Set to PENDING
    // when an unattended load waived a capability deviation and promoted anyway, and to
    // ACKNOWLEDGED when a human has signed that waiver off.
    void updateReviewState(ListVersionId listVersionId, ReviewState newReviewState);

    void updateMeasuredTotals(ListVersionId listVersionId, long recordCount, SourceCapabilities capabilities);

    Optional<ListVersion> findById(ListVersionId listVersionId);

    Optional<ListVersion> findActive(String sourceId);

    // The version to roll back TO: the most recently loaded SUPERSEDED version for the source.
    Optional<ListVersion> findMostRecentSuperseded(String sourceId);
}
