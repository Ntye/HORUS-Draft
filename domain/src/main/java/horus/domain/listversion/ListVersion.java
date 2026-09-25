package horus.domain.listversion;

import horus.domain.shared.ListVersionId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// Identifies a LOAD, not a file.
public record ListVersion(
        ListVersionId listVersionId,
        String sourceId,
        String formatId,
        Optional<UUID> mappingId,
        String sourceFileName,
        String sourceFileChecksum,
        Optional<Instant> vendorPublishedAt,
        Instant loadedAt,
        long recordCount,
        LoadStatus status,
        String loadedBy,
        Optional<SourceCapabilities> capabilities,
        String pipelineVersion,
        Optional<String> indexBinding) {

    public ListVersion {
        if (listVersionId == null) {
            throw new IllegalArgumentException("listVersionId must not be null");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (formatId == null || formatId.isBlank()) {
            throw new IllegalArgumentException("formatId must not be blank");
        }
        if (mappingId == null) {
            throw new IllegalArgumentException("mappingId must not be null (use Optional.empty())");
        }
        if (sourceFileName == null || sourceFileName.isBlank()) {
            throw new IllegalArgumentException("sourceFileName must not be blank");
        }
        if (sourceFileChecksum == null || sourceFileChecksum.isBlank()) {
            throw new IllegalArgumentException("sourceFileChecksum must not be blank");
        }
        if (vendorPublishedAt == null) {
            throw new IllegalArgumentException("vendorPublishedAt must not be null (use Optional.empty())");
        }
        if (loadedAt == null) {
            throw new IllegalArgumentException("loadedAt must not be null");
        }
        if (recordCount < 0) {
            throw new IllegalArgumentException("recordCount must not be negative");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (loadedBy == null || loadedBy.isBlank()) {
            throw new IllegalArgumentException("loadedBy must not be blank");
        }
        if (capabilities == null) {
            throw new IllegalArgumentException("capabilities must not be null (use Optional.empty())");
        }
        // I-12: every screening traces back through a list version to the pipeline that normalised it.
        if (pipelineVersion == null || pipelineVersion.isBlank()) {
            throw new IllegalArgumentException("pipelineVersion must not be blank");
        }
        if (indexBinding == null) {
            throw new IllegalArgumentException("indexBinding must not be null (use Optional.empty())");
        }
    }

    public ListVersion withStatus(LoadStatus newStatus) {
        return new ListVersion(listVersionId, sourceId, formatId, mappingId, sourceFileName,
                sourceFileChecksum, vendorPublishedAt, loadedAt, recordCount, newStatus, loadedBy,
                capabilities, pipelineVersion, indexBinding);
    }

    public ListVersion withCapabilities(SourceCapabilities newCapabilities) {
        return new ListVersion(listVersionId, sourceId, formatId, mappingId, sourceFileName,
                sourceFileChecksum, vendorPublishedAt, loadedAt, recordCount, status, loadedBy,
                Optional.ofNullable(newCapabilities), pipelineVersion, indexBinding);
    }

    // record_count starts as a 0 placeholder at STAGE and is corrected once streaming finishes
    // and the true count is known (same reasoning as capabilities: unknowable before the file
    // has been read in full).
    public ListVersion withRecordCount(long newRecordCount) {
        return new ListVersion(listVersionId, sourceId, formatId, mappingId, sourceFileName,
                sourceFileChecksum, vendorPublishedAt, loadedAt, newRecordCount, status, loadedBy,
                capabilities, pipelineVersion, indexBinding);
    }

    public boolean isActive() {
        return status == LoadStatus.ACTIVE;
    }
}
