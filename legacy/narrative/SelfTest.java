package horus.narrative;

/**
 * Runs the parser over a known record and asserts the facts it must produce.
 *
 * Run this BEFORE pointing the extractor at the feed. It costs a second and
 * catches the class of defect that is otherwise invisible: rules that compile,
 * run, and quietly extract nothing.
 *
 * The fixture is the 17 November record, chosen because it is de-listed by
 * every authority that ever designated it while remaining in the feed with no
 * structured status field. That is exactly the case the structured record
 * cannot express.
 *
 *   java -cp out horus.narrative.SelfTest
 */
public final class SelfTest {

    private static int checks, failures;

    private static final String N17 =
        "[AUSTRALIA SANCTIONS - DFAT] No. 291,a,b,c. (Nov 2013 - removed). "
      + "PRIMARY NAME: REVOLUTIONARY ORGANIZATION 17 NOVEMBER. a.k.a: 17 November; "
      + "EPANASTATIKI ORGANOSI 17 NOEMVRI. "
      + "[CANADA SANCTIONS - UNSTR] Aug 2019 - removed. PRIMARY NAME: Revolutionary "
      + "Organization 17 November. Alias: Organisation revolutionnaire 17 novembre. "
      + "[EU SANCTIONS] CP 2001/931/CFSP, subject only to Article 4 (Jun 2009 - amended. "
      + "Jul 2023 - no longer appears). 2005/671/JHA. PRIMARY NAME: Dekati Evdomi "
      + "Noemvri. Alias: 'Revolutionary Organisation 17 November'. "
      + "[UK SANCTIONS - UKHMT] Nov 2001 - addition. Sep 2010 - removed. "
      + "PRIMARY NAME: REVOLUTIONARY ORGANISATION 17 NOVEMBER. Other Information: "
      + "Listed by UK only on 2 November 2001. Group ID: 7419. "
      + "[USA SANCTIONS - OFAC] SDN Ref No 4712 - FTO (Aug 1997 - addition). "
      + "(Sep 2015 - removed). PRIMARY NAME: REVOLUTIONARY ORGANIZATION 17 NOVEMBER "
      + "(a.k.a. 17 NOVEMBER; a.k.a. EPANASTATIKI ORGANOSI 17 NOEMVRI). "
      + "[BIOGRAPHY] Left-wing organisation in Greece. Formed circa 1974. "
      + "Presumed disbanded and inactive.";

    private static final String OWNED =
        "[ULTIMATE GOVERNMENT OWNERSHIP] Government of Sweden (100%). "
      + "[DIRECT SHAREHOLDER/S] Ministry of Finance (50%). "
      + "Sveriges Kommuner och Regioner (IOS) (50%). "
      + "[MAJORITY DIRECT STATE OWNED]";

    private static final String PARTIAL =
        "[ULTIMATE GOVERNMENT OWNERSHIP] Government of Libya (unknown percentage). "
      + "[DIRECT SHAREHOLDER/S] Ministry of Economy (51,7%). Uzbekneftegas (SOE) (46,78%).";

    private static final String LIVE_ONE =
        "[USA SANCTIONS - OFAC] SDN Ref No 9001 (Mar 2022 - addition). "
      + "[EU SANCTIONS] (Jan 2015 - addition. Feb 2016 - removed).";

