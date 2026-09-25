package horus.domain.screening;

import horus.domain.shared.DecisionBand;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.ScreeningCandidateId;
import horus.domain.shared.ScreeningSubjectId;

// The whole explanation is stored, versioned by schema -- reconstruction must not recompute (I-3).
public record ScreeningCandidate(
        ScreeningCandidateId candidateId,
        ScreeningSubjectId subjectId,
        String sourceId,
        EntityId entityId,
        EntityVersionId entityVersionId,
        int compositeScore,
        String explanationJson,
        String explanationSchemaVersion,
        DecisionBand decisionBand,
        int rank) {

    public ScreeningCandidate {
        if (candidateId == null) {
            throw new IllegalArgumentException("candidateId must not be null");
        }
        if (subjectId == null) {
            throw new IllegalArgumentException("subjectId must not be null");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (entityId == null) {
            throw new IllegalArgumentException("entityId must not be null");
        }
        if (entityVersionId == null) {
            throw new IllegalArgumentException("entityVersionId must not be null");
        }
        if (compositeScore < 0 || compositeScore > 100) {
            throw new IllegalArgumentException("compositeScore must be between 0 and 100");
        }
        if (explanationJson == null || explanationJson.isBlank()) {
            throw new IllegalArgumentException("explanationJson must not be blank");
        }
        if (explanationSchemaVersion == null || explanationSchemaVersion.isBlank()) {
            throw new IllegalArgumentException("explanationSchemaVersion must not be blank");
        }
        if (decisionBand == null) {
            throw new IllegalArgumentException("decisionBand must not be null");
        }
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be at least 1");
        }
    }
}
