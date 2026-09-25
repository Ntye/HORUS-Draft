package horus.adapter.source.worldcheck.narrative.source;

import horus.adapter.source.worldcheck.narrative.Facts;
import horus.adapter.source.worldcheck.narrative.IdentifierParser;
import horus.adapter.source.worldcheck.narrative.ListSources;
import horus.adapter.source.worldcheck.narrative.Section;
import horus.domain.shared.Provenance;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EU consolidated list format, plus the EU-aligned national lists.
 *
 * Conventions observed in the feed:
 *   CP 2001/931/CFSP, subject only to Article 4 (Jun 2009 - amended)
 *   2005/671/JHA
 *   PRIMARY NAME: Dekati Evdomi Noemvri
 *   Alias: 'Revolutionary Organisation 17 November'
 *
 * EU aliases are single-quoted, and the legal basis is a citable regulation
 * reference. That reference is worth capturing: it is the closest thing the feed
 * has to a designation programme identifier for EU listings.
 */
public final class EuSourceParser extends AbstractSourceParser {

    @Override public String parserId() { return "EU"; }

    @Override
    public boolean handles(String h) {
        if (h == null) return false;
        return h.startsWith("EU SANCTIONS") || h.contains("CFSP")
                || h.contains("EUROPEAN UNION");
    }

    private static final String[] LABELS = {
        "Reasons", "Date of listing", "Identifying information",
        "Legal basis", "Council Regulation", "Council Decision",
        "Article", "Annex", "Entry", "Remark"
    };

    @Override protected String[] labels() { return LABELS; }

    /** 2001/931/CFSP, 2005/671/JHA, (EU) 2014/145 */
    private static final Pattern LEGAL_BASIS = Pattern.compile(
        "\\b(?:\\(EU\\)\\s*)?(\\d{4}/\\d{1,4}/(?:CFSP|JHA|EC|EU))\\b");
    private static final Pattern REG_NUMBER = Pattern.compile(
        "\\bRegulation\\s+\\(E[UC]\\)\\s+No\\.?\\s*(\\d{1,6}/\\d{4})",
        Pattern.CASE_INSENSITIVE);
    /**
     * EU quotes aliases: 'Revolutionary Organisation 17 November'
     *
     * v3: the previous pattern "'([^']{3,120})'" matched across possessive
     * apostrophes, so "People's Republic ... Luhansk People's" yielded the
     * fragment "s Republic and the Luhansk People". An opening quote must now be
     * preceded by start-of-string, whitespace or an opening bracket, and the
     * content must begin with a letter or digit -- which a possessive tail
     * cannot.
     */
    private static final Pattern QUOTED = Pattern.compile(
        "(?<=^|[\\s(\\[:,])'([\\p{L}\\p{Nd}][^']{2,119})'");

    @Override
    public void parse(Section section, Facts.Builder out) {
        String body = section.body();
        int offset = section.bodyStart();
        String source = ListSources.canonical(section.header());

        extractEvents(section, out);
        extractNames(section, out);
        IdentifierParser.parse(body, offset, source, out);

        Matcher lb = LEGAL_BASIS.matcher(body);
        int n = 0;
        while (lb.find() && n < 10) {
            out.identifier(new Facts.Identifier("EU_LEGAL_BASIS", lb.group(1), source,
                    new Provenance("EU.LEGAL_BASIS", offset + lb.start(1),
                            offset + lb.end(1))));
            n++;
        }

        Matcher rn = REG_NUMBER.matcher(body);
        if (rn.find()) {
            out.identifier(new Facts.Identifier("EU_REGULATION", rn.group(1), source,
                    new Provenance("EU.REGULATION", offset + rn.start(1),
                            offset + rn.end(1))));
        }

        Matcher q = QUOTED.matcher(body);
        int emitted = 0;
        while (q.find() && emitted < 20) {
            String cand = cutAtLabel(q.group(1));
            if (plausibleName(cand)) {
                out.name(new Facts.Name(cand, "AKA", parserId(),
                        new Provenance("EU.QUOTED_ALIAS",
                                offset + q.start(1), offset + q.end(1))));
                emitted++;
            }
        }
    }
}
