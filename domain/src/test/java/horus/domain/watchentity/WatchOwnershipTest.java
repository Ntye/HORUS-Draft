package horus.domain.watchentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityVersionId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchOwnershipTest {

    @Test
    void acceptsAValidStake() {
        WatchOwnership stake = new WatchOwnership(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                "Jane Doe",
                Optional.of("IOS"),
                Optional.of(51.0),
                true,
                Optional.empty());

        assertThat(stake.percent()).contains(51.0);
    }

    @Test
    void rejectsBlankOwner() {
        assertThatThrownBy(() -> new WatchOwnership(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                " ",
                Optional.empty(),
                Optional.empty(),
                false,
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPercentOutOfRange() {
        assertThatThrownBy(() -> new WatchOwnership(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                "Jane Doe",
                Optional.empty(),
                Optional.of(150.0),
                true,
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
