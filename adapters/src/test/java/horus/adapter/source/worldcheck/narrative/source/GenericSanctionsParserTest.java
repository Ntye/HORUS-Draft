package horus.adapter.source.worldcheck.narrative.source;

import static org.assertj.core.api.Assertions.assertThat;

import horus.adapter.source.worldcheck.narrative.Facts;
import horus.adapter.source.worldcheck.narrative.Section;
import horus.adapter.source.worldcheck.narrative.SectionKind;
import org.junit.jupiter.api.Test;

class GenericSanctionsParserTest {

    private final GenericSanctionsParser parser = new GenericSanctionsParser();

    @Test
    void handlesEverything() {
        assertThat(parser.handles("ANY UNRECOGNISED AUTHORITY")).isTrue();
        assertThat(parser.handles(null)).isTrue();
    }

    @Test
    void extractsEventsButDeliberatelyNeverAttemptsNames() {
        Section section = new Section("SOME OTHER JURISDICTION SANCTIONS",
                "PRIMARY NAME: SOMETHING THAT LOOKS LIKE A NAME. (Jan 2020 - addition).",
                0, SectionKind.SANCTIONS);
        Facts.Builder out = Facts.builder();

        parser.parse(section, out);

        Facts facts = out.build();
        assertThat(facts.events()).isNotEmpty();
        assertThat(facts.names()).isEmpty();
    }
}
