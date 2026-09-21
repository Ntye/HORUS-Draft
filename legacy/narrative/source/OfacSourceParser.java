package horus.narrative.source;

import horus.narrative.Facts;
import horus.narrative.IdentifierParser;
import horus.narrative.ListSources;
import horus.narrative.Provenance;
import horus.narrative.Section;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * US OFAC SDN / Consolidated list format.
 *
 * Conventions observed in the feed:
 *   SDN Ref No 4712 - FTO (Foreign Terrorist Organisation) (Aug 1997 - addition)
 *   PRIMARY NAME: X (a.k.a. Y; a.k.a. Z)
 *   ANDRADE QUINTERO, Ancizar, c/o INMOBILIARIA BOLIVAR LTDA., Cali, Colombia
 *   Cedula No. 16672464 (Colombia)
 *   Secondary sanctions risk: See Section 11 of Executive Order 14024
 *   Federal Register Notice: 61 FR 29784
 *
 * Two things matter here. Aliases are parenthesised and semicolon-separated
 * rather than marker-prefixed, so they need their own pattern. And "c/o" begins
 * an address: everything from there on is location, not name.
 */
public final class OfacSourceParser extends AbstractSourceParser {

    @Override public String parserId() { return "OFAC"; }

    @Override
    public boolean handles(String h) {
        if (h == null) return false;
        return h.contains("OFAC") || h.contains("SDN")
                || h.startsWith("USA SANCTIONS") || h.contains("BIS")
                || h.contains("ENTITY LIST");
    }

    private static final String[] LABELS = {
        "SDN Ref No", "Secondary sanctions risk", "Federal Register Notice",
        "Executive Order", "Program:", "Linked To", "c/o", "Cedula No",
        "D.N.I.", "Identity Card No", "Digital Currency Address",
        "Vessel Registration Identification", "Former Vessel Flag",
        "Organization Established Date", "Organization Type"
    };

    @Override protected String[] labels() { return LABELS; }

    /** "(a.k.a. X; a.k.a. Y; f.k.a. Z)" */
    private static final Pattern PAREN_AKA = Pattern.compile(
        "\\((?:a\\.k\\.a\\.|f\\.k\\.a\\.|n\\.k\\.a\\.)\\s*([^)]{3,400})\\)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern AKA_SPLIT = Pattern.compile(
        "(?i)\\s*;\\s*(?:a\\.k\\.a\\.|f\\.k\\.a\\.|n\\.k\\.a\\.)?\\s*");
    private static final Pattern SDN_REF = Pattern.compile(
        "SDN\\s+Ref\\s+No\\.?\\s*(\\d{1,10})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROGRAMME = Pattern.compile(
        "\\b(SDGT|SDNT|SDNTK|FTO|NPWMD|IRGC|CYBER2|UKRAINE-EO\\d+|"
        + "RUSSIA-EO\\d+|SYRIA|IRAN|DPRK\\d?)\\b");

    @Override
    public void parse(Section section, Facts.Builder out) {
        String body = section.body();
        int offset = section.bodyStart();
        String source = ListSources.canonical(section.header());

        extractEvents(section, out);
        extractNames(section, out);
        IdentifierParser.parse(body, offset, source, out);

        Matcher ref = SDN_REF.matcher(body);
        if (ref.find()) {
            out.identifier(new Facts.Identifier("SDN_REF", ref.group(1), source,
                    new Provenance("OFAC.SDN_REF", offset + ref.start(1), offset + ref.end(1))));
        }

        Matcher prog = PROGRAMME.matcher(body);
        int progs = 0;
        while (prog.find() && progs < 10) {
            out.identifier(new Facts.Identifier("OFAC_PROGRAMME", prog.group(1), source,
                    new Provenance("OFAC.PROGRAMME", offset + prog.start(1),
                            offset + prog.end(1))));
            progs++;
        }

        // parenthesised alias runs -- OFAC's actual alias convention
        Matcher m = PAREN_AKA.matcher(body);
        int emitted = 0;
        while (m.find() && emitted < 40) {
            for (String piece : AKA_SPLIT.split(m.group(1))) {
                String cand = cutAtLabel(stripAkaPrefix(piece));
                if (!plausibleName(cand)) continue;
                out.name(new Facts.Name(cand, "AKA", parserId(),
                        new Provenance("OFAC.PAREN_AKA",
                                offset + m.start(1), offset + m.end(1))));
                emitted++;
            }
        }
    }

    /** OFAC marks weak aliases; they must not be scored as strong ones. */
    private static String stripAkaPrefix(String s) {
        String t = s.trim();
        for (String p : new String[]{"a.k.a.", "f.k.a.", "n.k.a.", "a.k.a", "aka"}) {
            if (t.regionMatches(true, 0, p, 0, p.length())) {
                t = t.substring(p.length()).trim();
            }
        }
        return trimPunct(t);
    }
}
