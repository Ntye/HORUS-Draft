package horus.domain.watchentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityVersionId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchDobTest {

    @Test
    void acceptsAFullDate() {
        WatchDob dob = new WatchDob(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                Optional.of(1971),
                Optional.of(4),
                Optional.of(12),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                DobPrecision.FULL_DATE);

        assertThat(dob.year()).contains(1971);
    }

    @Test
    void acceptsAgeOnly() {
        WatchDob dob = new WatchDob(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(54),
                Optional.empty(),
                Optional.empty(),
                DobPrecision.AGE_ONLY);

        assertThat(dob.age()).contains(54);
    }

    @Test
    void rejectsWhenNeitherYearNorAgeIsPresent() {
        assertThatThrownBy(() -> new WatchDob(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                DobPrecision.FULL_DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
