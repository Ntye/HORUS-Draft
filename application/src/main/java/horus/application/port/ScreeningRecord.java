package horus.application.port;

import horus.domain.screening.ScreeningCandidate;
import horus.domain.screening.ScreeningRequest;
import horus.domain.screening.ScreeningSubject;
import java.util.List;

// Everything one screening persists, saved in a single transaction so evidence is never half
// written. Explanations are stored whole and reloaded as stored -- reconstruction never
// recomputes (CLAUDE.md §5, I-3).
public record ScreeningRecord(ScreeningRequest request, List<SubjectRecord> subjects) {

    public record SubjectRecord(ScreeningSubject subject, List<ScreeningCandidate> candidates) {

        public SubjectRecord {
            if (subject == null || candidates == null) {
                throw new IllegalArgumentException("subject and candidates must not be null");
            }
            candidates = List.copyOf(candidates);
        }
    }

    public ScreeningRecord {
        if (request == null || subjects == null || subjects.isEmpty()) {
            throw new IllegalArgumentException("a screening needs a request and at least one subject");
        }
        subjects = List.copyOf(subjects);
    }
}
