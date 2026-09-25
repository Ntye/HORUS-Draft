package horus.ingestion.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.Provenance;
import horus.domain.watchentity.ActionType;
import horus.domain.watchentity.NameType;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CanonicalValueTypesTest {

    @Test
    void canonicalNameRejectsBlankValue() {
        assertThatThrownBy(() -> new CanonicalName(" ", NameType.PRIMARY, "person/last_name"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalDobRequiresAtLeastYearOrAge() {
        assertThatThrownBy(() -> new CanonicalDob(Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                "date_of_birth"))
                .isInstanceOf(IllegalArgumentException.class);

        CanonicalDob valid = new CanonicalDob(Optional.of(1980), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                "date_of_birth/year");
        assertThat(valid.year()).contains(1980);
    }

    @Test
    void canonicalCountryRejectsBlankRawCountry() {
        assertThatThrownBy(() -> new CanonicalCountry(" ", "countries/country"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalAddressRequiresAtLeastOneField() {
        assertThatThrownBy(() -> new CanonicalAddress(null, null, null, "locations/location"))
                .isInstanceOf(IllegalArgumentException.class);

        CanonicalAddress valid = new CanonicalAddress("AE", "Dubai", null, "locations/location");
        assertThat(valid.city()).isEqualTo("Dubai");
    }

    @Test
    void canonicalIdentifierRejectsBlankValue() {
        assertThatThrownBy(() -> new CanonicalIdentifier("PASSPORT", " ", "identification/passport"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void canonicalDesignationCarriesRealProvenanceFromNarrativeExtraction() {
        CanonicalDesignation designation = new CanonicalDesignation(
                "USA:OFAC", ActionType.ADDITION, "sanctions imposed", 2020,
                new Provenance("OFAC.ADDITION", 0, 20));

        assertThat(designation.action()).isEqualTo(ActionType.ADDITION);
        assertThat(designation.provenance().ruleId()).isEqualTo("OFAC.ADDITION");
    }

    @Test
    void canonicalOwnershipRejectsBlankOwner() {
        assertThatThrownBy(() -> new CanonicalOwnership(" ", null, null, false,
                new Provenance("OWN.BARE", 0, 10)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
