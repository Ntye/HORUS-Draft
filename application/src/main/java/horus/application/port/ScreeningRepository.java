package horus.application.port;

import horus.domain.shared.ScreeningId;
import java.util.Optional;

// Append-only evidence: save and read, never update or delete.
public interface ScreeningRepository {

    void save(ScreeningRecord record);

    Optional<ScreeningRecord> findById(ScreeningId screeningId);

    Optional<ScreeningId> findIdByIdempotencyKey(String idempotencyKey);
}
