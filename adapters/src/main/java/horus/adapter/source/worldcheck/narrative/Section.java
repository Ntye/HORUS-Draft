package horus.adapter.source.worldcheck.narrative;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One bracketed section of further_information, plus the splitter.
 *
 * The formal register ([USA SANCTIONS - OFAC], [SANCTIONS HISTORY],
 * [DIRECT SHAREHOLDER/S]) is templated and parseable. The prose register
 * ([BIOGRAPHY], [REPORTS], [FUNDING]) is genuine natural language and is never
 * parsed for facts -- measurement showed 14,308 distinct verb forms there
 * against a bounded set in sanctions sections.
 */
public record Section(String header, String body, int bodyStart, SectionKind kind) {

    public static final int MAX_HEADER_LEN = 64;


    private static final Set<String> PROSE = new HashSet<>(Arrays.asList(
        "BIOGRAPHY", "REPORTS", "IDENTIFICATION", "FUNDING", "ADDITIONAL NOTE",
        "ADDITIONAL COMMENTS", "COMMENTS", "NOTE", "PROFILE NOTES",
        // v3: measured in quarantine.tsv. Free commentary, not facts.
        "KEYWORD NOTE", "OFAC NOTE", "UK NOTE", "EU NOTE", "UN REPORTS",
        "CRIME - TERROR CATEGORY NOTICE", "FATF STATEMENT",
        "USA - OFAC BROCHURES AND NOTICES",
        // v4: a risk keyword marker, not a section carrying facts.
        // 296 occurrences, previously the largest remaining quarantine entry.
        "FENTANYL", "OECD / FATF"));

    /**
     * v3: widened from measurement. The previous set required the header to
     * contain SANCTIONS, EMBARGO or REGULATIONS, so "EU RESTRICTIVE MEASURES",
     * "UK INVESTMENT BAN - UKHMT-IB" and bare jurisdiction headers fell through
     * to UNKNOWN and contributed NO status events at all. That made the
     * de-listing figure a floor rather than a measurement.
     */
    private static final String[] SANCTIONS_HINTS = {
        "SANCTIONS", "EMBARGO", "REGULATIONS", "DESIGNATION", "ASSET FREEZE",
        "RESTRICTIVE MEASURES", "INVESTMENT BAN", "FINANCIAL RESTRICTIONS",
        "DUAL-USE", "DUAL USE", "SPECIFIED ENTITIES", "SPECIAL ECONOMIC MEASURES",
        "HIGH RISK THIRD COUNTRIES", "CONTROVERSIAL WEAPONS", "NATIONAL LISTS"};

    /**
     * Bare jurisdiction headers ("USA", "UK", "CANADA", "UN", "UKTR",
     * "USA TREASURY", "US DEPARTMENT OF STATE") carry designation events with no
     * qualifying word at all. Matched exactly, never by substring: a substring
     * rule on "UK" or "UN" would swallow half the vocabulary.
     */
    private static final Set<String> BARE_JURISDICTIONS = new HashSet<>(Arrays.asList(
        "USA", "UK", "EU", "UN", "CANADA", "AUSTRALIA", "SWITZERLAND", "JAPAN",
        "UKTR", "USA TREASURY", "US DEPARTMENT OF STATE", "US DEPARTMENT OF COMMERCE",
        "HONG KONG", "SINGAPORE", "NEW ZEALAND", "NORWAY", "ISLE OF MAN"));

    private static final String[] OWNERSHIP_HINTS = {
        "SHAREHOLDER", "OWNED", "OWNERSHIP", "STATE INVESTED", "INSTRUMENTALITY"};
    private static final String[] WARNING_HINTS = {
        "WARNING", "ALERT", "UNAUTHORISED", "UNAUTHORIZED", "UNLICENSED",
        "FINANCIAL SERVICES", "SEC", "FINRA", "CFTC", "INVESTOR", "NFA",
        "CIVIL PENALTIES"};
    private static final String[] ENFORCEMENT_HINTS = {
        "INTERPOL", "WANTED", "DEBARMENT", "DEBARRED", "ENFORCEMENT",
        "LAW ENFORCEMENT", "CONVICTION", "INDICTMENT", "ENTITY LIST",
        "DISQUALIFIED DIRECTORS"};

