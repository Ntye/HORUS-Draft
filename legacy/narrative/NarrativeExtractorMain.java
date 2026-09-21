package horus.narrative;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Streams the feed, runs NarrativeParser over every further_information, and
 * writes the extracted facts plus coverage metrics.
 *
 * This is a HARNESS, not the ingestion pipeline. It exists so extraction rules
 * can be measured against the whole corpus before they are trusted, and so the
 * quarantine can be reviewed rather than assumed empty.
 *
 * Usage:
 *   java -Xmx4g horus.narrative.NarrativeExtractorMain <input.xml> <outDir>
 *        [maxRecords] [stride]
 *
 * stride=120 gives an unskewed ~50k sample across all 5,990,394 records;
 * omit both trailing arguments for the full run.
 */
public final class NarrativeExtractorMain {

    private static final String RECORD = "record";
    private static final String NARRATIVE = "record/details/further_information";
    private static final long PROGRESS_EVERY = 250_000L;
    private static final int MAX_ROWS_PER_FILE = 200_000;

    private final NarrativeParser parser = new NarrativeParser();

    private long seen, profiled, withNarrative;
    private long sectionsSeen, sectionsParsed;

    private final Map<String, long[]> statusByCat = new TreeMap<>();  // {none,live,rem,indet}
    private final Map<String, Long> eventsByType = new TreeMap<>();
    private final Map<String, Long> idsByType = new TreeMap<>();
    private final Map<String, Long> namesByParser = new TreeMap<>();
    private final Map<String, Long> quarantineByReason = new TreeMap<>();
    private final Map<String, Long> unclassifiedVerbs = new HashMap<>();
    private final Map<String, Long> sourceCounts = new TreeMap<>();

    private long ownershipStakes, ownershipWithPct, ownershipUnknownPct;

