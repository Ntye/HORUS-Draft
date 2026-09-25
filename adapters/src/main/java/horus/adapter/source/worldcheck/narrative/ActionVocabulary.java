package horus.adapter.source.worldcheck.narrative;

import horus.domain.watchentity.ActionType;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Classifies the verb phrase following "Mon YYYY -" in a sanctions section.
 *
 * The vocabulary below was derived by MEASUREMENT, not assumption: a strided
 * sample over all 5,990,394 records produced 858 distinct formal verb forms, of
 * which the sanctions-lifecycle set accounts for the large majority of
 * occurrences once regulator-warning sections are excluded (I-13).
 *
 * Anything unmatched returns UNCLASSIFIED and is quarantined by the caller. It
 * is never guessed at and never silently dropped -- an unrecognised verb that
 * quietly became REMOVAL would be a silent recall failure.
 */
public final class ActionVocabulary {

    private ActionVocabulary() {}

    /** Ordered: first match wins, so more specific phrases come first. */
    private static final Map<String, ActionType> RULES = new LinkedHashMap<>();
    static {
        // --- authorisation FIRST. "ofac issued general license" contains
        // "issued", which would otherwise match an ADDITION rule; and
        // "non-renewal of general licence" contains "renewal", which would
        // otherwise match AMENDMENT.
        put(ActionType.AUTHORISATION,
            "general license", "general licence", "gl issued", "int/",
            "authorizing", "authorising", "authorisation issued",
            "authorization issued", "licence issued", "license issued",
            "non-renewal of general", "specific licence", "specific license",
            "wind-down authorisation", "wind-down authorization");

        // --- removal (checked first among lifecycle verbs: "no longer appears"
        //     contains "appears", which is an ADDITION phrase)
        put(ActionType.REMOVAL,
            "no longer appears", "no longer subject", "list retired", "removed",
            "delisted", "de-listed", "deleted", "revoked", "rescinded",
            "withdrawn", "lifted", "cancelled", "canceled", "struck off",
            "terminated", "expired", "ceased", "annulled", "repealed",
            "suspension lifted", "notice cancelled", "entry removed",
            "no longer designated", "no longer listed",
            // v3: observed in unclassified_verbs.tsv
            "no longer applies", "no longer applicable", "not in effect",
            "no longer in effect", "no longer named", "list officially withdrawn");

        // --- amendment
        put(ActionType.AMENDMENT,
            "statement amended", "amended", "updated", "corrected", "modified",
            "renewed", "extended", "replaced", "transferred to", "reinstated",
            "re-listed", "relisted", "administrative list update",
            "issues update", "issued update", "consolidated",
            // v3: observed in unclassified_verbs.tsv
            "list officially confirmed", "issues further guidance",
            "further guidance", "officially confirmed");

        // --- addition
        put(ActionType.ADDITION,
            "addition", "added", "designated", "listed on", "listed by",
            "appears on", "appears in", "entry added", "inclusion", "included",
            "red notice issued", "notice issued", "asset freeze imposed",
            "sanctions imposed", "listed",
            // v3: observed in unclassified_verbs.tsv. "named on the ... list"
            // is how several jurisdictions record an unofficial designation.
            "named on the official", "named on the unofficial",
            "named on the version", "named on the",
            "restrictive measures", "special economic measures",
            // v4: "assets frozen for a further period" is a designation event,
            // not a licence. Ordered after AUTHORISATION so a licence extending
            // a freeze is not miscounted here.
            "assets frozen", "asset freeze", "implemented unscr", "unscr",
            "imposed");

        // --- regulator warnings: recognised, but never a designation
        put(ActionType.WARNING,
            "investors cautioned", "barred from", "censured", "fined",
            "suspended for", "public administrative proceedings",
            "without admitting", "deemed in default", "initial decision",
            "agreed to the entry", "registration of each class",
            "unauthorised", "unauthorized", "unlicensed", "disqualified");
    }

    private static void put(ActionType t, String... phrases) {
        for (String p : phrases) RULES.put(p, t);
    }

    /** Rule id recorded in Provenance so the classification is reconstructible. */
    public record Classification(ActionType type, String matchedPhrase, String ruleId) {}

    public static Classification classify(String rawVerb) {
        if (rawVerb == null) return unclassified();
        String v = rawVerb.toLowerCase().trim();
        if (v.isEmpty() || isMonth(v)) return unclassified();
        for (Map.Entry<String, ActionType> e : RULES.entrySet()) {
            if (v.contains(e.getKey())) {
                return new Classification(e.getValue(), e.getKey(),
                        "ACTION." + e.getValue() + "." + e.getKey().replace(' ', '_'));
            }
        }
        return unclassified();
    }

    private static Classification unclassified() {
        return new Classification(ActionType.UNCLASSIFIED, null, "ACTION.UNCLASSIFIED");
    }

    private static final List<String> MONTHS = Arrays.asList(
        "jan", "feb", "mar", "apr", "may", "jun",
        "jul", "aug", "sep", "oct", "nov", "dec");

    /** "Nov 2001 - Sep 2015" is a range, not a verb. */
    public static boolean isMonth(String v) {
        return MONTHS.contains(v.length() > 3 ? v.substring(0, 3) : v);
    }

    public static int ruleCount() { return RULES.size(); }
}
