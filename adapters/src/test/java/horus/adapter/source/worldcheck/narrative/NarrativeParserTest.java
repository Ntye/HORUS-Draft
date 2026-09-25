package horus.adapter.source.worldcheck.narrative;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Migrated from legacy/narrative/SelfTest.java. Every fixture below is invented -- CLAUDE.md §3
// forbids copying a real listed name into a test, so this is a structural rewrite, not a
// byte-for-byte port: same grammatical traps the original assertions exercised (multi-source
// designation/removal history, per-authority alias conventions, field-label rejection,
// truncation-fragment rejection), invented organisations and people throughout.
class NarrativeParserTest {

    private final NarrativeParser parser = new NarrativeParser();

    // A fictional organisation, designated then removed by every authority that ever listed it,
    // structurally equivalent to the "de-listed everywhere, no structured status field" case
    // this parser exists to handle.
    private static final String REMOVED_EVERYWHERE =
        "[AUSTRALIA SANCTIONS - DFAT] No. 481,a,b,c. (Nov 2013 - removed). "
      + "PRIMARY NAME: PALMARA LIBERATION FRONT. a.k.a: PLF; "
      + "FRONT FOR THE LIBERATION OF PALMARA. "
      + "[CANADA SANCTIONS - UNSTR] Aug 2019 - removed. PRIMARY NAME: Palmara "
      + "Liberation Front. Alias: Free Palmara Movement. "
      + "[EU SANCTIONS] CP 2001/931/CFSP, subject only to Article 4 (Jun 2009 - amended. "
      + "Jul 2023 - no longer appears). 2005/671/JHA. PRIMARY NAME: Deka Enosi "
      + "Palmara. Alias: 'Palmara Liberation Front'. "
      + "[UK SANCTIONS - UKHMT] Nov 2001 - addition. Sep 2010 - removed. "
      + "PRIMARY NAME: PALMARA LIBERATION FRONT. Other Information: "
      + "Listed by UK only on 2 November 2001. Group ID: 8821. "
      + "[USA SANCTIONS - OFAC] SDN Ref No 5390 - FTO (Aug 1997 - addition). "
      + "(Sep 2015 - removed). PRIMARY NAME: PALMARA LIBERATION FRONT "
      + "(a.k.a. PLF; a.k.a. FRONT FOR THE LIBERATION OF PALMARA). "
      + "[BIOGRAPHY] Left-wing organisation in Palmara. Formed circa 1974. "
      + "Presumed disbanded and inactive.";

    private static final String OWNERSHIP_WITH_PERCENT =
        "[ULTIMATE GOVERNMENT OWNERSHIP] Government of Astoria (100%). "
      + "[DIRECT SHAREHOLDER/S] Ministry of Finance (50%). "
      + "Astoria National Holdings (IOS) (50%). "
      + "[MAJORITY DIRECT STATE OWNED]";

    private static final String OWNERSHIP_PARTIAL =
        "[ULTIMATE GOVERNMENT OWNERSHIP] Government of Veyland (unknown percentage). "
      + "[DIRECT SHAREHOLDER/S] Ministry of Economy (51,7%). Veyland Energy Corp (SOE) (46,78%).";

    private static final String LIVE_ON_ONE_SOURCE =
        "[USA SANCTIONS - OFAC] SDN Ref No 9001 (Mar 2022 - addition). "
      + "[EU SANCTIONS] (Jan 2015 - addition. Feb 2016 - removed).";

    @Test
    void sectionsAreSplitAndClassifiedCorrectly() {
        var sections = Section.split(REMOVED_EVERYWHERE);

        assertThat(sections.size()).isGreaterThanOrEqualTo(6);
        assertThat(sections).anyMatch(s -> "BIOGRAPHY".equals(s.header()) && s.kind() == SectionKind.PROSE);
        assertThat(sections).anyMatch(s -> "USA SANCTIONS - OFAC".equals(s.header())
                && s.kind() == SectionKind.SANCTIONS);
    }

    @Test
    void removedEverywhereRecordYieldsRemovedEverywhereStatus() {
        var outcome = parser.parseWithStatus(REMOVED_EVERYWHERE);
        Facts facts = outcome.facts();

        assertThat(facts.listSources().size()).isGreaterThanOrEqualTo(4);
        assertThat(facts.events().size()).isGreaterThanOrEqualTo(6);
        assertThat(outcome.status().status()).isEqualTo(StatusDeriver.RecordStatus.REMOVED_EVERYWHERE);
        assertThat(StatusDeriver.mayStillBeDesignated(outcome.status())).isFalse();
    }

    @Test
    void perAuthorityIdentifiersAreCaptured() {
        Facts facts = parser.parse(REMOVED_EVERYWHERE);

        assertThat(facts.identifiers()).anyMatch(i -> "SDN_REF".equals(i.type())
                && "5390".equals(i.value()));
        assertThat(facts.identifiers()).anyMatch(i -> "GROUP_ID".equals(i.type())
                && "8821".equals(i.value()));
        assertThat(facts.identifiers()).anyMatch(i -> "EU_LEGAL_BASIS".equals(i.type()));
    }

    @Test
    void perAuthorityAliasConventionsAreCaptured() {
        Facts facts = parser.parse(REMOVED_EVERYWHERE);

        assertThat(facts.names()).anyMatch(n -> n.value().contains("FRONT FOR THE LIBERATION"));
        assertThat(facts.names()).anyMatch(n -> "EU".equals(n.sourceFormat()));
    }

