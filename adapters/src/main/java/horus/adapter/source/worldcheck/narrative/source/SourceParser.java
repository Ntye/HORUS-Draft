package horus.adapter.source.worldcheck.narrative.source;

import horus.adapter.source.worldcheck.narrative.Facts;
import horus.adapter.source.worldcheck.narrative.Section;

/**
 * Parses one sanctions authority's record layout as reproduced inside
 * further_information.
 *
 * WHY PER-SOURCE. Each [SOURCE] section reproduces that authority's own record
 * format verbatim. Measurement showed UK OFSI field labels ("Sex:",
 * "Designation source:", "Secondary sanctions risk:", "Financial sanctions
 * imposed in addition to an asset freeze:") sitting alongside OFAC conventions
 * ("SDN Ref No", "c/o", "Cedula No.", "Addr", "(a.k.a. X; a.k.a. Y)"). A single
 * generic extractor mistakes those labels for names -- that is precisely what
 * polluted the first alias-overlap measurement.
 *
 * Each parser knows its own authority's label vocabulary, which is what lets it
 * tell a name from a field heading.
 *
 * Implementations MUST be pure: no I/O, no shared mutable state, deterministic
 * for identical input (I-4, I-9). Anything recognised but unhandled goes to
 * Facts.Unparsed rather than being dropped.
 */
public interface SourceParser {

    /** Stable id, used in Provenance rule ids and in coverage reporting. */
    String parserId();

    /** True when this parser owns the section. Tested in registry order. */
    boolean handles(String sectionHeader);

    /** Extracts facts from the section into the builder. */
    void parse(Section section, Facts.Builder out);
}
