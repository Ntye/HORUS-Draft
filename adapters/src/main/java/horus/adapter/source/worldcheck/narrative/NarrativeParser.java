package horus.adapter.source.worldcheck.narrative;

import horus.adapter.source.worldcheck.narrative.source.SourceParser;
import horus.adapter.source.worldcheck.narrative.source.SourceParserRegistry;
import horus.domain.shared.Provenance;
import java.util.List;

/**
 * Orchestrates narrative extraction for one record.
 *
 * Splits further_information into sections, classifies each, and dispatches to
 * the parser that owns it. Prose sections are skipped entirely -- measurement
 * found 14,308 distinct verb forms there and nothing the engine consumes.
 *
 * SCOPE, decided by measurement rather than appetite:
 *   DO    list-source attribution, (source, action, year) triples, ownership,
 *         identifiers
 *   PARTIAL names, only where an authority-specific parser knows the label
 *         vocabulary; the generic fallback does not attempt them
 *   NEVER anything from BIOGRAPHY, REPORTS or FUNDING
 *
 * Pure: no I/O, no shared mutable state beyond the registry's counters,
 * deterministic for identical input (I-4, I-9).
 */
public final class NarrativeParser {

    private final SourceParserRegistry registry;

    public NarrativeParser() { this(SourceParserRegistry.standard()); }

    public NarrativeParser(SourceParserRegistry registry) { this.registry = registry; }

    public Facts parse(String narrative) {
        Facts.Builder out = Facts.builder();
        if (narrative == null || narrative.isBlank()) return out.build();

        for (Section s : Section.split(narrative)) {
            if (s.header() == null) continue;          // preamble
            out.sectionSeen();

            switch (s.kind()) {
                case PROSE -> { /* never parsed for facts */ }

                case OWNERSHIP -> {
                    OwnershipParser.parse(s, out);
                    out.sectionParsed();
                }

                case SANCTIONS -> {
                    if (ListSources.isListSource(s.header())) out.source(canonical(s));
                    SourceParser p = registry.resolve(s.header());
                    p.parse(s, out);
                    out.sectionParsed();
                }

                case IDENTIFIER -> {
                    // v3: sections whose whole content IS an identifier.
                    // IMO REGISTRATION alone accounted for 287 quarantined
                    // sections whose identifiers were being discarded.
                    IdentifierParser.parse(s.body(), s.bodyStart(), canonical(s), out);
                    out.sectionParsed();
                }

                case ASSET_STATUS -> {
                    // v3: vessel/aircraft operating status. Not a designation
                    // and not an identifier, but a real risk signal for VESSEL
                    // and AIRCRAFT screening. Captured as an identifier-shaped
                    // fact so it carries provenance like everything else.
                    out.identifier(new Facts.Identifier("ASSET_STATUS",
                            s.header(), canonical(s),
                            new Provenance("SECTION.ASSET_STATUS", s.bodyStart(),
                                    s.bodyStart() + Math.min(s.body().length(), 200))));
                    out.sectionParsed();
                }

                case LAW_ENFORCEMENT -> {
                    // Debarment and wanted lists carry designation-like events and
                    // identifiers, but not the sanctions lifecycle grammar.
                    IdentifierParser.parse(s.body(), s.bodyStart(), canonical(s), out);
                    out.sectionParsed();
                }

                case REGULATORY_WARNING -> {
                    // Regulator cautions are NOT designations. Identifiers only;
                    // no status effect, ever.
                    IdentifierParser.parse(s.body(), s.bodyStart(), canonical(s), out);
                    out.sectionParsed();
                }

                case UNKNOWN -> out.unparsed(new Facts.Unparsed(
                        s.header(), "UNCLASSIFIED_SECTION", excerpt(s.body()),
                        new Provenance("SECTION.UNKNOWN", s.bodyStart(),
                                s.bodyStart() + Math.min(s.body().length(), 200))));
            }
        }
        return out.build();
    }

    /** Facts plus the derived status, which demotes and explains -- never suppresses. */
    public record Outcome(Facts facts, StatusDeriver.Result status) {}

    public Outcome parseWithStatus(String narrative) {
        Facts f = parse(narrative);
        return new Outcome(f, StatusDeriver.derive(f));
    }

    public SourceParserRegistry registry() { return registry; }

    public List<String> parserIds() { return registry.parserIds(); }

    private static String canonical(Section s) {
        String c = ListSources.canonical(s.header());
        return c == null ? s.header() : c;
    }

    private static String excerpt(String s) {
        String t = s.replace('\t', ' ').replace('\n', ' ').trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }
}
