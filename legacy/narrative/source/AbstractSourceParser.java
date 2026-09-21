package horus.narrative.source;

import horus.narrative.ActionVocabulary;
import horus.narrative.Facts;
import horus.narrative.ListSources;
import horus.narrative.Provenance;
import horus.narrative.Section;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared machinery for source parsers: the "Mon YYYY - action" grammar, and a
 * name extractor that is told which labels its authority uses.
 *
 * The label list is the whole point of the per-source split. Given OFSI's
 * labels, "Designation source: UK" is a field, not an alias. Given OFAC's,
 * "c/o INMOBILIARIA U.M.V" is an address, not a company. A generic extractor
 * has no way to know either.
 */
abstract class AbstractSourceParser implements SourceParser {

    /** Mon YYYY - verb phrase. Dominant across the feed. */
    protected static final Pattern DATE_ACTION = Pattern.compile(
        "\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+(\\d{4})\\s*[-\u2013]\\s*"
        + "([A-Za-z][A-Za-z /'-]{1,60})");

    protected static final Pattern YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");

    /** Labels shared by every authority. Subclasses add their own. */
    private static final String[] COMMON_LABELS = {
        "DOB", "D.O.B", "POB", "P.O.B", "Date of birth", "Date of Birth",
        "Place of birth", "Place of Birth", "Nationality", "nationality",
        "Citizenship", "Gender", "Sex:", "Sex ", "Address", "Addr",
        "Other Information", "Other information", "Position", "Function",
        "Title:", "Justification", "Listed on", "Type of entity",
        "Passport", "National ID", "Tax ID",
        // v3: observed leaking into extracted_names.tsv
        "Financial sanctions", "Statement of Reasons", "Reasons:",
        "Designation", "Secondary sanctions", "Federal Register",
        "Identity Card", "Cedula", "D.N.I.", "Spelling variant",
        "Good quality", "Low quality", "Date trust services",
        "Individual, Entity", "Regime:", "Group ID", "Ref No"
    };

    /**
     * v3. A candidate beginning with one of these is a job title, a role
     * description or attribute data -- never a name.
     *
     * Ported from the profiler, where it worked, and omitted from v2 by mistake.
     * The consequence was visible in extracted_names.tsv: "Deputy Minister of
     * Culture", "activist", "Member of the State Duma of the Federal Assembly"
     * were all emitted as names.
     */
    private static final String[] NOT_A_NAME_PREFIX = {
        "dob", "d.o.b", "pob", "p.o.b", "nationality", "citizenship", "passport",
        "date of birth", "place of birth", "gender", "sex", "male", "female",
        "group id", "other information", "justification", "national id",
        "tax id", "address", "addr", "reg no", "registration", "town of birth",
        "country of birth", "cedula", "identity card", "d.n.i",
        // roles and relationships
        "former", "minister", "deputy", "director", "adviser", "advisor",
        "member of", "member ", "head of", "chairman", "chairperson",
        "president of", "governor", "ambassador", "secretary general",
        "secretary of", "general secretary", "commander", "chief of",
        "spouse of", "husband of", "wife of", "son of", "daughter of",
        "brother of", "sister of", "father of", "mother of", "associated with",
        "activist", "official", "employee", "representative",
        // sanctions boilerplate
        "listed", "subject to", "designated", "financial sanctions",
        "secondary sanctions", "statement of reasons", "reasons",
        "date trust", "individual, entity", "designation source",
        "federal register", "spelling variant", "review", "belongs to",
        "unknown", "none", "n/a", "position", "function", "c/o",
        // v4: "Additional Sanctions Information - Subject to Secondary
        // Sanctions" is an OFAC label. Truncated to "Additional Sanctio" it has
        // the shape of a name -- capitalised, two tokens, no digits -- so no
        // structural rule can reject it. The prefix is the only handle.
        "additional"
    };

    /** Authority-specific field labels. Never treated as names. */
    protected abstract String[] labels();

