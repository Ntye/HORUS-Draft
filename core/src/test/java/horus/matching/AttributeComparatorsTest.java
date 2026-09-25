package horus.matching;

import static horus.matching.MatchTestData.candidate;
import static horus.matching.MatchTestData.n;
import static org.assertj.core.api.Assertions.assertThat;

import horus.domain.shared.EntityType;
import horus.domain.shared.Provenance;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AttributeComparatorsTest {

    private static final MatchConfig CFG = MatchConfig.defaults();

    private static MatchQuery queryWith(
            EntityType type, PartialDate dob, Set<String> countries, Set<String> identifiers) {
        return new MatchQuery(n("Zorvan Talmesk"), Optional.ofNullable(type), Optional.ofNullable(dob),
                countries, identifiers);
    }

    private static CandidateDob dob(int year, Integer month, Integer day) {
        return new CandidateDob(OptionalInt.of(year), month == null ? OptionalInt.empty() : OptionalInt.of(month),
                day == null ? OptionalInt.empty() : OptionalInt.of(day), OptionalInt.empty(), OptionalInt.empty());
    }

    // ---- DOB ----

    @Test
    void dobExactMatchAddsTheExactEffect() {
        AttributeEffect e = new DobComparator().compare(
                queryWith(null, new PartialDate(1980, OptionalInt.of(5), OptionalInt.of(17)), Set.of(), Set.of()),
                candidate("X Y", EntityType.INDIVIDUAL).dob(dob(1980, 5, 17)).build(), CFG);

        assertThat(e.delta()).isEqualTo(CFG.dobExact());
        assertThat(e.attribute()).isEqualTo("dob");
    }

    @Test
    void dobYearOnlyMatchAddsTheYearEffect() {
        AttributeEffect e = new DobComparator().compare(
                queryWith(null, new PartialDate(1980, OptionalInt.empty(), OptionalInt.empty()), Set.of(), Set.of()),
                candidate("X Y", EntityType.INDIVIDUAL).dob(dob(1980, 5, 17)).build(), CFG);

        assertThat(e.delta()).isEqualTo(CFG.dobYearOnly());
    }

    @Test
    void dobConflictBeyondToleranceDemotes() {
        AttributeEffect e = new DobComparator().compare(
                queryWith(null, new PartialDate(1950, OptionalInt.empty(), OptionalInt.empty()), Set.of(), Set.of()),
                candidate("X Y", EntityType.INDIVIDUAL).dob(dob(1980, 5, 17)).build(), CFG);

        assertThat(e.delta()).isEqualTo(CFG.dobConflict());
        assertThat(e.reason()).isNotBlank();
    }

    @Test
    void dobWithinToleranceIsNotAConflict() {
        // Listed birth years are often off by one; a demotion for that would cost recall (I-2).
        AttributeEffect e = new DobComparator().compare(
                queryWith(null, new PartialDate(1981, OptionalInt.empty(), OptionalInt.empty()), Set.of(), Set.of()),
                candidate("X Y", EntityType.INDIVIDUAL).dob(dob(1980, null, null)).build(), CFG);

        assertThat(e.delta()).isEqualTo(0.0);
    }

    @Test
    void dobAbsentOnEitherSideIsNeutralNeverPenalised() {
        AttributeEffect candidateHasNone = new DobComparator().compare(
                queryWith(null, new PartialDate(1980, OptionalInt.empty(), OptionalInt.empty()), Set.of(), Set.of()),
                candidate("X Y", EntityType.INDIVIDUAL).build(), CFG);
        AttributeEffect queryHasNone = new DobComparator().compare(
                queryWith(null, null, Set.of(), Set.of()),
                candidate("X Y", EntityType.INDIVIDUAL).dob(dob(1980, 5, 17)).build(), CFG);

        assertThat(candidateHasNone.delta()).isEqualTo(0.0);
        assertThat(candidateHasNone.capabilityGap()).isPresent();
        assertThat(queryHasNone.delta()).isEqualTo(0.0);
        assertThat(queryHasNone.capabilityGap()).isEmpty();
    }

    @Test
    void dobUsesTheBestOfSeveralCandidateDatesOfBirth() {
        AttributeEffect e = new DobComparator().compare(
                queryWith(null, new PartialDate(1980, OptionalInt.empty(), OptionalInt.empty()), Set.of(), Set.of()),
                candidate("X Y", EntityType.INDIVIDUAL).dob(dob(1950, null, null)).dob(dob(1980, null, null)).build(),
                CFG);

        assertThat(e.delta()).isEqualTo(CFG.dobYearOnly());
    }

    @Test
    void ageOnlyDobIsConvertedToABirthYearUsingItsAsOfYear() {
        CandidateDob ageOnly = new CandidateDob(OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(),
                OptionalInt.of(40), OptionalInt.of(2020));

        AttributeEffect e = new DobComparator().compare(
                queryWith(null, new PartialDate(1980, OptionalInt.empty(), OptionalInt.empty()), Set.of(), Set.of()),
                candidate("X Y", EntityType.INDIVIDUAL).dob(ageOnly).build(), CFG);

        assertThat(e.delta()).isEqualTo(CFG.dobYearOnly());
    }

    // ---- country ----

    @Test
    void countryOverlapCorroboratesAndDisjointSetsConflict() {
        CountryComparator c = new CountryComparator();

        assertThat(c.compare(queryWith(null, null, Set.of("AE", "EG"), Set.of()),
                        candidate("X Y", EntityType.INDIVIDUAL).country("EG").build(), CFG).delta())
                .isEqualTo(CFG.countryCorroboration());
        assertThat(c.compare(queryWith(null, null, Set.of("AE"), Set.of()),
                        candidate("X Y", EntityType.INDIVIDUAL).country("EG").build(), CFG).delta())
                .isEqualTo(CFG.countryConflict());
    }

    @Test
    void countryAbsenceIsNeutral() {
        CountryComparator c = new CountryComparator();

        assertThat(c.compare(queryWith(null, null, Set.of(), Set.of()),
                        candidate("X Y", EntityType.INDIVIDUAL).country("EG").build(), CFG).delta())
                .isEqualTo(0.0);
        assertThat(c.compare(queryWith(null, null, Set.of("AE"), Set.of()),
                        candidate("X Y", EntityType.INDIVIDUAL).build(), CFG).delta())
                .isEqualTo(0.0);
    }

    // ---- identifier ----

    @Test
    void identifierExactMatchForcesAStrongMatchAndCarriesProvenance() {
        Provenance provenance = new Provenance("passport-rule", 10, 20);

        AttributeEffect e = new IdentifierComparator().compare(
                queryWith(null, null, Set.of(), Set.of("P1234567")),
                candidate("X Y", EntityType.INDIVIDUAL)
                        .identifier(new CandidateIdentifier("P1234567", Optional.of(provenance))).build(),
                CFG);

        assertThat(e.forcesStrongMatch()).isTrue();
        assertThat(e.provenance()).contains(provenance);
    }

    @Test
    void identifierMismatchOrAbsenceNeverForcesAnything() {
        IdentifierComparator c = new IdentifierComparator();

        AttributeEffect mismatch = c.compare(queryWith(null, null, Set.of(), Set.of("P1")),
                candidate("X Y", EntityType.INDIVIDUAL)
                        .identifier(new CandidateIdentifier("P2", Optional.empty())).build(), CFG);

        assertThat(mismatch.forcesStrongMatch()).isFalse();
        assertThat(mismatch.delta()).isEqualTo(0.0);
    }

    // ---- entity type ----

    @Test
    void entityTypeMismatchBetweenPersonAndOrganisationDemotes() {
        EntityTypeComparator c = new EntityTypeComparator();

        assertThat(c.compare(queryWith(EntityType.INDIVIDUAL, null, Set.of(), Set.of()),
                        candidate("X Y", EntityType.ORGANISATION).build(), CFG).delta())
                .isEqualTo(CFG.entityTypeMismatch());
        assertThat(c.compare(queryWith(EntityType.ORGANISATION, null, Set.of(), Set.of()),
                        candidate("X Y", EntityType.VESSEL).build(), CFG).delta())
                .isEqualTo(0.0);
        assertThat(c.compare(queryWith(null, null, Set.of(), Set.of()),
                        candidate("X Y", EntityType.VESSEL).build(), CFG).delta())
                .isEqualTo(0.0);
    }

    // ---- status ----

    @Test
    void delistedStatusDemotesAndSaysTheEntityMayStillBeDesignated() {
        AttributeEffect e = new StatusComparator().compare(
                queryWith(null, null, Set.of(), Set.of()),
                candidate("X Y", EntityType.INDIVIDUAL).delisted(true).build(), CFG);

        assertThat(e.delta()).isEqualTo(CFG.delisted());
        assertThat(e.reason()).containsIgnoringCase("may still be designated");
    }
}
