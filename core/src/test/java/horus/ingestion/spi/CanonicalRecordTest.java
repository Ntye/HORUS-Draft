package horus.ingestion.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.EntityType;
import horus.domain.watchentity.NameType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CanonicalRecordTest {

    @Test
    void hasReflectsAbsentSlots() {
        CanonicalRecord record = new CanonicalRecord(
                "worldcheck",
                "wc-1",
                EntityType.INDIVIDUAL,
                Optional.empty(),
                List.of(new CanonicalName("Ahmed Yousef", NameType.PRIMARY, "person/first_name+person/last_name")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Set.of(CanonicalSlot.DATE_OF_BIRTH),
                Map.of(CanonicalSlot.PRIMARY_NAME, "person/first_name+person/last_name"));

        assertThat(record.has(CanonicalSlot.PRIMARY_NAME)).isTrue();
        assertThat(record.has(CanonicalSlot.DATE_OF_BIRTH)).isFalse();
    }

    @Test
    void rejectsBlankSourceId() {
        assertThatThrownBy(() -> new CanonicalRecord(
                        " ", "wc-1", EntityType.INDIVIDUAL, Optional.empty(),
                        List.of(new CanonicalName("X", NameType.PRIMARY, "p")),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        Set.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