    /** Marker introducing the entity's principal name in this format. */
    protected String primaryMarker() { return "PRIMARY NAME:"; }

    /** Markers introducing aliases in this format. */
    protected String[] aliasMarkers() { return new String[]{"a.k.a.", "a.k.a:", "Alias:"}; }

    // ------------------------------------------------------------------ events
    /**
     * Extracts (source, action, year) triples. Called by every subclass; the
     * grammar is identical across authorities even though the field layout is not.
     */
    protected void extractEvents(Section s, Facts.Builder out) {
        String source = ListSources.canonical(s.header());
        Matcher m = DATE_ACTION.matcher(s.body());
        int hits = 0;
        while (m.find() && hits < 100) {
            String raw = normaliseVerb(m.group(3));
            if (raw.isEmpty() || ActionVocabulary.isMonth(raw)) continue;   // date range
            ActionVocabulary.Classification c = ActionVocabulary.classify(raw);
            int year = Integer.parseInt(m.group(2));
            Provenance p = new Provenance(parserId() + "." + c.ruleId(),
                    s.bodyStart() + m.start(), s.bodyStart() + m.end());

            out.event(new Facts.SanctionEvent(source, c.type(), raw, year, p));
            if (c.type() == ActionVocabulary.ActionType.UNCLASSIFIED) {
                out.unparsed(new Facts.Unparsed(s.header(), "UNCLASSIFIED_ACTION_VERB",
                        raw, p));
            }
            hits++;
        }
    }

    // ------------------------------------------------------------------- names
    /**
     * Extracts names, cutting each candidate at the first label this authority
     * is known to use and rejecting anything that looks like field data.
     */
    protected void extractNames(Section s, Facts.Builder out) {
        String body = s.body();
        emit(body, s, primaryMarker(), "PRIMARY", out);
        for (String mk : aliasMarkers()) emit(body, s, mk, "AKA", out);
    }

    private void emit(String body, Section s, String marker, String nameType,
                      Facts.Builder out) {
        int from = 0, at, emitted = 0;
        while ((at = body.indexOf(marker, from)) >= 0 && emitted < 40) {
            int start = at + marker.length();
            int stop = Math.min(body.length(), start + 300);

            // v3: CUT FIRST, THEN SPLIT. v2 split the raw segment and cut each
            // piece afterwards, so a label straddling a split boundary was
            // truncated mid-word -- which is how "Financial sanctions imp"
            // reached the output as a name.
            String segment = cutAtLabel(body.substring(start, stop));

            for (String piece : splitNames(segment)) {
                String cand = trimPunct(piece.trim());
                if (!plausibleName(cand)) continue;
                out.name(new Facts.Name(cand, nameType, parserId(),
                        new Provenance(parserId() + ".NAME." + nameType,
                                s.bodyStart() + start, s.bodyStart() + stop)));
                emitted++;
            }
            from = at + marker.length();
        }
    }

    private static final Pattern NAME_SPLIT = Pattern.compile(
        "(?i)\\s*(?:;|\\ba\\.k\\.a\\.?\\s*:?|\\bAKA\\s*:|\\bAlias(?:es)?\\s*:"
        + "|\\n|\\.\\s+(?=[A-Z])|\\b[a-z]\\)\\s)\\s*");

    protected String[] splitNames(String segment) {
        return NAME_SPLIT.split(segment);
    }

    /** Truncates at the first known label: everything after it is field data. */
    protected String cutAtLabel(String raw) {
        String t = raw == null ? "" : raw.trim();
        int cut = t.length();
        for (String k : COMMON_LABELS) { int i = t.indexOf(k); if (i >= 0 && i < cut) cut = i; }
        for (String k : labels())      { int i = t.indexOf(k); if (i >= 0 && i < cut) cut = i; }
        t = t.substring(0, cut).trim();
        return trimPunct(t);
    }

