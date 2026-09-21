package horus.narrative.source;

import horus.narrative.Facts;
import horus.narrative.Section;

/**
 * Fallback for sanctions sections with no authority-specific parser.
 *
 * Extracts the (source, action, year) grammar, which is consistent across every
 * authority, but does NOT attempt names: without knowing the authority's label
 * vocabulary, name extraction produces field headings rather than names. That
 * restraint is deliberate -- injecting label text into the name index costs
 * precision and buys no recall.
 *
 * A persistently high share of sections landing here is the signal that another
 * authority-specific parser is worth writing. Watch SourceParserRegistry
 * resolutionCounts().
 */
public final class GenericSanctionsParser extends AbstractSourceParser {

    @Override public String parserId() { return "GENERIC"; }

    @Override public boolean handles(String sectionHeader) { return true; }

    @Override protected String[] labels() { return new String[0]; }

    @Override
    public void parse(Section section, Facts.Builder out) {
        extractEvents(section, out);
        // names deliberately not attempted -- see class comment
    }
}
