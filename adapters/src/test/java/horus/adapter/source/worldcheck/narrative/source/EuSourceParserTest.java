package horus.adapter.source.worldcheck.narrative.source;

import static org.assertj.core.api.Assertions.assertThat;

import horus.adapter.source.worldcheck.narrative.Facts;
import horus.adapter.source.worldcheck.narrative.Section;
import horus.adapter.source.worldcheck.narrative.SectionKind;
import org.junit.jupiter.api.Test;

class EuSourceParserTest {

    private final EuSourceParser parser = new EuSourceParser();

    @Test
    void handlesEuAndCfspHeaders() {
        assertThat(parser.handles("EU SANCTIONS")).isTrue();
        assertThat(parser.handles("SOMETHING CFSP RELATED")).isTrue();
        assertThat(parser.handles("USA SANCTIONS - OFAC")).isFalse();
    }

    @Test
    void extractsALegalBasisReference() {
        Section section = new Section("EU SANCTIONS",
                "CP 2001/931/CFSP, subject only to Article 4.", 0, SectionKind.SANCTIONS);
        Facts.Builder out = Facts.builder();

        parser.parse(section, out);

        assertThat(out.build().identifiers()).anyMatch(i -> "EU_LEGAL_BASIS".equals(i.type())
                && "2001/931/CFSP".equals(i.value()));
    }

    @Test
    void extractsASingleQuotedAlias() {
        Section section = new Section("EU SANCTIONS",
                "PRIMARY NAME: Deka Enosi Palmara. Alias: 'Palmara Liberation Front'.",
                0, SectionKind.SANCTIONS);
        Facts.Builder out = Facts.builder();

        parser.parse(section, out);

        assertThat(out.build().names()).anyMatch(n -> "Palmara Liberation Front".equals(n.value()));
    }

    @Test
    void doesNotSplitOnAPossessiveApostropheInsideAQuotedAlias() {
        Section section = new Section("EU SANCTIONS",
                "Alias: 'Astoria People's Republic and the Veyland People's Republic'.",
                0, SectionKind.SANCTIONS);
        Facts.Builder out = Facts.builder();

        parser.parse(section, out);

        assertThat(out.build().names()).noneMatch(n -> n.value().startsWith("s Republic"));
    }
}
