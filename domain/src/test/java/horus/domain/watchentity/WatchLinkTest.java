package horus.domain.watchentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchLinkTest {

    @Test
    void acceptsALinkWithoutRoleOrPercent() {
        WatchLink link = new WatchLink(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                new EntityId(UUID.randomUUID()),
                Optional.empty(),
                Optional.empty());

        assertThat(link.role()).isEmpty();
        assertThat(link.percent()).isEmpty();
    }

    @Test
    void rejectsNullTargetEntityId() {
        assertThatThrownBy(() -> new WatchLink(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                null,
                Optional.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
