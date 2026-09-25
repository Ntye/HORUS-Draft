package horus.adapter.source.worldcheck.narrative;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentifierParserTest {

    @Test
    void extractsAPassportNumber() {
        Facts.Builder out = Facts.builder();

        IdentifierParser.parse("Passport No: AB1234567", 0, "TEST:SOURCE", out);

        Facts facts = out.build();
        assertThat(facts.identifiers()).anyMatch(i -> "PASSPORT".equals(i.type())
                && "AB1234567".equals(i.value()));
    }

    @Test
    void extractsAnImoNumber() {
        Facts.Builder out = Facts.builder();

        IdentifierParser.parse("IMO Number: 9176187", 0, "TEST:SOURCE", out);

        assertThat(out.build().identifiers()).anyMatch(i -> "IMO".equals(i.type())
                && "9176187".equals(i.value()));
    }

    @Test
    void everyExtractedIdentifierCarriesProvenance() {
        Facts.Builder out = Facts.builder();

        IdentifierParser.parse("Group ID: 5501", 0, "TEST:SOURCE", out);

        assertThat(out.build().identifiers()).allSatisfy(i -> assertThat(i.provenance()).isNotNull());
    }

    @Test
    void nullOrEmptyTextYieldsNoIdentifiers() {
        Facts.Builder out = Facts.builder();

        IdentifierParser.parse(null, 0, "TEST:SOURCE", out);
        IdentifierParser.parse("", 0, "TEST:SOURCE", out);

        assertThat(out.build().identifiers()).isEmpty();
    }
}
