package horus.adapter.source.worldcheck.narrative.source;

import static org.assertj.core.api.Assertions.assertThat;

import horus.adapter.source.worldcheck.narrative.Facts;
import horus.adapter.source.worldcheck.narrative.Section;
import horus.adapter.source.worldcheck.narrative.SectionKind;
import org.junit.jupiter.api.Test;

class OfsiSourceParserTest {

    private final OfsiSourceParser parser = new OfsiSourceParser();

    @Test
    void handlesUkAndCrownDependencyHeaders() {
        assertThat(parser.handles("UK SANCTIONS - UKHMT")).isTrue();
        assertThat(parser.handles("ISLE OF MAN SANCTIONS")).isTrue();
        assertThat(parser.handles("USA SANCTIONS - OFAC")).isFalse();
    }

    @Test
    void extractsAGroupId() {
        Section section = new Section("UK SANCTIONS - UKHMT",
                "PRIMARY NAME: KASSIM TRADING GROUP. Group ID: 8821.",
                0, SectionKind.SANCTIONS);
        Facts.Builder out = Facts.builder();

        parser.parse(section, out);

        assertThat(out.build().identifiers()).anyMatch(i -> "GROUP_ID".equals(i.type())
                && "8821".equals(i.value()));
    }

    @Test
    void extractsNumberedNameVariants() {
        Section section = new Section("UK SANCTIONS - UKHMT",
                "PRIMARY NAME: KASSIM TRADING GROUP. Name 2: KTG HOLDINGS LIMITED.",
                0, SectionKind.SANCTIONS);
        Facts.Builder out = Facts.builder();

        parser.parse(section, out);

        assertThat(out.build().names()).anyMatch(n -> n.value().contains("KTG HOLDINGS"));
    }

    @Test
    void fieldLabelsAreNeverEmittedAsNames() {
        Section section = new Section("UK SANCTIONS - UKHMT",
                "PRIMARY NAME: VELIKANOV, Stepan Igorevich. Designation source: UK. Sex: M.",
                0, SectionKind.SANCTIONS);
        Facts.Builder out = Facts.builder();

        parser.parse(section, out);

        assertThat(out.build().names()).noneMatch(n -> n.value().contains("Designation source"));
    }
}