    @Test
    void noFieldLabelsOrYearStringsLeakIntoNames() {
        Facts facts = parser.parse(REMOVED_EVERYWHERE);

        assertThat(facts.names()).noneMatch(n ->
                n.value().contains("Designation source")
             || n.value().contains("Other Information")
             || n.value().contains("Secondary sanctions")
             || n.value().contains(":"));
        assertThat(facts.names()).noneMatch(n -> n.value().matches(".*\\b(19|20)\\d{2}\\b.*"));
    }

    @Test
    void proseSectionsContributeNoEventsAndEveryEventHasProvenance() {
        Facts facts = parser.parse(REMOVED_EVERYWHERE);

        assertThat(facts.events()).noneMatch(e -> e.rawVerb().contains("disbanded"));
        assertThat(facts.events()).allSatisfy(e -> assertThat(e.provenance()).isNotNull());
    }

    @Test
    void ownershipStakesAreParsedWithPercentagesAndTypeMarkers() {
        Facts facts = parser.parse(OWNERSHIP_WITH_PERCENT);

        assertThat(facts.ownership().size()).isGreaterThanOrEqualTo(3);
        assertThat(facts.ownership()).anyMatch(s ->
                s.percent() != null && Math.abs(s.percent() - 50.0) < 0.001);
        assertThat(facts.ownership()).anyMatch(s -> "IOS".equals(s.ownerType()));
    }

    @Test
    void ownershipHandlesCommaDecimalsAndUnknownPercentages() {
        Facts facts = parser.parse(OWNERSHIP_PARTIAL);

        assertThat(facts.ownership()).anyMatch(s ->
                s.percent() != null && Math.abs(s.percent() - 51.7) < 0.001);
        assertThat(facts.ownership()).anyMatch(s -> !s.percentStated() && s.owner().contains("Veyland"));
        assertThat(facts.ownership()).anyMatch(s -> "SOE".equals(s.ownerType()));
    }

    @Test
    void liveOnOneSourceOverridesRemovalOnAnother() {
        var outcome = parser.parseWithStatus(LIVE_ON_ONE_SOURCE);

        assertThat(outcome.status().status()).isEqualTo(StatusDeriver.RecordStatus.LIVE);
        assertThat(StatusDeriver.mayStillBeDesignated(outcome.status())).isTrue();
    }

    @Test
    void parsingIsDeterministic() {
        Facts first = parser.parse(REMOVED_EVERYWHERE);
        Facts second = parser.parse(REMOVED_EVERYWHERE);

        assertThat(second.events().size()).isEqualTo(first.events().size());
        assertThat(second.names().size()).isEqualTo(first.names().size());
    }

    @Test
    void nullOrBlankNarrativeYieldsNoFacts() {
        assertThat(parser.parse(null).events()).isEmpty();
        assertThat(parser.parse("   ").sectionsSeen()).isZero();
    }

    // ---- v3 regressions: field labels, roles and truncation must never reach the name index

    @Test
    void v3FieldLabelsRolesAndAddressesAreNeverEmittedAsNames() {
        Facts bad = parser.parse(
            "[UK SANCTIONS - UKHMT] PRIMARY NAME: VELIKANOV, Stepan Igorevich. "
          + "Financial sanctions imposed in addition to an asset freeze: Trust services. "
          + "Designation source: UK. Sex: M. "
          + "[USA SANCTIONS - OFAC] PRIMARY NAME: MORALES TORRES, Reinaldo, "
          + "c/o INMOBILIARIA DEL VALLE SRL., Medellin, Colombia. nationality Astoria. "
          + "Member of the Supreme Council of the National Assembly. "
          + "[EU SANCTIONS] PRIMARY NAME: Deputy Minister of Culture. "
          + "Alias: 'Astoria People's Republic and the Veyland People's Republic'.");

        for (String junk : new String[]{
                "Financial sanctions", "Designation source", "nationality",
                "Member of the Supreme Council", "Deputy Minister", "activist",
                "Medellin, Colombia", "c/o"}) {
            assertThat(bad.names()).noneMatch(n -> n.value().contains(junk));
        }

        for (String frag : new String[]{"s Republic", "and ", "the ", "of "}) {
            assertThat(bad.names()).noneMatch(n -> n.value().startsWith(frag));
        }
        assertThat(bad.names()).noneMatch(n ->
                n.value().length() > 1 && Character.isLowerCase(n.value().charAt(0))
                && n.value().indexOf(' ') > 0);
        assertThat(bad.names()).anyMatch(n -> n.value().contains("VELIKANOV"));
    }

    // ---- v4 regressions: truncation stubs left by cutAtLabel must be rejected

    @Test
    void v4TruncationFragmentsAreRejectedButShortRealNamesSurvive() {
        Facts frag = parser.parse(
            "[USA SANCTIONS - OFAC] PRIMARY NAME: Additional Sanctio. "
          + "[EU SANCTIONS] PRIMARY NAME: A com. PRIMARY NAME: Add.");
        for (String stub : new String[]{"Additional Sanctio", "A com", "Add"}) {
            assertThat(frag.names()).noneMatch(n -> n.value().equals(stub));
        }

        Facts keep = parser.parse("[USA SANCTIONS - OFAC] PRIMARY NAME: KASSIM, Tarek.");
        assertThat(keep.names()).anyMatch(n -> n.value().contains("KASSIM"));
    }

    @Test
    void anImoRegistrationSectionYieldsAnIdentifier() {
        Facts facts = parser.parse("[IMO REGISTRATION] IMO 9176187.");

        assertThat(facts.identifiers()).anyMatch(i -> "IMO".equals(i.type()));
    }
}
