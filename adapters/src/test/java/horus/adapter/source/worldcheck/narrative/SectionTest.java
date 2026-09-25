package horus.adapter.source.worldcheck.narrative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SectionTest {

    @Test
    void splitsMultipleBracketedSectionsWithPreambleIgnored() {
        String text = "preamble text [USA SANCTIONS - OFAC] body one. [BIOGRAPHY] body two.";

        List<Section> sections = Section.split(text);

        assertThat(sections).hasSize(3); // preamble (null header) + two real sections
        assertThat(sections.get(0).header()).isNull();
        assertThat(sections.get(1).header()).isEqualTo("USA SANCTIONS - OFAC");
        assertThat(sections.get(2).header()).isEqualTo("BIOGRAPHY");
    }

    @Test
    void classifiesKnownProseHeadersAsProse() {
        assertThat(Section.classify("BIOGRAPHY")).isEqualTo(SectionKind.PROSE);
        assertThat(Section.classify("KEYWORD NOTE")).isEqualTo(SectionKind.PROSE);
        assertThat(Section.classify("FENTANYL")).isEqualTo(SectionKind.PROSE);
    }

    @Test
    void classifiesSanctionsHeaders() {
        assertThat(Section.classify("USA SANCTIONS - OFAC")).isEqualTo(SectionKind.SANCTIONS);
        assertThat(Section.classify("EU RESTRICTIVE MEASURES")).isEqualTo(SectionKind.SANCTIONS);
        assertThat(Section.classify("UK INVESTMENT BAN - UKHMT-IB")).isEqualTo(SectionKind.SANCTIONS);
    }

    @Test
    void classifiesBareJurisdictionsExactlyAsSanctions() {
        assertThat(Section.classify("USA")).isEqualTo(SectionKind.SANCTIONS);
        assertThat(Section.classify("UN")).isEqualTo(SectionKind.SANCTIONS);
    }

    @Test
    void classifiesIdentifierAndAssetStatusHeaders() {
        assertThat(Section.classify("IMO REGISTRATION")).isEqualTo(SectionKind.IDENTIFIER);
        assertThat(Section.classify("DETAINED VESSEL")).isEqualTo(SectionKind.ASSET_STATUS);
    }

    @Test
    void classifiesOwnershipBeforeSanctionsWhenBothWordsCouldMatch() {
        // "MAJORITY DIRECT STATE OWNED" contains neither hint literally, but exercises
        // ownership-before-sanctions precedence via a header that does.
        assertThat(Section.classify("DIRECT SHAREHOLDER/S")).isEqualTo(SectionKind.OWNERSHIP);
    }

    @Test
    void classifiesWarningsAsRegulatoryNotSanctions() {
        // "CIVIL PENALTIES - OFAC" contains no sanctions hint but has a warning hint.
        assertThat(Section.classify("CIVIL PENALTIES - OFAC")).isEqualTo(SectionKind.REGULATORY_WARNING);
    }

    @Test
    void unrecognisedHeaderIsUnknown() {
        assertThat(Section.classify("SOME UNRECOGNISED HEADER TEXT")).isEqualTo(SectionKind.UNKNOWN);
    }

    @Test
    void parseableIsFalseForProseAndForThePreambleSection() {
        Section prose = new Section("BIOGRAPHY", "text", 0, SectionKind.PROSE);
        Section preamble = new Section(null, "text", 0, SectionKind.UNKNOWN);
        Section sanctions = new Section("USA SANCTIONS - OFAC", "text", 0, SectionKind.SANCTIONS);

        assertThat(prose.parseable()).isFalse();
        assertThat(preamble.parseable()).isFalse();
        assertThat(sanctions.parseable()).isTrue();
    }
}
