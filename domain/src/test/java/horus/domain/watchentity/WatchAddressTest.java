package horus.domain.watchentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityVersionId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchAddressTest {

    @Test
    void acceptsAnAddressWithAtLeastOneField() {
        WatchAddress address = new WatchAddress(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                Optional.of("AE"),
                Optional.of("Dubai"),
                Optional.empty(),
                Optional.empty());

        assertThat(address.city()).contains("Dubai");
    }

    @Test
    void rejectsWhenEveryFieldIsAbsent() {
        assertThatThrownBy(() -> new WatchAddress(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
