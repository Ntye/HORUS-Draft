package horus.ingestion.spi;

import static org.assertj.core.api.Assertions.assertThat;

import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.EntityType;
import horus.domain.watchentity.NameType;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CapabilityDeriverTest {

    private static CanonicalRecord withDob() {
        return new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Ahmed Yousef", NameType.PRIMARY, "person/last_name")
                .dob(new CanonicalDob(Optional.of(1975), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), "date_of_birth/year"))
                .build();
    }

    private static CanonicalRecord withoutDob() {
        return new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-2")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Sara Ali", NameType.PRIMARY, "person/last_name")
                .absent(CanonicalSlot.DATE_OF_BIRTH, "date_of_birth xsi:nil")
                .build();
    }

    @Test
    void derivesCoverageAsAFractionOfRecordsObserved() {
        CapabilityDeriver deriver = new CapabilityDeriver();

        deriver.observe(withDob());
        deriver.observe(withDob());
        deriver.observe(withoutDob());
        deriver.observe(withoutDob());

        var coverage = deriver.derive();

        assertThat(coverage.get(CanonicalSlot.DATE_OF_BIRTH)).isEqualTo(0.5);
        assertThat(deriver.recordsObserved()).isEqualTo(4);
    }

    @Test
    void everyRecordCarriesPrimaryNameSoItsCoverageIsAlwaysOne() {
        CapabilityDeriver deriver = new CapabilityDeriver();

        deriver.observe(withDob());
        deriver.observe(withoutDob());

        assertThat(deriver.derive().get(CanonicalSlot.PRIMARY_NAME)).isEqualTo(1.0);
    }

    @Test
    void withNoRecordsObservedCoverageIsEmpty() {
        CapabilityDeriver deriver = new CapabilityDeriver();

        assertThat(deriver.derive()).isEmpty();
        assertThat(deriver.recordsObserved()).isZero();
    }

    @Test
    void rejectsANullRecord() {
        CapabilityDeriver deriver = new CapabilityDeriver();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deriver.observe(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
