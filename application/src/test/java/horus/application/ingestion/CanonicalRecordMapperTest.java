package horus.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import horus.application.port.IdGenerator;
import horus.application.port.WatchEntityVersionAggregate;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityType;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.ListVersionId;
import horus.domain.watchentity.ActionType;
import horus.domain.watchentity.DobPrecision;
import horus.domain.watchentity.EntityStatus;
import horus.domain.watchentity.IdType;
import horus.domain.watchentity.NameType;
import horus.domain.shared.Provenance;
import horus.ingestion.spi.CanonicalCountry;
import horus.ingestion.spi.CanonicalDesignation;
import horus.ingestion.spi.CanonicalDob;
import horus.ingestion.spi.CanonicalIdentifier;
import horus.ingestion.spi.CanonicalRecord;
import horus.ingestion.spi.CanonicalRecordBuilder;
import horus.normalisation.NormalisationPipeline;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CanonicalRecordMapperTest {

    private static final NormalisationPipeline PIPELINE = NormalisationPipeline.standard();

    private static IdGenerator sequentialIds() {
        AtomicLong counter = new AtomicLong();
        return () -> new UUID(0, counter.incrementAndGet());
    }

    @Test
    void mapsPrimaryNameThroughTheSharedNormalisationPipeline() {
        CanonicalRecord record = new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Mohammed Al-Sayed", NameType.PRIMARY, "person/last_name")
                .build();

        CanonicalRecordMapper mapper = new CanonicalRecordMapper(PIPELINE, sequentialIds());
        EntityVersionId entityVersionId = new EntityVersionId(UUID.randomUUID());
        WatchEntityVersionAggregate aggregate = mapper.toAggregate(
                record, new EntityId(UUID.randomUUID()), entityVersionId,
                new ListVersionId(UUID.randomUUID()), "sha256:test");

        assertThat(aggregate.version().primaryName()).isEqualTo("Mohammed Al-Sayed");
        assertThat(aggregate.version().status()).isEqualTo(EntityStatus.ACTIVE);
        assertThat(aggregate.names()).hasSize(1);
        assertThat(aggregate.names().get(0).normalisedName())
                .isEqualTo(PIPELINE.normalise("Mohammed Al-Sayed").normalised());
        assertThat(aggregate.names().get(0).entityVersionId()).isEqualTo(entityVersionId);
    }

    @Test
    void derivesFullDatePrecisionWhenYearMonthAndDayArePresent() {
        CanonicalRecord record = new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Ahmed Yousef", NameType.PRIMARY, "person/last_name")
                .dob(new CanonicalDob(Optional.of(1975), Optional.of(3), Optional.of(4), Optional.empty(),
                        Optional.empty(), Optional.empty(), "date_of_birth"))
                .build();

        WatchEntityVersionAggregate aggregate = new CanonicalRecordMapper(PIPELINE, sequentialIds())
                .toAggregate(record, new EntityId(UUID.randomUUID()), new EntityVersionId(UUID.randomUUID()),
                        new ListVersionId(UUID.randomUUID()), "sha256:test");

        assertThat(aggregate.dobs()).hasSize(1);
        assertThat(aggregate.dobs().get(0).precision()).isEqualTo(DobPrecision.FULL_DATE);
    }

    @Test
    void derivesAgeOnlyPrecisionWhenOnlyAgeIsPresent() {
        CanonicalRecord record = new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Ahmed Yousef", NameType.PRIMARY, "person/last_name")
                .dob(new CanonicalDob(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(50),
                        Optional.empty(), Optional.empty(), "date_of_birth"))
                .build();

        WatchEntityVersionAggregate aggregate = new CanonicalRecordMapper(PIPELINE, sequentialIds())
                .toAggregate(record, new EntityId(UUID.randomUUID()), new EntityVersionId(UUID.randomUUID()),
                        new ListVersionId(UUID.randomUUID()), "sha256:test");

        assertThat(aggregate.dobs().get(0).precision()).isEqualTo(DobPrecision.AGE_ONLY);
    }

    @Test
    void mapsAStructuredPassportIdentifierByType() {
        CanonicalRecord record = new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Ahmed Yousef", NameType.PRIMARY, "person/last_name")
                .identifier(new CanonicalIdentifier("PASSPORT", " p1234567 ", "identification/passport"))
                .build();

        WatchEntityVersionAggregate aggregate = new CanonicalRecordMapper(PIPELINE, sequentialIds())
                .toAggregate(record, new EntityId(UUID.randomUUID()), new EntityVersionId(UUID.randomUUID()),
                        new ListVersionId(UUID.randomUUID()), "sha256:test");

        assertThat(aggregate.identifiers()).hasSize(1);
        assertThat(aggregate.identifiers().get(0).idType()).isEqualTo(IdType.PASSPORT);
        assertThat(aggregate.identifiers().get(0).normalisedIdValue()).isEqualTo("P1234567");
    }

    // Narrative-derived identifier types outside the structured IdType vocabulary (§6) must
    // demote to OTHER, never be dropped or throw (I-8: attributes adjust, never eliminate).
    @Test
    void mapsAnUnrecognisedIdentifierTypeToOther() {
        CanonicalRecord record = new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Ahmed Yousef", NameType.PRIMARY, "person/last_name")
                .identifier(new CanonicalIdentifier("GROUP_ID", "G-1", "details/further_information"))
                .build();

        WatchEntityVersionAggregate aggregate = new CanonicalRecordMapper(PIPELINE, sequentialIds())
                .toAggregate(record, new EntityId(UUID.randomUUID()), new EntityVersionId(UUID.randomUUID()),
                        new ListVersionId(UUID.randomUUID()), "sha256:test");

        assertThat(aggregate.identifiers().get(0).idType()).isEqualTo(IdType.OTHER);
    }

    @Test
    void collectsDistinctSortedListSourcesFromDesignations() {
        CanonicalRecord record = new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.ORGANISATION, "person@e-i")
                .name("Sahara Trading Ltd", NameType.PRIMARY, "person/last_name")
                .designation(new CanonicalDesignation(
                        "UN_CONSOLIDATED", ActionType.ADDITION, "designated by", 2020,
                        new Provenance("designation-verb", 0, 10)))
                .designation(new CanonicalDesignation(
                        "EU_CONSOLIDATED", ActionType.ADDITION, "designated by", 2021,
                        new Provenance("designation-verb", 0, 10)))
                .designation(new CanonicalDesignation(
                        "UN_CONSOLIDATED", ActionType.AMENDMENT, "amended by", 2022,
                        new Provenance("designation-verb", 0, 10)))
                .build();

        WatchEntityVersionAggregate aggregate = new CanonicalRecordMapper(PIPELINE, sequentialIds())
                .toAggregate(record, new EntityId(UUID.randomUUID()), new EntityVersionId(UUID.randomUUID()),
                        new ListVersionId(UUID.randomUUID()), "sha256:test");

        assertThat(aggregate.version().listSources()).containsExactly("EU_CONSOLIDATED", "UN_CONSOLIDATED");
        assertThat(aggregate.designations()).hasSize(3);
    }

    @Test
    void mapsCountriesAndAddresses() {
        CanonicalRecord record = new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Ahmed Yousef", NameType.PRIMARY, "person/last_name")
                .country(new CanonicalCountry("AE", "countries/country"))
                .address(new horus.ingestion.spi.CanonicalAddress("AE", "Dubai", null, "locations/location"))
                .build();

        WatchEntityVersionAggregate aggregate = new CanonicalRecordMapper(PIPELINE, sequentialIds())
                .toAggregate(record, new EntityId(UUID.randomUUID()), new EntityVersionId(UUID.randomUUID()),
                        new ListVersionId(UUID.randomUUID()), "sha256:test");

        assertThat(aggregate.countries()).hasSize(1);
        assertThat(aggregate.countries().get(0).rawCountry()).isEqualTo("AE");
        assertThat(aggregate.addresses()).hasSize(1);
        assertThat(aggregate.addresses().get(0).city()).contains("Dubai");
        assertThat(aggregate.addresses().get(0).state()).isEmpty();
    }
}
