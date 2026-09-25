package horus.adapter.source.worldcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.EntityType;
import horus.domain.shared.Gender;
import horus.ingestion.format.RawRecord;
import horus.ingestion.format.XmlFormatReader;
import horus.ingestion.spi.CanonicalCountry;
import horus.ingestion.spi.CanonicalDob;
import horus.ingestion.spi.CanonicalIdentifier;
import horus.ingestion.spi.CanonicalName;
import horus.ingestion.spi.CanonicalRecord;
import horus.ingestion.spi.RecordMappingException;
import horus.domain.watchentity.NameType;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// THE TEST THAT DID NOT EXIST (D12 B-6). Until 2026-09-25 nothing anywhere loaded a fixture
// through WorldCheckAdapter: `new WorldCheckAdapter()` appeared only in bootstrap wiring, and
// LoadWatchlistVersionIntegrationTest deliberately substitutes a stub adapter, because the real
// one's CapabilityExpectation is calibrated to the whole feed and twenty records can never satisfy
// it. That reasoning is sound for the integration test and was allowed to excuse the unit test
// too -- yet toCanonical needs no database and no capability gate. The mapping that every one of
// 5,990,394 records passes through was therefore the one part of the pipeline with no test at all.
//
// So this drives the real reader over the real fixture and hand-checks the canonical output, which
// is what CLAUDE.md §10 asks of an adapter: "a synthetic source file whose expected canonical
// output is hand-checked ... the fixture is the only thing that catches it".
class WorldCheckAdapterTest {

    private static final WorldCheckAdapter ADAPTER = new WorldCheckAdapter();

    // The two records the fixture deliberately makes unmappable, and the one it deliberately
    // leaves without a primary name. Naming them here means the sweep below can assert that
    // EVERY other record maps and is named -- the regression guard for a path going stale.
    private static final Set<String> UNMAPPABLE = Set.of("wc-016", "wc-017");
    private static final Set<String> NO_PRIMARY_NAME = Set.of("wc-018");

    private static Map<String, RawRecord> raw;

    @BeforeAll
    static void readFixture() throws Exception {
        raw = new LinkedHashMap<>();
        try (InputStream in = WorldCheckAdapterTest.class.getResourceAsStream(
                "/worldcheck/fixture-20-records.xml")) {
            assertThat(in).as("fixture must be on the test classpath").isNotNull();
            Iterator<RawRecord> records = new XmlFormatReader("record").open(in);
            while (records.hasNext()) {
                RawRecord record = records.next();
                raw.put(record.attributes().get("@uid"), record);
            }
        }
    }

    @Test
    void readsEveryFixtureRecord() {
        assertThat(raw).hasSize(20);
        assertThat(raw.keySet()).contains("wc-001", "wc-020");
    }

    // The sweep. If a path in the adapter drifts from the fixture, this fails first and loudly,
    // rather than a specific assertion failing for an obscure reason.
    @Test
    void everyMappableRecordIsTypedAndNamed() {
        for (String uid : raw.keySet()) {
            if (UNMAPPABLE.contains(uid)) {
                continue;
            }
            CanonicalRecord record = map(uid);
            assertThat(record.entityType()).as("entity type of %s", uid).isNotNull();
            assertThat(record.names()).as("names of %s", uid).isNotEmpty();
            if (NO_PRIMARY_NAME.contains(uid)) {
                assertThat(record.has(CanonicalSlot.PRIMARY_NAME)).as("%s has no primary name", uid).isFalse();
            } else {
                assertThat(record.has(CanonicalSlot.PRIMARY_NAME)).as("primary name of %s", uid).isTrue();
                assertThat(primaryNameOf(record)).as("primary name of %s", uid).isNotBlank();
            }
        }
    }