    public static void main(String[] args) {
        NarrativeParser p = new NarrativeParser();

        // ---------------- sectioning
        var sections = Section.split(N17);
        ge("sections found", sections.size(), 6);
        has("BIOGRAPHY classified as prose",
            sections.stream().anyMatch(s -> "BIOGRAPHY".equals(s.header())
                    && s.kind() == SectionKind.PROSE));
        has("OFAC section classified as sanctions",
            sections.stream().anyMatch(s -> "USA SANCTIONS - OFAC".equals(s.header())
                    && s.kind() == SectionKind.SANCTIONS));

        // ---------------- 17 November: removed everywhere
        var o = p.parseWithStatus(N17);
        Facts f = o.facts();
        ge("list sources", f.listSources().size(), 4);
        ge("events", f.events().size(), 6);
        eq("status", o.status().status(), StatusDeriver.RecordStatus.REMOVED_EVERYWHERE);
        has("mayStillBeDesignated false", !StatusDeriver.mayStillBeDesignated(o.status()));

        has("OFAC SDN ref captured",
            f.identifiers().stream().anyMatch(i -> "SDN_REF".equals(i.type())
                    && "4712".equals(i.value())));
        has("OFSI group id captured",
            f.identifiers().stream().anyMatch(i -> "GROUP_ID".equals(i.type())
                    && "7419".equals(i.value())));
        has("EU legal basis captured",
            f.identifiers().stream().anyMatch(i -> "EU_LEGAL_BASIS".equals(i.type())));

        has("OFAC parenthesised alias captured",
            f.names().stream().anyMatch(n -> n.value().contains("EPANASTATIKI")));
        has("EU quoted alias captured",
            f.names().stream().anyMatch(n -> "EU".equals(n.sourceFormat())));

        // the defect that polluted the first measurement pass
        has("no field labels leaked into names",
            f.names().stream().noneMatch(n ->
                    n.value().contains("Designation source")
                 || n.value().contains("Other Information")
                 || n.value().contains("Secondary sanctions")
                 || n.value().contains(":")));
        has("no year strings in names",
            f.names().stream().noneMatch(n -> n.value().matches(".*\\b(19|20)\\d{2}\\b.*")));
        has("prose contributed no events",
            f.events().stream().noneMatch(e -> e.rawVerb().contains("disbanded")));

        for (Facts.SanctionEvent e : f.events()) {
            has("event " + e.rawVerb() + " has provenance", e.provenance() != null);
        }

        // ---------------- ownership
        Facts ow = p.parse(OWNED);
        ge("ownership stakes", ow.ownership().size(), 3);
        has("50% parsed", ow.ownership().stream()
                .anyMatch(s -> s.percent() != null && Math.abs(s.percent() - 50.0) < 0.001));
        has("IOS type marker parsed", ow.ownership().stream()
                .anyMatch(s -> "IOS".equals(s.ownerType())));

        Facts pw = p.parse(PARTIAL);
        has("comma decimal 51,7% parsed", pw.ownership().stream()
                .anyMatch(s -> s.percent() != null && Math.abs(s.percent() - 51.7) < 0.001));
        has("unknown percentage flagged, not dropped", pw.ownership().stream()
                .anyMatch(s -> !s.percentStated() && s.owner().contains("Libya")));
        has("SOE type marker parsed", pw.ownership().stream()
                .anyMatch(s -> "SOE".equals(s.ownerType())));

        // ---------------- live on one source, removed on another
        var lo = p.parseWithStatus(LIVE_ONE);
        eq("live when any source still designates", lo.status().status(),
           StatusDeriver.RecordStatus.LIVE);
        has("mayStillBeDesignated true", StatusDeriver.mayStillBeDesignated(lo.status()));

        // ---------------- determinism (I-4)
        eq("deterministic events", p.parse(N17).events().size(), f.events().size());
        eq("deterministic names", p.parse(N17).names().size(), f.names().size());

        // ---------------- v3 regressions: every one of these reached the
        //                  output of the v2 run and must never return
        Facts bad = p.parse(
            "[UK SANCTIONS - UKHMT] PRIMARY NAME: ZYUGANOV,Gennady Andreevich. "
          + "Financial sanctions imposed in addition to an asset freeze: Trust services. "
          + "Designation source: UK. Sex: M. "
          + "[USA SANCTIONS - OFAC] PRIMARY NAME: ANDRADE QUINTERO, Ancizar, "
          + "c/o INMOBILIARIA BOLIVAR LTDA., Cali, Colombia. nationality Russia. "
          + "Member of the State Duma of the Federal Assembly. "
          + "[EU SANCTIONS] PRIMARY NAME: Deputy Minister of Culture. "
          + "Alias: 'Donetsk People's Republic and the Luhansk People's Republic'.");

        // Substrings that must never appear anywhere in a name.
        for (String junk : new String[]{
                "Financial sanctions", "Designation source", "nationality",
                "Member of the State", "Deputy Minister", "activist",
                "Cali, Colombia", "c/o"}) {
            has("v3: name output free of \"" + junk + "\"",
                bad.names().stream().noneMatch(n -> n.value().contains(junk)));
        }

        // Split fragments, which are only ever wrong at the START of a value.
        // Testing these with contains() was wrong: "Donetsk People's Republic"
        // legitimately contains "s Republic" because of the possessive, so the
        // assertion failed on a correct extraction.
        for (String frag : new String[]{"s Republic", "and ", "the ", "of "}) {
            has("v3: no name begins with \"" + frag + "\"",
                bad.names().stream().noneMatch(n -> n.value().startsWith(frag)));
        }
        has("v3: no name begins lowercase mid-phrase",
            bad.names().stream().noneMatch(n ->
                n.value().length() > 1 && Character.isLowerCase(n.value().charAt(0))
                && n.value().indexOf(' ') > 0));
        has("v3: real name still extracted",
            bad.names().stream().anyMatch(n -> n.value().contains("ZYUGANOV")));

        // ---------------- v3: section classification widened
        eq("EU RESTRICTIVE MEASURES is sanctions",
           Section.classify("EU RESTRICTIVE MEASURES"), SectionKind.SANCTIONS);
        eq("UK INVESTMENT BAN is sanctions",
           Section.classify("UK INVESTMENT BAN - UKHMT-IB"), SectionKind.SANCTIONS);
        eq("bare USA is sanctions", Section.classify("USA"), SectionKind.SANCTIONS);
        eq("bare UN is sanctions", Section.classify("UN"), SectionKind.SANCTIONS);
        eq("IMO REGISTRATION is identifier",
           Section.classify("IMO REGISTRATION"), SectionKind.IDENTIFIER);
        eq("DETAINED VESSEL is asset status",
           Section.classify("DETAINED VESSEL"), SectionKind.ASSET_STATUS);
        eq("KEYWORD NOTE is prose",
           Section.classify("KEYWORD NOTE"), SectionKind.PROSE);
        eq("CIVIL PENALTIES is a warning, not a designation",
           Section.classify("CIVIL PENALTIES - OFAC"), SectionKind.REGULATORY_WARNING);

        // ---------------- v3: verbs previously unclassified
        for (String v : new String[]{"imposed", "no longer applies", "not in effect",
                "named on the official list relating to russia",
                "list officially confirmed"}) {
            has("v3: verb classified: " + v,
                ActionVocabulary.classify(v).type()
                        != ActionVocabulary.ActionType.UNCLASSIFIED);
        }

        Facts imo = p.parse("[IMO REGISTRATION] IMO 9176187.");
        has("v3: IMO section yields an identifier",
            imo.identifiers().stream().anyMatch(i -> "IMO".equals(i.type())));

        // ---------------- v4: general licences are not lifecycle events
        for (String v : new String[]{
                "ofac issued general license no",
                "ofsi general licence int/",
                "ofsi extends general licence int/",
                "ofac issued notice regarding the non-renewal of general lice"}) {
            eq("v4: licence not a lifecycle event: " + v,
               ActionVocabulary.classify(v).type(),
               ActionVocabulary.ActionType.AUTHORISATION);
        }
        eq("v4: assets frozen is an addition",
           ActionVocabulary.classify("assets frozen for a further").type(),
           ActionVocabulary.ActionType.ADDITION);

        var lic = p.parseWithStatus(
            "[USA SANCTIONS - OFAC] (Jun 2023 - ofac issued general license no 42).");
        eq("v4: a licence alone implies a LIVE listing",
           lic.status().status(), StatusDeriver.RecordStatus.LIVE);
        has("v4: licence recorded as authorisation, not addition",
            lic.facts().events().stream().anyMatch(e ->
                e.type() == ActionVocabulary.ActionType.AUTHORISATION));
        has("v4: licence moved no addition date",
            lic.status().perSource().stream()
                .allMatch(ss -> ss.lastAdditionYear() == null));

        // ---------------- v4: FENTANYL is prose, not a fact section
        eq("v4: FENTANYL is prose",
           Section.classify("FENTANYL"), SectionKind.PROSE);

        // ---------------- v4: truncation fragments rejected
        Facts frag = p.parse(
            "[USA SANCTIONS - OFAC] PRIMARY NAME: Additional Sanctio. "
          + "[EU SANCTIONS] PRIMARY NAME: A com. PRIMARY NAME: Add.");
        for (String stub : new String[]{"Additional Sanctio", "A com", "Add"}) {
            has("v4: fragment rejected: " + stub,
                frag.names().stream().noneMatch(n -> n.value().equals(stub)));
        }
        Facts keep = p.parse("[USA SANCTIONS - OFAC] PRIMARY NAME: HAWATMEH, Nayif.");
        has("v4: short real name still kept",
            keep.names().stream().anyMatch(n -> n.value().contains("HAWATMEH")));

        // ---------------- empty input
        eq("null narrative yields no events", p.parse(null).events().size(), 0);
        eq("blank narrative yields no sections", p.parse("   ").sectionsSeen(), 0);

        System.out.printf("%n%d checks, %d failures%n", checks, failures);
        if (failures > 0) {
            System.out.println("\nFAILURES ABOVE ARE NOT COSMETIC. A rule that extracts");
            System.out.println("nothing looks identical to a rule that works, until the");
            System.out.println("coverage report is read as success.");
        }
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void has(String what, boolean ok) {
        checks++;
        if (!ok) { failures++; System.out.println("FAIL  " + what); }
        else System.out.println("ok    " + what);
    }

    private static void eq(String what, Object actual, Object expected) {
        checks++;
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (!ok) {
            failures++;
            System.out.println("FAIL  " + what + "  expected=" + expected + " actual=" + actual);
        } else System.out.println("ok    " + what + "  (" + actual + ")");
    }

    private static void ge(String what, int actual, int atLeast) {
        checks++;
        if (actual < atLeast) {
            failures++;
            System.out.println("FAIL  " + what + "  expected >=" + atLeast + " actual=" + actual);
        } else System.out.println("ok    " + what + "  (" + actual + ")");
    }
}