    protected static String trimPunct(String in) {
        String t = in;
        while (!t.isEmpty()) {
            char c = t.charAt(0);
            if (c == '\'' || c == '"' || c == '(' || c == '.' || c == ':' || c == ',')
                t = t.substring(1).trim();
            else break;
        }
        while (!t.isEmpty()) {
            char c = t.charAt(t.length() - 1);
            if (c == '\'' || c == '"' || c == ')' || c == '.' || c == ','
                    || c == ';' || c == '-' || c == ':')
                t = t.substring(0, t.length() - 1).trim();
            else break;
        }
        return t.length() > 120 ? t.substring(0, 120) : t;
    }

    /**
     * Structural test only. No statistics, no model -- I-3 and I-4.
     *
     * Deliberately strict. A rejected real name costs one alias out of several
     * on the same record; an accepted job title pollutes the name index for
     * every query. Rejections are recoverable, pollution is not.
     */
    protected boolean plausibleName(String c) {
        if (c == null || c.length() < 3) return false;
        if (YEAR.matcher(c).find()) return false;          // a date, not a name
        if (c.indexOf(':') >= 0) return false;             // residual label

        String lc = c.toLowerCase();
        for (String p : NOT_A_NAME_PREFIX) if (lc.startsWith(p)) return false;

        // v3: fragments left by a split inside a quoted phrase, e.g.
        // "s Republic and the Luhansk People" from "People's Republic".
        if (c.length() > 1 && Character.isLowerCase(c.charAt(0))
                && c.indexOf(' ') > 0) return false;
        if (lc.startsWith("and ") || lc.startsWith("the ")
                || lc.startsWith("of ") || lc.startsWith("or ")) return false;

        int letters = 0, digits = 0, words = 1;
        for (int i = 0; i < c.length(); i++) {
            char ch = c.charAt(i);
            if (Character.isLetter(ch)) letters++;
            else if (Character.isDigit(ch)) digits++;
            else if (ch == ' ') words++;
        }
        if (letters < 3 || words > 12) return false;
        if (digits > letters / 4) return false;            // reference numbers

        // v4: truncation fragments left by cutAtLabel cutting mid-token --
        // "A com", "Add", "Additional Sanctio". A single token under four
        // characters is not a name worth indexing, and a two-token candidate
        // whose last token is a short lowercase stub is a cut word.
        // KNOWN TRADE-OFF, deliberate and tunable.
        //
        // This rejects single tokens under four characters. It clears truncation
        // stubs ("Add", "A com") but also drops genuine three-letter aliases
        // such as "ALI", which appeared in the v3 output.
        //
        // Kept because a lone three-character token has almost no
        // distinctiveness: spec 15.2 warns that partial and placeholder names
        // must not be allowed to match everything, and worked example A10 makes
        // the same point. Indexing it would raise alerts against a large share
        // of queries while adding one alias to records that already carry
        // others.
        //
        // If Compliance would rather carry the alert volume, drop the floor to 3
        // and rely on BR-12 minimumNameLength to tighten matching downstream.
        // The reverse -- silently discarding a name at extraction time -- cannot
        // be recovered later, so this is the one rule to revisit first if a
        // recall gap shows up in the benchmark.
        if (words == 1 && c.length() < 4) return false;
        int lastSpace = c.lastIndexOf(' ');
        if (lastSpace > 0) {
            String tail = c.substring(lastSpace + 1);
            // "A com" has tail "com": length 3, so the previous "< 3" test
            // missed it by one. Four is safe -- a real name rarely ends in a
            // lowercase token of three characters or fewer.
            if (tail.length() < 4 && Character.isLowerCase(tail.charAt(0)))
                return false;
        }

        // v3: an address tail -- "S.A., Cali, Colombia"
        int commas = 0;
        for (int i = 0; i < c.length(); i++) if (c.charAt(i) == ',') commas++;
        return commas <= 1;
    }

    protected static String normaliseVerb(String v) {
        String s = v.trim().toLowerCase();
        int stop = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.' || c == ',' || c == '(' || c == ')') { stop = i; break; }
        }
        s = s.substring(0, stop).trim();
        return s.length() > 60 ? s.substring(0, 60) : s;
    }
}
