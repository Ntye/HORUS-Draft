package horus.ingestion.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.EntityType;
import horus.domain.watchentity.NameType;
import org.junit.jupiter.api.Test;

class CanonicalRecordBuilderTest {

    @Test
    void buildsARecordWithARequiredNameAndSourceId() {
        CanonicalRecord record = new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Ahmed Yousef", NameType.PRIMARY, "person/first_name+person/last_name")
                .build();

        assertThat(record.sourceId()).isEqualTo("worldcheck");
        assertThat(record.names()).hasSize(1);
        assertThat(record.has(CanonicalSlot.PRIMARY_NAME)).isTrue();
    }

    @Test
    void rejectsBuildingWithoutASourceId() {
        CanonicalRecordBuilder builder = new CanonicalRecordBuilder()
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Ahmed Yousef", NameType.PRIMARY, "person/last_name");

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsBuildingWithoutAtLeastOneName() {
        CanonicalRecordBuilder builder = new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i");

        assertThatThrownBy(builder::build).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void absentSlotsAreRecordedAndReflectedInHas() {
        CanonicalRecord record = new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Ahmed Yousef", NameType.PRIMARY, "person/last_name")
                .absent(CanonicalSlot.DATE_OF_BIRTH, "date_of_birth/day and month both xsi:nil")
                .build();

        assertThat(record.absentSlots()).contains(CanonicalSlot.DATE_OF_BIRTH);
        assertThat(record.has(CanonicalSlot.DATE_OF_BIRTH)).isFalse();
    }

    @Test
    void everyDeclaredFieldEndsUpOnTheBuiltRecord() {
        CanonicalRecord record = new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-9")
                .entityType(EntityType.ORGANISATION, "person@e-i")
                .name("Sahara Trading Ltd", NameType.PRIMARY, "person/last_name")
                .dob(new CanonicalDob(java.util.Optional.of(1975), java.util.Optional.empty(),
                        java.util.Optional.empty(), java.util.Optional.empty(),
                        java.util.Optional.empty(), java.util.Optional.empty(), "date_of_birth/year"))
                .country(new CanonicalCountry("AE", "countries/country"))
                .address(new CanonicalAddress("AE", "Dubai", null, "locations/location"))
                .identifier(new CanonicalIdentifier("PASSPORT", "P1234567", "identification/passport"))
                .build();

        assertThat(record.dobs()).hasSize(1);
        assertThat(record.countries()).hasSize(1);
        assertThat(record.addresses()).hasSize(1);
        assertThat(record.identifiers()).hasSize(1);
    }
}
