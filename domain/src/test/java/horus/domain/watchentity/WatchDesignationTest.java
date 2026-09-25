package horus.domain.watchentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityVersionId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchDesignationTest {

    @Test
    void isRemovalIsTrueOnlyForRemovalAction() {
        WatchDesignation removal = new WatchDesignation(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                "OFAC_SDN",
                ActionType.REMOVAL,
                Optional.of(2026),
                "removed from the SDN list",
                Optional.empty());
        WatchDesignation addition = new WatchDesignation(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                "OFAC_SDN",
                ActionType.ADDITION,
                Optional.of(2020),
                "added to the SDN list",
                Optional.empty());

        assertThat(removal.isRemoval()).isTrue();
        assertThat(addition.isRemoval()).isFalse();
    }

    @Test
    void rejectsBlankListSource() {
        assertThatThrownBy(() -> new WatchDesignation(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                " ",
                ActionType.ADDITION,
                Optional.empty(),
                "verb",
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
