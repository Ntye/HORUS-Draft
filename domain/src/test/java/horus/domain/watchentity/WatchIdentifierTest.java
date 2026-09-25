package horus.domain.watchentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityVersionId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchIdentifierTest {

    @Test
    void acceptsAValidPassport() {
        WatchIdentifier identifier = new WatchIdentifier(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                IdType.PASSPORT,
                "P1234567",
                "p1234567",
                Optional.of("AE"),
                Optional.empty());

        assertThat(identifier.idType()).isEqualTo(IdType.PASSPORT);
    }

    @Test
    void rejectsBlankIdValue() {
        assertThatThrownBy(() -> new WatchIdentifier(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                IdType.PASSPORT,
                " ",
                " ",
                Optional.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
