package horus.application.port;

import horus.domain.shared.EntityId;
import java.util.Optional;

// Labelled corpora name the expected entity by the source's own identifier (a human can write
// that down); the system's stable EntityId is resolved from it.
public interface EntityIdResolver {

    Optional<EntityId> resolve(String sourceId, String sourceEntityId);
}
