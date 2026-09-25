package horus.domain.screening;

import horus.domain.shared.DecisionBand;
import horus.domain.shared.QueryName;
import horus.domain.shared.ScreeningId;
import horus.domain.shared.ScreeningSubjectId;
import java.util.Optional;

public record ScreeningSubject(
        ScreeningSubjectId subjectId,
        ScreeningId screeningId,
        Optional<String> subjectReference,
        QueryName queryName,
        int candidateCount,
        int topScore,
        DecisionBand outcome) {

    public ScreeningSubject {
        if (subjectId == null) {
            throw new IllegalArgumentException("subjectId must not be null");
        }
        if (screeningId == null) {
            throw new IllegalArgumentException("screeningId must not be null");
        }
        if (subjectReference == null) {
            throw new IllegalArgumentException("subjectReference must not be null (use Optional.empty())");
        }
        if (queryName == null) {
            throw new IllegalArgumentException("queryName must not be null");
        }
        if (candidateCount < 0) {
            throw new IllegalArgumentException("candidateCount must not be negative");
        }
        if (topScore < 0 || topScore > 100) {
            throw new IllegalArgumentException("topScore must be between 0 and 100");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
    }
}