    @Test
    void individualPrimaryNameIsFirstThenLast() {
        CanonicalRecord record = map("wc-001");

        assertThat(record.entityType()).isEqualTo(EntityType.INDIVIDUAL);
        assertThat(record.gender()).contains(Gender.MALE);
        assertThat(primaryNameOf(record)).isEqualTo("Tarek Kassim");
        assertThat(aliasesOf(record)).containsExactly("KASSIM,Tarek", "Tareq Kasim");
        assertThat(record.provenance().get(CanonicalSlot.PRIMARY_NAME))
                .isEqualTo("person/names/first_name+person/names/last_name");
    }

    @Test
    void organisationPrimaryNameComesFromLastNameAlone() {
        CanonicalRecord record = map("wc-002");

        assertThat(record.entityType()).isEqualTo(EntityType.ORGANISATION);
        assertThat(record.gender()).isEmpty();
        // An empty <first_name/> must not leave a leading space, which is what naive joining does.
        assertThat(primaryNameOf(record)).isEqualTo("NORTHWIND HOLDING COMPANY");
        assertThat(record.countries().stream().map(CanonicalCountry::rawCountry).toList())
                .containsExactly("CY", "MT");
    }

    @Test
    void nonOrganisationCategoriesMapToTheirOwnEntityType() {
        Map<String, EntityType> expected = Map.of(
                "wc-003", EntityType.VESSEL,
                "wc-004", EntityType.COUNTRY,
                "wc-005", EntityType.AIRCRAFT,
                "wc-006", EntityType.WEBSITE,
                "wc-007", EntityType.PORT,
                "wc-008", EntityType.ADDRESS);

        expected.forEach((uid, type) ->
                assertThat(map(uid).entityType()).as("entity type of %s", uid).isEqualTo(type));
    }

    // "1939-00-00": legal in the feed, illegal for LocalDate. The year is the only part of a
    // birth date most records carry, and it is the part DobComparator can actually use.
    @Test
    void yearOnlyDateOfBirthKeepsTheYearAndDropsTheRest() {
        CanonicalDob dob = onlyDobOf(map("wc-009"));

        assertThat(dob.year()).contains(1939);
        assertThat(dob.month()).isEmpty();
        assertThat(dob.day()).isEmpty();
    }

    @Test
    void ageWithoutADateOfBirthIsStillADateOfBirthSlot() {
        CanonicalRecord record = map("wc-010");
        CanonicalDob dob = onlyDobOf(record);

        assertThat(record.has(CanonicalSlot.DATE_OF_BIRTH)).isTrue();
        assertThat(dob.year()).isEmpty();
        assertThat(dob.age()).contains(62);
        assertThat(dob.asOfDate()).contains(LocalDate.of(2024, 1, 1));
        assertThat(record.gender()).contains(Gender.UNKNOWN);
    }

    @Test
    void completeDateOfDeathIsMapped() {
        assertThat(onlyDobOf(map("wc-011")).deceased()).contains(LocalDate.of(2015, 6, 2));
    }

    // The behaviour that matters most here is that the record SURVIVES. Strict parsing of
    // "2015-00-00" throws, and a throw in the adapter quarantines the whole record.
    @Test
    void partialDateOfDeathIsAbsentRatherThanFatal() {
        CanonicalDob dob = onlyDobOf(map("wc-012"));

        assertThat(dob.year()).contains(1951);
        assertThat(dob.deceased()).isEmpty();
    }

    @Test
    void alternativeSpellingSplitsOnSemicolon() {
        assertThat(aliasesOf(map("wc-013")))
                .containsExactly("SOLOVYEV,Yevgeni", "SOLOVIOV,Evgeni", "SOLOVEV,Yevgeny");
    }

    @Test
    void aliasIdenticalToThePrimaryNameIsNotStoredTwice() {
        CanonicalRecord record = map("wc-014");

        assertThat(primaryNameOf(record)).isEqualTo("Idrissa TRAORE");
        assertThat(aliasesOf(record)).containsExactly("TRAORE,Idriss");
        assertThat(record.names()).hasSize(2);
    }

