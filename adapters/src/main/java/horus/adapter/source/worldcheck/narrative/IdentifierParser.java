package horus.adapter.source.worldcheck.narrative;

import horus.domain.shared.Provenance;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls structured identifiers out of narrative text.
 *
 * The structured schema carries passport only, populated on 0.74% of records
 * and effectively never for organisations. Tax ID, LEI, SWIFT BIC, IMO and the
 * various registry references exist ONLY here. For the 840,617 CORPORATE
 * records this is the difference between name-only matching and having
 * something for BR-09 to short-circuit on.
 */
public final class IdentifierParser {

    private IdentifierParser() {}

    private record Rule(String type, String ruleId, Pattern pattern) {}

    private static final Rule[] RULES = {
        // LEI is a strict 20-char alphanumeric; check before generic refs.
        new Rule("LEI", "ID.LEI",
            Pattern.compile("\\bLEI[:\\s#]*([A-Z0-9]{20})\\b")),
        new Rule("SWIFT_BIC", "ID.SWIFT_BIC",
            Pattern.compile("\\b(?:SWIFT|BIC)(?:\\s+(?:BIC|CODE))?[:\\s#]*"
                          + "([A-Z]{4}[A-Z]{2}[A-Z0-9]{2}(?:[A-Z0-9]{3})?)\\b")),
        new Rule("IMO", "ID.IMO",
            Pattern.compile("\\bIMO(?:\\s+(?:No|Number|REGISTRATION))?[:.\\s#]*(\\d{7})\\b")),
        new Rule("TAX_ID", "ID.TAX_ID",
            Pattern.compile("\\bTax\\s+ID(?:\\s+No)?[:.\\s#]*([A-Z0-9\\-/]{4,20})\\b",
                            Pattern.CASE_INSENSITIVE)),
        new Rule("GROUP_ID", "ID.GROUP_ID",
            Pattern.compile("\\bGroup\\s+ID[:.\\s#]*(\\d{1,10})\\b",
                            Pattern.CASE_INSENSITIVE)),
        new Rule("SDN_REF", "ID.SDN_REF",
            Pattern.compile("\\bSDN\\s+Ref\\s+No[:.\\s#]*(\\d{1,10})\\b",
                            Pattern.CASE_INSENSITIVE)),
        new Rule("REF_NO", "ID.REF_NO",
            Pattern.compile("\\bRef\\s+No[:.\\s#]*([A-Z0-9\\-/]{1,20})\\b",
                            Pattern.CASE_INSENSITIVE)),
        new Rule("REGISTRATION", "ID.REGISTRATION",
            Pattern.compile("\\b(?:Registration|Reg)\\s+No[:.\\s#]*([A-Z0-9\\-/]{3,25})\\b",
                            Pattern.CASE_INSENSITIVE)),
        new Rule("NATIONAL_ID", "ID.NATIONAL_ID",
            Pattern.compile("\\bNational\\s+ID(?:\\s+No)?[:.\\s#]*([A-Z0-9\\-/.]{4,25})\\b",
                            Pattern.CASE_INSENSITIVE)),
        new Rule("PASSPORT", "ID.PASSPORT",
            Pattern.compile("\\bPassport\\s+No[:.\\s#]*([A-Z0-9\\-]{4,20})\\b",
                            Pattern.CASE_INSENSITIVE)),
        // OFAC / Latin American national identity numbers
        new Rule("CEDULA", "ID.CEDULA",
            Pattern.compile("\\b(?:Cedula|C\\.?I\\.?|D\\.?N\\.?I\\.?)\\s*(?:No\\.?)?[:\\s#]*"
                          + "([0-9][0-9.\\-]{5,20})", Pattern.CASE_INSENSITIVE))
    };

    /**
     * @param text     section body
     * @param offset   section start within the full narrative, for provenance
     * @param context  list source the identifier was stated under, may be null
     */
    public static void parse(String text, int offset, String context, Facts.Builder out) {
        if (text == null || text.isEmpty()) return;
        for (Rule r : RULES) {
            Matcher m = r.pattern().matcher(text);
            int hits = 0;
            while (m.find() && hits < 20) {
                String v = m.group(1).trim();
                if (v.isEmpty()) continue;
                out.identifier(new Facts.Identifier(r.type(), v, context,
                        new Provenance(r.ruleId(), offset + m.start(1), offset + m.end(1))));
                hits++;
            }
        }
    }

    public static int ruleCount() { return RULES.length; }
}