    /**
     * v3: sections whose whole content is an identifier or a status token.
     * IMO REGISTRATION (287 occurrences) and SWIFT BIC CODE were previously
     * quarantined and their identifiers discarded.
     */
    private static final String[] IDENTIFIER_HINTS = {
        "IMO REGISTRATION", "MSN REGISTRATION", "SWIFT BIC", "UN/LOCODE",
        "REGISTRATION NUMBER", "CALL SIGN"};

    /**
     * v3: vessel and aircraft operating status. Neither a designation nor an
     * identifier, but a real risk signal for VESSEL and AIRCRAFT screening.
     */
    private static final String[] ASSET_STATUS_HINTS = {
        "OPERATIONAL", "DETAINED VESSEL", "SPECIAL INTEREST VESSEL",
        "SPECIAL INTEREST AIRCRAFT", "SCRAPPED", "BROKEN UP"};

    /**
     * Splits a narrative into sections. Text before the first header is returned
     * with a null header. Bracketed tokens that are not all-caps are treated as
     * prose punctuation, not headers.
     */
    public static List<Section> split(String text) {
        List<Section> out = new ArrayList<>(16);
        if (text == null || text.isEmpty()) return out;

        int i = 0, lastEnd = 0;
        String lastHeader = null;
        while ((i = text.indexOf('[', i)) >= 0) {
            int close = text.indexOf(']', i + 1);
            if (close < 0) break;
            String h = (close - i - 1 <= MAX_HEADER_LEN)
                    ? text.substring(i + 1, close).trim() : null;
            if (h != null && isHeader(h)) {
                if (i > lastEnd || lastHeader != null)
                    out.add(make(lastHeader, text, lastEnd, i));
                lastHeader = h;
                lastEnd = close + 1;
            }
            i = close + 1;
        }
        if (lastEnd <= text.length()) out.add(make(lastHeader, text, lastEnd, text.length()));
        return out;
    }

    private static Section make(String header, String text, int from, int to) {
        String body = text.substring(Math.min(from, text.length()), Math.min(to, text.length()));
        return new Section(header, body, from, classify(header));
    }

    public static boolean isHeader(String s) {
        if (s.isEmpty() || s.length() > MAX_HEADER_LEN) return false;
        int upper = 0, letters = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) { letters++; if (Character.isUpperCase(c)) upper++; }
        }
        return letters >= 2 && upper == letters;
    }

    /**
     * Order matters. Ownership is tested before sanctions because
     * "MAJORITY DIRECT STATE OWNED" contains neither, and warnings before
     * sanctions because "FINANCIAL SERVICES WARNINGS" must not be read as a
     * designation source.
     */
    public static SectionKind classify(String header) {
        if (header == null) return SectionKind.UNKNOWN;
        if (PROSE.contains(header)) return SectionKind.PROSE;
        if (BARE_JURISDICTIONS.contains(header)) return SectionKind.SANCTIONS;
        if (any(header, IDENTIFIER_HINTS)) return SectionKind.IDENTIFIER;
        if (any(header, OWNERSHIP_HINTS)) return SectionKind.OWNERSHIP;
        if (any(header, ASSET_STATUS_HINTS)) return SectionKind.ASSET_STATUS;
        if (any(header, WARNING_HINTS)) return SectionKind.REGULATORY_WARNING;
        if (any(header, ENFORCEMENT_HINTS)) return SectionKind.LAW_ENFORCEMENT;
        if (any(header, SANCTIONS_HINTS)) return SectionKind.SANCTIONS;
        return SectionKind.UNKNOWN;
    }

    private static boolean any(String h, String[] hints) {
        for (String s : hints) if (h.contains(s)) return true;
        return false;
    }

    public boolean parseable() {
        return kind != SectionKind.PROSE && header != null;
    }
}
