package horus.adapter.source.worldcheck.narrative;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OwnershipParserTest {

    @Test
    void parsesMultipleStakesWithExplicitPercentagesAndTypeMarkers() {
        Section section = new Section("DIRECT SHAREHOLDER/S",
                "Ministry of Finance (50%). Northgate Holdings Council (IOS) (50%).",
                0, SectionKind.OWNERSHIP);
        Facts.Builder out = Facts.builder();

        OwnershipParser.parse(section, out);

        Facts facts = out.build();
        assertThat(facts.ownership()).hasSize(2);
        assertThat(facts.ownership()).anyMatch(s ->
                s.percent() != null && Math.abs(s.percent() - 50.0) < 0.001);
        assertThat(facts.ownership()).anyMatch(s -> "IOS".equals(s.ownerType()));
    }

    @Test
    void parsesEuropeanCommaDecimalPercentages() {
        Section section = new Section("DIRECT SHAREHOLDER/S",
                "Ministry of Economy (51,7%). Northland Energy Group (SOE) (46,78%).",
                0, SectionKind.OWNERSHIP);
        Facts.Builder out = Facts.builder();

        OwnershipParser.parse(section, out);

        assertThat(out.build().ownership()).anyMatch(s ->
                s.percent() != null && Math.abs(s.percent() - 51.7) < 0.001);
    }

    @Test
    void unknownPercentageIsFlaggedNotDropped() {
        Section section = new Section("ULTIMATE GOVERNMENT OWNERSHIP",
                "Government of Palmara (unknown percentage).", 0, SectionKind.OWNERSHIP);
        Facts.Builder out = Facts.builder();

        OwnershipParser.parse(section, out);

        assertThat(out.build().ownership()).anyMatch(
                s -> !s.percentStated() && s.owner().contains("Palmara"));
    }
}
