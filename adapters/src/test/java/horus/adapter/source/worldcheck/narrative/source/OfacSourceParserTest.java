package horus.adapter.source.worldcheck.narrative.source;

import static org.assertj.core.api.Assertions.assertThat;

import horus.adapter.source.worldcheck.narrative.Facts;
import horus.adapter.source.worldcheck.narrative.Section;
import horus.adapter.source.worldcheck.narrative.SectionKind;
import org.junit.jupiter.api.Test;

class OfacSourceParserTest {

    private final OfacSourceParser parser = new OfacSourceParser();

    @Test
    void handlesOfacSdnAndEntityListHeaders() {
        assertThat(parser.handles("USA SANCTIONS - OFAC")).isTrue();
        assertThat(parser.handles("BIS ENTITY LIST")).isTrue();
        assertThat(parser.handles("EU SANCTIONS")).isFalse();
        assertThat(parser.handles(null)).isFalse();
    }

    @Test
    void extractsAnSdnReferenceNumber() {
        Section section = new Section("USA SANCTIONS - OFAC",
                "SDN Ref No 7788 - FTO (Aug 1997 - addition).", 0, SectionKind.SANCTIONS);
        Facts.Builder out = Facts.builder();

        parser.parse(section, out);

        assertThat(out.build().identifiers()).anyMatch(i -> "SDN_REF".equals(i.type())
                && "7788".equals(i.value()));
    }

    @Test
    void extractsParenthesisedAliasesSeparatedBySemicolons() {
        Section section = new Section("USA SANCTIONS - OFAC",
                "PRIMARY NAME: KASSIM TRADING GROUP (a.k.a. KASSIM TRADE; a.k.a. KASSIM HOLDINGS).",
                0, SectionKind.SANCTIONS);
        Facts.Builder out = Facts.builder();

        parser.parse(section, out);

        assertThat(out.build().names()).anyMatch(n -> "KASSIM TRADE".equals(n.value()));
        assertThat(out.build().names()).anyMatch(n -> "KASSIM HOLDINGS".equals(n.value()));
    }

    @Test
    void addressAfterCoSlashOIsNeverEmittedAsAName() {
        Section section = new Section("USA SANCTIONS - OFAC",
                "PRIMARY NAME: MORALES TORRES, Reinaldo, c/o INMOBILIARIA DEL VALLE SRL., "
                        + "Medellin, Colombia.",
                0, SectionKind.SANCTIONS);
        Facts.Builder out = Facts.builder();

        parser.parse(section, out);

        assertThat(out.build().names()).noneMatch(n -> n.value().contains("Medellin"));
    }
}