    @Test
    void everyDistinctPassportBecomesAnIdentifier() {
        CanonicalRecord record = map("wc-015");

        assertThat(record.identifiers().stream().map(CanonicalIdentifier::idValue).toList())
                .containsExactly("QQ0099887", "RR1122334");
        assertThat(record.has(CanonicalSlot.IDENTIFIER)).isTrue();
    }

    @Test
    void unrecognisedEntityIndicatorIsQuarantinedWithAReasonCode() {
        assertThatThrownBy(() -> map("wc-016"))
                .isInstanceOf(RecordMappingException.class)
                .extracting(e -> ((RecordMappingException) e).reasonCode())
                .isEqualTo("ENTITY_TYPE_UNKNOWN_EI:Z");
    }

    @Test
    void missingEntityIndicatorIsQuarantinedWithAReasonCode() {
        assertThatThrownBy(() -> map("wc-017"))
                .isInstanceOf(RecordMappingException.class)
                .extracting(e -> ((RecordMappingException) e).reasonCode())
                .isEqualTo("ENTITY_TYPE_NO_EI");
    }

    // An alias-only record must build, with PRIMARY_NAME asserted absent. That absence is the
    // signal LoadWatchlistVersion acts on; silently ingesting it is the recall hole that 10,460
    // records fell into on the first real load.
    @Test
    void recordWithNoFirstOrLastNameBuildsWithPrimaryNameAbsent() {
        CanonicalRecord record = map("wc-018");

        assertThat(record.has(CanonicalSlot.PRIMARY_NAME)).isFalse();
        assertThat(record.absentSlots()).contains(CanonicalSlot.PRIMARY_NAME);
        assertThat(aliasesOf(record)).containsExactly("ONLY,An Alias");
        assertThat(record.names().stream().map(CanonicalName::type).toList()).containsExactly(NameType.ALIAS);
    }

    @Test
    void narrativeContributesDesignationsAndAliases() {
        CanonicalRecord record = map("wc-019");

        assertThat(record.entityType()).isEqualTo(EntityType.ORGANISATION);
        assertThat(record.designations()).isNotEmpty();
        assertThat(aliasesOf(record)).isNotEmpty();
    }

    // Recorded as an assertion rather than a comment so the limitation cannot quietly change:
    // RawRecord holds attributes in a flat map, so only the last <location> survives.
    @Test
    void onlyTheLastLocationSurvivesAndNilCountryIsNotAValue() {
        CanonicalRecord record = map("wc-020");

        assertThat(record.addresses()).hasSize(1);
        assertThat(record.addresses().get(0).city()).isEqualTo("London");
        assertThat(record.addresses().get(0).rawCountry()).isEqualTo("GB");
        assertThat(record.countries().stream().map(CanonicalCountry::rawCountry).toList()).containsExactly("ZA");
    }

    @Test
    void absentSlotsAreAssertedRatherThanLeftUnexplained() {
        CanonicalRecord record = map("wc-003"); // vessel: no dob, no passport, no location

        assertThat(record.absentSlots()).contains(
                CanonicalSlot.DATE_OF_BIRTH, CanonicalSlot.IDENTIFIER, CanonicalSlot.ADDRESS);
        assertThat(record.has(CanonicalSlot.COUNTRY)).isTrue();
    }

    private static CanonicalRecord map(String uid) {
        RawRecord record = raw.get(uid);
        assertThat(record).as("fixture record %s", uid).isNotNull();
        return ADAPTER.toCanonical(record);
    }

    private static String primaryNameOf(CanonicalRecord record) {
        return record.names().stream()
                .filter(n -> n.type() == NameType.PRIMARY)
                .map(CanonicalName::value)
                .findFirst()
                .orElse(null);
    }

    private static List<String> aliasesOf(CanonicalRecord record) {
        return record.names().stream()
                .filter(n -> n.type() == NameType.ALIAS)
                .map(CanonicalName::value)
                .toList();
    }

    private static CanonicalDob onlyDobOf(CanonicalRecord record) {
        assertThat(record.dobs()).hasSize(1);
        return record.dobs().get(0);
    }
}
