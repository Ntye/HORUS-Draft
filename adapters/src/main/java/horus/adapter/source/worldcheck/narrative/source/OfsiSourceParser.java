package horus.adapter.source.worldcheck.narrative.source;

import horus.adapter.source.worldcheck.narrative.Facts;
import horus.adapter.source.worldcheck.narrative.IdentifierParser;
import horus.adapter.source.worldcheck.narrative.ListSources;
import horus.adapter.source.worldcheck.narrative.Section;
import horus.domain.shared.Provenance;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * UK OFSI / HM Treasury consolidated list format.
 *
 * Label vocabulary observed directly in the feed:
 *   Designation source: UK
 *   Sex: M
 *   Individual, Entity, Ship: Individual
 *   Financial sanctions imposed in addition to an asset freeze: Trust services
 *   Date trust services sanctions imposed: 21/03/2023
 *   UK Statement of Reasons: ...
 *   Group ID: 7419
 *   Other Information: Listed by UK only on 2 November 2001
 *
 * Every one of those appeared as a false "alias" in the first measurement pass.
 * They are field headings; knowing them is what makes name extraction safe here.
 */
public final class OfsiSourceParser extends AbstractSourceParser {

    @Override public String parserId() { return "OFSI"; }

    @Override
    public boolean handles(String h) {
        if (h == null) return false;
        return h.contains("UKHMT") || h.contains("OFSI")
                || h.startsWith("UK SANCTIONS")
                || h.contains("ISLE OF MAN") || h.contains("GUERNSEY")
                || h.contains("JERSEY");
    }

    private static final String[] LABELS = {
        "Designation source", "Individual, Entity, Ship", "Sex",
        "Financial sanctions imposed", "Date trust services sanctions imposed",
        "UK Statement of Reasons", "Statement of Reasons", "Group ID",
        "Regime", "Date designated", "Date of Listing", "Last Updated",
        "Type of entity", "Parent company", "Subsidiaries", "Business registration"
    };

    @Override protected String[] labels() { return LABELS; }

    /** OFSI numbers name variants: "Name 6: SMITH". */
    private static final Pattern NAME_N = Pattern.compile("\\bName\\s+\\d+\\s*:\\s*");
    private static final Pattern GROUP_ID = Pattern.compile(
        "Group\\s+ID\\s*:\\s*(\\d{1,10})", Pattern.CASE_INSENSITIVE);
    private static final Pattern REGIME = Pattern.compile(
        "Regime\\s*:\\s*([A-Za-z ()/,-]{3,60})");

    @Override
    public void parse(Section section, Facts.Builder out) {
        String body = section.body();
        int offset = section.bodyStart();
        String source = ListSources.canonical(section.header());

        extractEvents(section, out);
        extractNames(section, out);
        IdentifierParser.parse(body, offset, source, out);

        Matcher g = GROUP_ID.matcher(body);
        if (g.find()) {
            out.identifier(new Facts.Identifier("GROUP_ID", g.group(1), source,
                    new Provenance("OFSI.GROUP_ID", offset + g.start(1), offset + g.end(1))));
        }

        Matcher r = REGIME.matcher(body);
        if (r.find()) {
            out.identifier(new Facts.Identifier("REGIME", r.group(1).trim(), source,
                    new Provenance("OFSI.REGIME", offset + r.start(1), offset + r.end(1))));
        }

        // "Name 6: X" numbered variants, which the generic markers miss entirely
        Matcher n = NAME_N.matcher(body);
        int emitted = 0;
        while (n.find() && emitted < 20) {
            int start = n.end();
            int stop = Math.min(body.length(), start + 160);
            String cand = cutAtLabel(firstSegment(body.substring(start, stop)));
            if (plausibleName(cand)) {
                out.name(new Facts.Name(cand, "AKA", parserId(),
                        new Provenance("OFSI.NAME_N", offset + start, offset + stop)));
                emitted++;
            }
        }
    }

    private static String firstSegment(String s) {
        int i = s.indexOf('\n');
        int j = s.indexOf(". ");
        int cut = s.length();
        if (i >= 0) cut = Math.min(cut, i);
        if (j >= 0) cut = Math.min(cut, j);
        return s.substring(0, cut);
    }
}
