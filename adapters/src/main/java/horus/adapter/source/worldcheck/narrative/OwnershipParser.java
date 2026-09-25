package horus.adapter.source.worldcheck.narrative;

import horus.domain.shared.Provenance;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ownership and control sections.
 *
 * Measurement: DIRECT SHAREHOLDER/S appears on 1.23% of records (~74,000
 * extrapolated) and 89% of those carry an explicit percentage. Observed shape:
 *
 *   Ministry of Finance (50%). Sveriges Kommuner och Regioner (IOS) (50%).
 *   Ministry of Economy and Finance of Uzbekistan (51,7%). Uzbekneftegas (SOE) (46,78%).
 *   Government of Libya (unknown percentage).
 *
 * Owner name, optional type marker, percentage or the explicit token
 * "unknown percentage", separated by ". ".
 *
 * This is the OFAC 50 Percent Rule input. linked_to/uid resolves at 100% but
 * carries no role, type or percentage, so the graph has edges without meaning;
 * the semantics are here instead.
 */
public final class OwnershipParser {

    private OwnershipParser() {}

    /** name ... optional (TYPE) ... (NN.NN%) | (unknown percentage) */
    private static final Pattern STAKE = Pattern.compile(
        "([^.;()]{2,120}?)\\s*"
        + "(?:\\((SOE|IOS|SOB|POE)\\)\\s*)?"
        + "\\(\\s*(?:(\\d{1,3}(?:[.,]\\d{1,4})?)\\s*%|(unknown\\s+percentage))\\s*\\)",
        Pattern.CASE_INSENSITIVE);

    /** A named party with no percentage clause at all. */
    private static final Pattern BARE = Pattern.compile(
        "([A-Z][^.;()]{3,120})\\s*(?:\\((SOE|IOS|SOB|POE)\\))?\\s*(?:\\.|$)");

    public static void parse(Section s, Facts.Builder out) {
        String body = s.body();
        if (body == null || body.isBlank()) return;
        int offset = s.bodyStart();
        String relation = s.header();

        Matcher m = STAKE.matcher(body);
        int found = 0, lastEnd = 0;
        while (m.find() && found < 40) {
            String owner = clean(m.group(1));
            if (owner.isEmpty()) continue;
            String type = m.group(2) == null ? null : m.group(2).toUpperCase();
            Double pct = null;
            boolean stated = false;
            if (m.group(3) != null) {
                pct = parsePercent(m.group(3));
                stated = pct != null;
            }
            out.stake(new Facts.OwnershipStake(owner, type, pct, stated, relation,
                    new Provenance("OWN.STAKE", offset + m.start(), offset + m.end())));
            found++;
            lastEnd = m.end();
        }

        if (found == 0) {
            Matcher b = BARE.matcher(body);
            if (b.find()) {
                String owner = clean(b.group(1));
                if (!owner.isEmpty()) {
                    out.stake(new Facts.OwnershipStake(owner,
                            b.group(2) == null ? null : b.group(2).toUpperCase(),
                            null, false, relation,
                            new Provenance("OWN.BARE", offset + b.start(), offset + b.end())));
                    return;
                }
            }
            if (!body.isBlank()) {
                out.unparsed(new Facts.Unparsed(relation, "OWNERSHIP_SHAPE_UNRECOGNISED",
                        excerpt(body), new Provenance("OWN.UNPARSED", offset,
                                offset + Math.min(body.length(), 200))));
            }
        } else if (body.length() - lastEnd > 40) {
            // Trailing content the stake pattern did not consume.
            out.unparsed(new Facts.Unparsed(relation, "OWNERSHIP_TRAILING_CONTENT",
                    excerpt(body.substring(lastEnd)),
                    new Provenance("OWN.TRAILING", offset + lastEnd, offset + body.length())));
        }
    }

    /** Handles both "51.7" and the European "51,7". */
    static Double parsePercent(String raw) {
        try {
            double v = Double.parseDouble(raw.replace(',', '.'));
            return (v < 0.0 || v > 100.0) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String clean(String s) {
        String t = s.trim();
        while (!t.isEmpty() && (t.charAt(0) == '.' || t.charAt(0) == ','
                || t.charAt(0) == ';' || t.charAt(0) == '-')) t = t.substring(1).trim();
        while (!t.isEmpty()) {
            char c = t.charAt(t.length() - 1);
            if (c == '.' || c == ',' || c == ';' || c == '-' || c == ':')
                t = t.substring(0, t.length() - 1).trim();
            else break;
        }
        // "and Ministry of X" -> "Ministry of X"
        if (t.regionMatches(true, 0, "and ", 0, 4)) t = t.substring(4).trim();
        return t;
    }

    private static String excerpt(String s) {
        String t = s.replace('\t', ' ').replace('\n', ' ').trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }
}
