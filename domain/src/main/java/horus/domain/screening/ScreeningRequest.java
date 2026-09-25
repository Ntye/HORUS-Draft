package horus.domain.screening;

import horus.domain.shared.DecisionBand;
import horus.domain.shared.ListVersionId;
import horus.domain.shared.ScreeningId;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;

public record ScreeningRequest(
        ScreeningId screeningId,
        String consumerSystem,
        Optional<String> consumerReference,
        String profileId,
        String requestedBy,
        Instant requestedAt,
        Set<ListVersionId> listVersionIds,
        UUID configVersionId,
        String pipelineVersion,
        DecisionBand outcome,
        Optional<String> failureReason,
        String idempotencyKey) {

    public ScreeningRequest {
        if (screeningId == null) {
            throw new IllegalArgumentException("screeningId must not be null");
        }
        if (consumerSystem == null || consumerSystem.isBlank()) {
            throw new IllegalArgumentException("consumerSystem must not be blank");
        }
        if (consumerReference == null) {
            throw new IllegalArgumentException("consumerReference must not be null (use Optional.empty())");
        }
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId must not be blank");
        }
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new IllegalArgumentException("requestedBy must not be blank");
        }
        if (requestedAt == null) {
            throw new IllegalArgumentException("requestedAt must not be null");
        }
        // I-12: every result carries provenance -- at least one list version is mandatory.
        if (listVersionIds == null || listVersionIds.isEmpty()) {
            throw new IllegalArgumentException("listVersionIds must not be empty");
        }
        if (configVersionId == null) {
            throw new IllegalArgumentException("configVersionId must not be null");
        }
        if (pipelineVersion == null || pipelineVersion.isBlank()) {
            throw new IllegalArgumentException("pipelineVersion must not be blank");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        if (failureReason == null) {
            throw new IllegalArgumentException("failureReason must not be null (use Optional.empty())");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        // I-4: fixed iteration order, not whatever the caller's Set happened to produce.
        SortedSet<ListVersionId> sorted = new TreeSet<>(Comparator.comparing(ListVersionId::value));
        sorted.addAll(listVersionIds);
        listVersionIds = Collections.unmodifiableSortedSet(sorted);
    }
}
