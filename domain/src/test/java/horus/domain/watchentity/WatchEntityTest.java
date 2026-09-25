package horus.domain.watchentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchEntityTest {

    @Test
    void acceptsAValidIdentity() {
        EntityId entityId = new EntityId(UUID.randomUUID());

        WatchEntity entity = new WatchEntity(entityId, "worldcheck", "src-001");

        assertThat(entity.entityId()).isEqualTo(entityId);
        assertThat(entity.sourceId()).isEqualTo("worldcheck");
        assertThat(entity.sourceEntityId()).isEqualTo("src-001");
    }

    @Test
    void rejectsNullEntityId() {
        assertThatThrownBy(() -> new WatchEntity(null, "worldcheck", "src-001"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankSourceId() {
        EntityId entityId = new EntityId(UUID.randomUUID());

        assertThatThrownBy(() -> new WatchEntity(entityId, " ", "src-001"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankSourceEntityId() {
        EntityId entityId = new EntityId(UUID.randomUUID());

        assertThatThrownBy(() -> new WatchEntity(entityId, "worldcheck", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