    private final List<String> eventRows = new ArrayList<>();
    private final List<String> ownershipRows = new ArrayList<>();
    private final List<String> identifierRows = new ArrayList<>();
    private final List<String> nameRows = new ArrayList<>();
    private final List<String> statusRows = new ArrayList<>();
    private final List<String> quarantineRows = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: java -Xmx4g horus.narrative.NarrativeExtractorMain "
                    + "<input.xml> <outDir> [maxRecords] [stride]");
            System.exit(2);
        }
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        long max = args.length > 2 ? Long.parseLong(args[2]) : Long.MAX_VALUE;
        int stride = args.length > 3 ? Math.max(1, Integer.parseInt(args[3])) : 1;

        if (!Files.isReadable(in)) {
            System.err.println("cannot read: " + in.toAbsolutePath());
            System.exit(2);
        }
        Files.createDirectories(out);

        NarrativeExtractorMain m = new NarrativeExtractorMain();
        long t0 = System.nanoTime();
        m.run(in, max, stride);
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        m.write(out, in, ms, stride);
        System.err.printf("done: %,d seen, %,d profiled, %,d ms%n", m.seen, m.profiled, ms);
    }


    /**
     * StAX factory configured for this feed.
     *
     * COALESCING IS OFF, deliberately. further_information reaches 125,644
     * characters, and coalescing assembles the whole text node in one go, which
     * trips JAXP's jdk.xml.maxGeneralEntitySizeLimit of 100,000 with
     *   "The length of entity [xml] is 100,001 that exceeds the limit".
     * This code already accumulates CHARACTERS events into a StringBuilder, so
     * coalescing bought nothing and cost the run.
     *
     * The JAXP limits are also lifted explicitly. That is safe here only because
     * DTD processing and external entity resolution are disabled below: the
     * attack the limits guard against is not reachable. Do not lift them in code
     * that leaves those enabled.
     *
     * Charset is deliberately not passed. The XML declaration decides, so a
     * wrong declaration fails loudly instead of producing silent mojibake -- a
     * silently corrupted name is a silent screening failure.
     */
    static XMLInputFactory newFactory() {
        System.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "0");
        System.setProperty("jdk.xml.totalEntitySizeLimit", "0");
        System.setProperty("jdk.xml.entityExpansionLimit", "0");
        System.setProperty("jdk.xml.maxElementDepth", "0");

        XMLInputFactory f = XMLInputFactory.newInstance();
        f.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
        f.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        f.setProperty("javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
        f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.TRUE);
        return f;
    }

    private void run(Path in, long max, int stride) throws Exception {
        XMLInputFactory f = newFactory();

        try (InputStream raw = new FileInputStream(in.toFile());
             BufferedInputStream bis = new BufferedInputStream(raw, 1 << 20)) {

            XMLStreamReader r = f.createXMLStreamReader(bis);
            StringBuilder text = new StringBuilder(4096);
            List<String> stack = new ArrayList<>(8);

            boolean inRecord = false, take = false;
            String uid = null, category = null, path = null;

            while (r.hasNext()) {
                int ev = r.next();

                if (ev == XMLStreamConstants.START_ELEMENT) {
                    String local = r.getLocalName();
                    if (!inRecord) {
                        if (!RECORD.equals(local)) continue;
                        inRecord = true;
                        stack.clear();
                        seen++;
                        take = (seen % stride == 0);
                        uid = attr(r, "uid");
                        category = attr(r, "category");
                        if (category == null || category.isBlank()) category = "(no-category)";
                        if (take) profiled++;
                        if (seen % PROGRESS_EVERY == 0) progress();
                    }
                    stack.add(local);
                    path = String.join("/", stack);
                    text.setLength(0);

                } else if (ev == XMLStreamConstants.CHARACTERS
                        || ev == XMLStreamConstants.CDATA) {
                    if (inRecord && take) text.append(r.getText());

                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    if (!inRecord) continue;
                    if (take && NARRATIVE.equals(path)) {
                        String body = text.toString().trim();
                        if (!body.isEmpty()) { withNarrative++; consume(uid, category, body); }
                    }
                    text.setLength(0);
                    stack.remove(stack.size() - 1);
                    if (stack.isEmpty()) {
                        inRecord = false;
                        path = null;
                        if (seen >= max) break;
                    } else {
                        path = String.join("/", stack);
                    }
                }
            }
            r.close();
        }
    }

    private void consume(String uid, String category, String narrative) {
        NarrativeParser.Outcome o = parser.parseWithStatus(narrative);
        Facts fx = o.facts();
        StatusDeriver.Result st = o.status();

        sectionsSeen += fx.sectionsSeen();
        sectionsParsed += fx.sectionsParsed();

        long[] s = statusByCat.computeIfAbsent(category, k -> new long[4]);
        switch (st.status()) {
            case NOT_SANCTIONED -> s[0]++;
            case LIVE -> s[1]++;
            case REMOVED_EVERYWHERE -> s[2]++;
            case INDETERMINATE -> s[3]++;
        }

        for (String src : fx.listSources()) sourceCounts.merge(src, 1L, Long::sum);

        for (Facts.SanctionEvent e : fx.events()) {
            eventsByType.merge(e.type().name(), 1L, Long::sum);
            if (e.type() == ActionVocabulary.ActionType.UNCLASSIFIED)
                unclassifiedVerbs.merge(e.rawVerb(), 1L, Long::sum);
            add(eventRows, uid + "\t" + category + "\t" + n(e.listSource()) + "\t"
                    + e.type() + "\t" + clean(e.rawVerb()) + "\t" + e.year() + "\t"
                    + e.provenance());
        }

        for (Facts.OwnershipStake k : fx.ownership()) {
            ownershipStakes++;
            if (k.percentStated()) ownershipWithPct++; else ownershipUnknownPct++;
            add(ownershipRows, uid + "\t" + category + "\t" + clean(k.relation()) + "\t"
                    + clean(k.owner()) + "\t" + n(k.ownerType()) + "\t"
                    + (k.percent() == null ? "" : k.percent()) + "\t"
                    + k.percentStated() + "\t" + k.provenance());
        }

        for (Facts.Identifier i : fx.identifiers()) {
            idsByType.merge(i.type(), 1L, Long::sum);
            add(identifierRows, uid + "\t" + category + "\t" + i.type() + "\t"
                    + clean(i.value()) + "\t" + n(i.issuingContext()) + "\t" + i.provenance());
        }

        for (Facts.Name nm : fx.names()) {
            namesByParser.merge(nm.sourceFormat(), 1L, Long::sum);
            add(nameRows, uid + "\t" + category + "\t" + nm.nameType() + "\t"
                    + clean(nm.value()) + "\t" + nm.sourceFormat() + "\t" + nm.provenance());
        }

        for (Facts.Unparsed u : fx.unparsed()) {
            quarantineByReason.merge(u.reason(), 1L, Long::sum);
            add(quarantineRows, uid + "\t" + category + "\t" + n(u.sectionHeader()) + "\t"
                    + u.reason() + "\t" + clean(u.excerpt()) + "\t" + u.provenance());
        }

        if (st.status() != StatusDeriver.RecordStatus.NOT_SANCTIONED) {
            StringBuilder per = new StringBuilder();
            for (StatusDeriver.SourceStatus ss : st.perSource()) {
                if (per.length() > 0) per.append(" | ");
                per.append(ss.listSource()).append(ss.removed() ? "=REMOVED" : "=LIVE")
                   .append("(add=").append(ss.lastAdditionYear())
                   .append(",rem=").append(ss.lastRemovalYear()).append(')');
            }
            add(statusRows, uid + "\t" + category + "\t" + st.status() + "\t"
                    + st.unclassifiedEvents() + "\t" + clean(st.rationale()) + "\t" + per);
        }
    }

    private static void add(List<String> rows, String row) {
        if (rows.size() < MAX_ROWS_PER_FILE) rows.add(row);
    }

    private void write(Path out, Path in, long ms, int stride) throws IOException {
        dump(out.resolve("extracted_events.tsv"),
             "uid\tcategory\tlist_source\taction_type\traw_verb\tyear\tprovenance", eventRows);
        dump(out.resolve("extracted_ownership.tsv"),
             "uid\tcategory\trelation\towner\towner_type\tpercent\tpercent_stated\tprovenance",
             ownershipRows);
        dump(out.resolve("extracted_identifiers.tsv"),
             "uid\tcategory\ttype\tvalue\tcontext\tprovenance", identifierRows);
        dump(out.resolve("extracted_names.tsv"),
             "uid\tcategory\tname_type\tvalue\tparser\tprovenance", nameRows);
        dump(out.resolve("extracted_status.tsv"),
             "uid\tcategory\tstatus\tunclassified_events\trationale\tper_source", statusRows);
        dump(out.resolve("quarantine.tsv"),
             "uid\tcategory\tsection\treason\texcerpt\tprovenance", quarantineRows);

        try (Writer w = writer(out.resolve("status_by_category.tsv"))) {
            w.write("category\tnot_sanctioned\tlive\tremoved_everywhere\tindeterminate"
                    + "\tremoved_pct_of_sanctioned\n");
            for (Map.Entry<String, long[]> e : statusByCat.entrySet()) {
                long[] v = e.getValue();
                long sanctioned = v[1] + v[2] + v[3];
                w.write(e.getKey() + "\t" + v[0] + "\t" + v[1] + "\t" + v[2] + "\t" + v[3]
                        + "\t" + pct(v[2], sanctioned) + "\n");
            }
        }

        try (Writer w = writer(out.resolve("unclassified_verbs.tsv"))) {
            w.write("raw_verb\toccurrences\n");
            List<Map.Entry<String, Long>> l = new ArrayList<>(unclassifiedVerbs.entrySet());
            l.sort(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                    .reversed());
            for (Map.Entry<String, Long> e : l) w.write(clean(e.getKey()) + "\t"
                    + e.getValue() + "\n");
            w.write("\n# Every verb here is a rule ActionVocabulary is missing. Add the\n"
                    + "# frequent ones and rerun; do NOT let them default to any status.\n");
        }

        try (Writer w = writer(out.resolve("list_sources.tsv"))) {
            w.write("canonical_list_source\trecords\n");
            for (Map.Entry<String, Long> e : sourceCounts.entrySet())
                w.write(e.getKey() + "\t" + e.getValue() + "\n");
        }

        Map<String, Long> res = parser.registry().resolutionCounts();
        try (Writer w = writer(out.resolve("parser_coverage.tsv"))) {
            w.write("parser_id\tsections_handled\tshare_pct\n");
            long tot = 0;
            for (long v : res.values()) tot += v;
            for (Map.Entry<String, Long> e : res.entrySet())
                w.write(e.getKey() + "\t" + e.getValue() + "\t" + pct(e.getValue(), tot) + "\n");
            w.write("\n# A high GENERIC share means another authority-specific parser is\n"
                    + "# worth writing: GENERIC extracts events but never names, because\n"
                    + "# without an authority's label vocabulary it cannot tell a name from\n"
                    + "# a field heading.\n");
        }

        try (Writer w = writer(out.resolve("extraction_summary.txt"))) {
            long none = 0, live = 0, rem = 0, ind = 0;
            for (long[] v : statusByCat.values()) { none += v[0]; live += v[1]; rem += v[2]; ind += v[3]; }
            long sanctioned = live + rem + ind;

            w.write("source file             : " + in.toAbsolutePath() + "\n");
            w.write("records seen            : " + seen + "\n");
            w.write("records profiled        : " + profiled
                    + (stride > 1 ? "  (stride " + stride + ")" : "") + "\n");
            w.write("with narrative          : " + withNarrative + "\n");
            w.write("sections seen           : " + sectionsSeen + "\n");
            w.write("sections parsed         : " + sectionsParsed
                    + " (" + pct(sectionsParsed, sectionsSeen) + "%)\n");
            w.write("\nSTATUS\n");
            w.write("  not sanctioned        : " + none + " (" + pct(none, profiled) + "%)\n");
            w.write("  live                  : " + live + "\n");
            w.write("  removed everywhere    : " + rem + "\n");
            w.write("  indeterminate         : " + ind + "\n");
            w.write("  removed as share of sanctioned records: "
                    + pct(rem, sanctioned) + "%\n");
            w.write("\nEVENTS BY TYPE\n");
            for (Map.Entry<String, Long> e : eventsByType.entrySet())
                w.write("  " + pad(e.getKey()) + e.getValue() + "\n");
            w.write("\nOWNERSHIP\n");
            w.write("  stakes extracted      : " + ownershipStakes + "\n");
            w.write("  with stated percent   : " + ownershipWithPct
                    + " (" + pct(ownershipWithPct, ownershipStakes) + "%)\n");
            w.write("  unknown percentage    : " + ownershipUnknownPct + "\n");
            w.write("\nIDENTIFIERS BY TYPE\n");
            for (Map.Entry<String, Long> e : idsByType.entrySet())
                w.write("  " + pad(e.getKey()) + e.getValue() + "\n");
            w.write("\nNAMES BY PARSER\n");
            for (Map.Entry<String, Long> e : namesByParser.entrySet())
                w.write("  " + pad(e.getKey()) + e.getValue() + "\n");
            w.write("\nQUARANTINE BY REASON\n");
            if (quarantineByReason.isEmpty()) w.write("  (empty)\n");
            for (Map.Entry<String, Long> e : quarantineByReason.entrySet())
                w.write("  " + pad(e.getKey()) + e.getValue() + "\n");
            w.write("\nvocabulary rules        : " + ActionVocabulary.ruleCount() + "\n");
            w.write("identifier rules        : " + IdentifierParser.ruleCount() + "\n");
            w.write("elapsed ms              : " + ms + "\n");
            w.write("heap at end (MB)        : "
                    + ((Runtime.getRuntime().totalMemory()
                       - Runtime.getRuntime().freeMemory()) >> 20) + "\n");

            w.write("\nWHAT TO CHECK, IN THIS ORDER\n");
            w.write("1. quarantine.tsv is the honest record of what the rules did NOT\n"
                    + "   handle. It is never expected to be empty. Read it before trusting\n"
                    + "   any coverage figure.\n");
            w.write("2. unclassified_verbs.tsv lists every verb ActionVocabulary missed.\n"
                    + "   Each one weakens the status derivation. Add the frequent ones.\n");
            w.write("3. extracted_names.tsv must contain NAMES. If field labels appear\n"
                    + "   ('Designation source', 'Secondary sanctions risk'), that parser's\n"
                    + "   label list is incomplete and the output must not be indexed.\n");
            w.write("4. parser_coverage.tsv: a high GENERIC share means the next\n"
                    + "   authority-specific parser is the highest-value work.\n");
            w.write("5. Nothing here suppresses a candidate. Status DEMOTES and EXPLAINS\n"
                    + "   (I-8). mayStillBeDesignated() answers true for INDETERMINATE.\n");
        }
    }

    private static void dump(Path p, String header, List<String> rows) throws IOException {
        try (Writer w = writer(p)) {
            w.write(header + "\n");
            for (String r : rows) w.write(r + "\n");
            if (rows.size() >= MAX_ROWS_PER_FILE)
                w.write("\n# CAPPED at " + MAX_ROWS_PER_FILE + " rows.\n");
        }
    }

    private static String attr(XMLStreamReader r, String name) {
        for (int i = 0; i < r.getAttributeCount(); i++)
            if (name.equals(r.getAttributeLocalName(i))) return r.getAttributeValue(i);
        return null;
    }

    private void progress() {
        Runtime rt = Runtime.getRuntime();
        System.err.printf("  %,d seen / %,d profiled  |  sections %,d  |  heap %d MB%n",
                seen, profiled, sectionsSeen, (rt.totalMemory() - rt.freeMemory()) >> 20);
    }

    private static String n(String s) { return s == null ? "" : s; }
    private static String pad(String s) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < 24) b.append(' ');
        return b.toString();
    }
    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ')
                                 .replace('\r', ' ').trim();
    }
    private static String pct(long a, long b) {
        return b == 0 ? "0.00" : String.format("%.2f", 100.0 * a / b);
    }
    private static Writer writer(Path p) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(p), StandardCharsets.UTF_8), 1 << 16);
    }
}
