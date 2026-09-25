package horus.application.port;

import horus.blocking.CandidateKey;
import horus.domain.shared.EntityVersionId;
import horus.matching.CandidateView;
import java.util.Collection;
import java.util.Map;

// The application layer's job (CLAUDE.md §5): turn blocked candidate keys into the CandidateViews
// the pure matching engine reads. A key with no entry in the result could not be assembled, and
// the caller must treat that as an error, never as "no candidate" (I-1).
public interface CandidateViewAssembler {

    Map<EntityVersionId, CandidateView> assemble(Collection<CandidateKey> keys);
}
