package horus.domain.watchentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityVersionId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchCountryTest {

    @Test
    void acceptsAValidCountry() {
        WatchCountry country = new WatchCountry(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                "United Arab Emirates",
                Optional.of("AE"));

        assertThat(country.isoCode()).contains("AE");
    }

    @Test
    void rejectsBlankRawCountry() {
        assertThatThrownBy(() -> new WatchCountry(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                " ",
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
