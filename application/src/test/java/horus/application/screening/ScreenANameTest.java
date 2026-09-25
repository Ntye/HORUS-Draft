package horus.application.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.application.ingestion.RecordAuditEvent;
import horus.application.ingestion.testsupport.FakeAuditEventPort;
import horus.application.ingestion.testsupport.FakeIdGenerator;
import horus.application.port.ActiveListVersion;
import horus.application.port.ActiveListVersionLookup;
import horus.application.port.CandidateViewAssembler;
import horus.application.port.MatchConfigRepository;
import horus.application.port.ScreeningRecord;
import horus.application.port.ScreeningRepository;
import horus.application.port.VersionedMatchConfig;
import horus.application.port.WhitelistViewProvider;
import horus.blocking.BlockingIndex;
import horus.blocking.BlockingOutcome;
import horus.blocking.CandidateGenerator;
import horus.blocking.CandidateKey;
import horus.blocking.Completeness;
import horus.blocking.IndexCapabilities;
import horus.blocking.KeyProbe;
import horus.blocking.Strategy;
import horus.domain.audit.ActorKind;
import horus.domain.audit.AuditEvent;
import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.DecisionBand;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityType;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.ListVersionId;
import horus.domain.shared.ScreeningId;
import horus.matching.CandidateView;
import horus.matching.MatchConfig;
import horus.matching.MatchingEngine;
import horus.matching.Registry;
import horus.matching.WhitelistView;
import horus.normalisation.NormalisationPipeline;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// All names are invented (CLAUDE.md §3). The blocking index, the assembler and every repository
// are fakes; the real matching engine and normalisation pipeline are used, so what is asserted is
// the use case's own logic -- fail-closed handling, dominance, provenance, evidence, audit.
class ScreenANameTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final UUID CONFIG_ID = new UUID(9, 9);
    private static final ListVersionId LIST_VERSION = new ListVersionId(new UUID(7, 7));
    private static final NormalisationPipeline PIPELINE = NormalisationPipeline.standard();

    private final Map<String, BlockingOutcome> outcomeByQuery = new HashMap<>();
    private final Map<EntityVersionId, CandidateView> views = new HashMap<>();
    private final FakeAuditEventPort audit = new FakeAuditEventPort();
    private final InMemoryScreeningRepository repository = new InMemoryScreeningRepository();
    private List<ActiveListVersion> activeLists = List.of(
            new ActiveListVersion(LIST_VERSION, "test-source", "1.0.0", EnumSet.allOf(CanonicalSlot.class)));
    private Optional<VersionedMatchConfig> currentConfig =
            Optional.of(new VersionedMatchConfig(CONFIG_ID, "default", MatchConfig.defaults()));
    private ScreenAName screenAName;

    private static CandidateKey key(long n) {
        return new CandidateKey(new EntityId(new UUID(0, n)), new EntityVersionId(new UUID(1, n)));
    }

    private static CandidateView view(long n, String name) {
        return CandidateView.builder(new EntityId(new UUID(0, n)), new EntityVersionId(new UUID(1, n)),
                "test-source", EntityType.INDIVIDUAL).primaryName(PIPELINE.normalise(name)).build();
    }

    private static final class InMemoryScreeningRepository implements ScreeningRepository {
        final Map<ScreeningId, ScreeningRecord> saved = new HashMap<>();
        boolean failOnSave;

        @Override
        public void save(ScreeningRecord record) {
            if (failOnSave) {
                throw new IllegalStateException("database unavailable");
            }
            saved.put(record.request().screeningId(), record);
        }

        @Override
        public Optional<ScreeningRecord> findById(ScreeningId id) {
            return Optional.ofNullable(saved.get(id));
        }

        @Override
        public Optional<ScreeningId> findIdByIdempotencyKey(String key) {
            return saved.values().stream().map(ScreeningRecord::request)
                    .filter(r -> r.idempotencyKey().equals(key)).map(r -> r.screeningId()).findFirst();
        }
    }

    // The scripted index answers by the raw text of the query, which the tests control.
    private BlockingIndex scriptedIndex(Function<KeyProbe, BlockingOutcome> answer) {
        return new BlockingIndex() {
            @Override
            public IndexCapabilities capabilities() {
                return new IndexCapabilities(Set.of(Strategy.TRIGRAM));
            }

            @Override
            public BlockingOutcome lookup(KeyProbe probe) {
                return answer.apply(probe);
            }
        };
    }

    @BeforeEach
    void wire() {
        rebuild(scriptedIndex(probe -> outcomeByQuery.getOrDefault(probe.name().raw(),
                new BlockingOutcome(Strategy.TRIGRAM, Completeness.COMPLETE, List.of()))));
    }

    private void rebuild(BlockingIndex index) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        FakeIdGenerator ids = new FakeIdGenerator();
        ActiveListVersionLookup activeLookup = () -> activeLists;
        MatchConfigRepository configs = new MatchConfigRepository() {
            @Override
            public Optional<VersionedMatchConfig> findCurrent(String profileId, Instant asOf) {
                return currentConfig;
            }

            @Override
            public void save(VersionedMatchConfig config, String createdBy, Instant createdAt, String approvedBy,
                    Instant effectiveFrom) {
                throw new UnsupportedOperationException();
            }
        };
        CandidateViewAssembler assembler = keys -> {
            Map<EntityVersionId, CandidateView> found = new HashMap<>();
            for (CandidateKey k : keys) {
                if (views.containsKey(k.entityVersionId())) {
                    found.put(k.entityVersionId(), views.get(k.entityVersionId()));
                }
            }
            return found;
        };
        WhitelistViewProvider whitelist = query -> WhitelistView.empty();
        screenAName = new ScreenAName(PIPELINE,
                new CandidateGenerator(index, List.of(Strategy.TRIGRAM)), assembler,
                new MatchingEngine(Registry.standard()),
                new LoadScreeningConfig(configs, activeLookup, Registry.standard(), clock),
                whitelist, repository, new RecordAuditEvent(audit, ids, clock), ids, clock);
    }

    private static ScreenAName.SubjectInput subject(String name) {
        return new ScreenAName.SubjectInput(Optional.empty(), name, Optional.of(EntityType.INDIVIDUAL),
                Optional.empty(), Set.of(), Set.of());
    }

    private static ScreenAName.Command command(String key, ScreenAName.SubjectInput... subjects) {
        return new ScreenAName.Command("test-consumer", Optional.of("ref-1"), "default", "operator-1",
                ActorKind.OPERATOR, key, List.of(subjects));
    }

    private void blockTo(String rawQuery, Completeness completeness, long... candidates) {
        List<CandidateKey> keys = new ArrayList<>();
        for (long c : candidates) {
            keys.add(key(c));
        }
        outcomeByQuery.put(rawQuery, new BlockingOutcome(Strategy.TRIGRAM, completeness, keys));
    }

    // ---- happy path & evidence ----

    @Test
    void screensANameStoresTheWholeExplainedResultAndCarriesProvenance() {
        views.put(key(1).entityVersionId(), view(1, "Zorvan Talmesk"));
        blockTo("Zorvan Talmesc", Completeness.COMPLETE, 1);

        ScreenAName.Result result = screenAName.execute(command("k-1", subject("Zorvan Talmesc")));

        assertThat(result.outcome()).isIn(DecisionBand.POSSIBLE_MATCH, DecisionBand.STRONG_MATCH);
        // I-12: every result carries the list versions, the config version and the pipeline version.
        assertThat(result.request().listVersionIds()).containsExactly(LIST_VERSION);
        assertThat(result.request().configVersionId()).isEqualTo(CONFIG_ID);
        assertThat(result.request().pipelineVersion()).isEqualTo(PIPELINE.pipelineVersion());

        ScreeningRecord stored = repository.findById(result.request().screeningId()).orElseThrow();
        assertThat(stored.subjects()).hasSize(1);
        var candidate = stored.subjects().get(0).candidates().get(0);
        assertThat(candidate.rank()).isEqualTo(1);
        assertThat(candidate.explanationJson()).contains("\"features\"").contains("\"effects\"");
        assertThat(candidate.explanationSchemaVersion()).isEqualTo("1");
        assertThat(stored.subjects().get(0).subject().topScore()).isEqualTo(candidate.compositeScore());
    }

    @Test
    void aClearResultIsRecordedNotSkipped() {
        // No candidates and a COMPLETE search is a genuine NO_MATCH -- and it is still evidence.
        ScreenAName.Result result = screenAName.execute(command("k-2", subject("Harbin Osterfeld")));

        assertThat(result.outcome()).isEqualTo(DecisionBand.NO_MATCH);
        assertThat(repository.saved).hasSize(1);
    }

    @Test
    void candidatesAreRankedByScoreThenVersionIdAndAllAreKept() {
        views.put(key(1).entityVersionId(), view(1, "Zorvan Talmesk"));
        views.put(key(2).entityVersionId(), view(2, "Zorvan Talmesc"));
        views.put(key(3).entityVersionId(), view(3, "Harbin Osterfeld"));
        blockTo("Zorvan Talmesk", Completeness.COMPLETE, 3, 2, 1);

        ScreenAName.Result result = screenAName.execute(command("k-3", subject("Zorvan Talmesk")));

        var candidates = repository.findById(result.request().screeningId()).orElseThrow()
                .subjects().get(0).candidates();
        assertThat(candidates).hasSize(3); // I-8: a low-scoring candidate is still recorded
        assertThat(candidates).extracting(c -> c.rank()).containsExactly(1, 2, 3);
        assertThat(candidates).extracting(c -> c.compositeScore()).isSortedAccordingTo(
                java.util.Comparator.reverseOrder());
        assertThat(candidates.get(0).entityId()).isEqualTo(key(1).entityId());
    }

    // ---- fail closed (I-1) ----

    @Test
    void aPartialCandidateSetMakesTheSubjectAnError() {
        views.put(key(1).entityVersionId(), view(1, "Zorvan Talmesk"));
        blockTo("Zorvan Talmesk", Completeness.PARTIAL, 1);

        ScreenAName.Result result = screenAName.execute(command("k-4", subject("Zorvan Talmesk")));

        assertThat(result.outcome()).isEqualTo(DecisionBand.ERROR);
        assertThat(result.request().failureReason()).isPresent();
    }

    @Test
    void aFailingIndexIsAnErrorNeverAClear() {
        rebuild(scriptedIndex(probe -> {
            throw new IllegalStateException("index down");
        }));

        ScreenAName.Result result = screenAName.execute(command("k-5", subject("Zorvan Talmesk")));

        assertThat(result.outcome()).isEqualTo(DecisionBand.ERROR);
    }

    @Test
    void errorDominatesAcrossSubjects() {
        views.put(key(1).entityVersionId(), view(1, "Zorvan Talmesk"));
        blockTo("Zorvan Talmesk", Completeness.COMPLETE, 1); // STRONG on its own
        blockTo("Nyrell Hazenwood", Completeness.PARTIAL);    // ERROR

        ScreenAName.Result result = screenAName.execute(
                command("k-6", subject("Zorvan Talmesk"), subject("Nyrell Hazenwood"), subject("Harbin Osterfeld")));

        assertThat(result.outcome()).isEqualTo(DecisionBand.ERROR);
        assertThat(result.subjects()).extracting(s -> s.subject().outcome())
                .containsExactly(DecisionBand.STRONG_MATCH, DecisionBand.ERROR, DecisionBand.NO_MATCH);
    }

    @Test
    void strongDominatesPossibleDominatesNoMatchAcrossSubjects() {
        views.put(key(1).entityVersionId(), view(1, "Zorvan Talmesk"));
        blockTo("Zorvan Talmesk", Completeness.COMPLETE, 1);

        ScreenAName.Result result = screenAName.execute(
                command("k-7", subject("Harbin Osterfeld"), subject("Zorvan Talmesk")));

        assertThat(result.outcome()).isEqualTo(DecisionBand.STRONG_MATCH);
    }

    @Test
    void aTruncatedSearchThatFoundNothingCannotAssertAClear() {
        blockTo("Zorvan Talmesk", Completeness.TRUNCATED);

        ScreenAName.Result result = screenAName.execute(command("k-8", subject("Zorvan Talmesk")));

        assertThat(result.outcome()).isEqualTo(DecisionBand.ERROR);
    }

    @Test
    void aTruncatedSearchThatFoundAMatchStillReportsTheMatch() {
        views.put(key(1).entityVersionId(), view(1, "Zorvan Talmesk"));
        blockTo("Zorvan Talmesk", Completeness.TRUNCATED, 1);

        assertThat(screenAName.execute(command("k-9", subject("Zorvan Talmesk"))).outcome())
                .isEqualTo(DecisionBand.STRONG_MATCH);
    }

    @Test
    void aCandidateThatCannotBeAssembledMakesTheSubjectAnError() {
        blockTo("Zorvan Talmesk", Completeness.COMPLETE, 1); // no view registered for key(1)

        assertThat(screenAName.execute(command("k-10", subject("Zorvan Talmesk"))).outcome())
                .isEqualTo(DecisionBand.ERROR);
    }

    @Test
    void noActiveListVersionMeansScreeningIsUnavailableAndIsAudited() {
        activeLists = List.of();

        assertThatThrownBy(() -> screenAName.execute(command("k-11", subject("Zorvan Talmesk"))))
                .isInstanceOf(ScreeningUnavailableException.class);
        assertThat(repository.saved).isEmpty();
        assertThat(audit.recorded()).extracting(AuditEvent::eventType).containsExactly("SCREENING_UNAVAILABLE");
    }

    @Test
    void aListVersionNormalisedByAnotherPipelineVersionMakesScreeningUnavailable() {
        // I-10: the query and the stored names must have gone through the same normalisation.
        activeLists = List.of(new ActiveListVersion(
                LIST_VERSION, "test-source", "0.9.0", EnumSet.allOf(CanonicalSlot.class)));

        assertThatThrownBy(() -> screenAName.execute(command("k-pv", subject("Zorvan Talmesk"))))
                .isInstanceOf(ScreeningUnavailableException.class)
                .hasMessageContaining("re-ingest");
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void noApprovedConfigurationMeansScreeningIsUnavailable() {
        currentConfig = Optional.empty();

        assertThatThrownBy(() -> screenAName.execute(command("k-12", subject("Zorvan Talmesk"))))
                .isInstanceOf(ScreeningUnavailableException.class);
    }

    @Test
    void anInvalidConfigurationFailsScreeningRatherThanScoringWithIt() {
        // I-7: the registry rejects it; the screening never runs on an unvalidated config.
        currentConfig = Optional.of(new VersionedMatchConfig(
                CONFIG_ID, "default", MatchConfig.defaults().withThresholds(90, 70)));

        assertThatThrownBy(() -> screenAName.execute(command("k-13", subject("Zorvan Talmesk"))))
                .isInstanceOf(horus.matching.InvalidMatchConfigException.class);
        assertThat(repository.saved).isEmpty();
    }

    @Test
    void ifTheEvidenceCannotBeStoredNoResultIsReturned() {
        // A result nobody can audit later must not exist: the failure propagates.
        repository.failOnSave = true;

        assertThatThrownBy(() -> screenAName.execute(command("k-14", subject("Harbin Osterfeld"))))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---- input bounds & privacy ----

    @Test
    void anOverLongNameIsRejectedBeforeAnythingIsRecorded() {
        String tooLong = "a".repeat(501);

        assertThatThrownBy(() -> screenAName.execute(command("k-15", subject(tooLong))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.saved).isEmpty();
        assertThat(audit.recorded()).isEmpty();
    }

    @Test
    void aBlankOrMissingSubjectListIsRejected() {
        assertThatThrownBy(() -> command("k-16"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> screenAName.execute(command("k-17", subject("   "))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theAuditTrailCarriesIdsAndCountsNeverTheScreenedName() {
        // I-11
        views.put(key(1).entityVersionId(), view(1, "Zorvan Talmesk"));
        blockTo("Zorvan Talmesk", Completeness.COMPLETE, 1);

        screenAName.execute(command("k-18", subject("Zorvan Talmesk")));

        assertThat(audit.recorded()).hasSize(1);
        AuditEvent event = audit.recorded().get(0);
        assertThat(event.eventType()).isEqualTo("SCREENING_COMPLETED");
        assertThat(event.toString().toLowerCase()).doesNotContain("zorvan").doesNotContain("talmesk");
        assertThat(event.payload()).containsKeys("outcome", "subjects", "configVersionId", "listVersionIds");
    }

    @Test
    void errorMessagesNeverCarryTheScreenedName() {
        activeLists = List.of();

        assertThatThrownBy(() -> screenAName.execute(command("k-19", subject("Zorvan Talmesk"))))
                .satisfies(e -> assertThat(String.valueOf(e.getMessage())).doesNotContainIgnoringCase("zorvan"));
    }

    // ---- idempotency & determinism ----

    @Test
    void repeatingAnIdempotencyKeyReturnsTheStoredScreeningWithoutRescoring() {
        views.put(key(1).entityVersionId(), view(1, "Zorvan Talmesk"));
        blockTo("Zorvan Talmesk", Completeness.COMPLETE, 1);

        ScreenAName.Result first = screenAName.execute(command("same-key", subject("Zorvan Talmesk")));
        ScreenAName.Result again = screenAName.execute(command("same-key", subject("Zorvan Talmesk")));

        assertThat(again.replayed()).isTrue();
        assertThat(again.request().screeningId()).isEqualTo(first.request().screeningId());
        assertThat(repository.saved).hasSize(1);
        assertThat(audit.recorded()).hasSize(1);
    }

    @Test
    void sameInputsAndVersionsGiveTheSameExplanationsByteForByte() {
        // I-4: two independent screenings (distinct keys) of the same name score identically.
        views.put(key(1).entityVersionId(), view(1, "Zorvan Talmesk"));
        views.put(key(2).entityVersionId(), view(2, "Zorvan Talmesc"));
        blockTo("Zorvan Talmesk", Completeness.COMPLETE, 2, 1);

        var a = screenAName.execute(command("det-1", subject("Zorvan Talmesk")));
        var b = screenAName.execute(command("det-2", subject("Zorvan Talmesk")));

        var jsonA = repository.findById(a.request().screeningId()).orElseThrow().subjects().get(0).candidates()
                .stream().map(c -> c.explanationJson()).toList();
        var jsonB = repository.findById(b.request().screeningId()).orElseThrow().subjects().get(0).candidates()
                .stream().map(c -> c.explanationJson()).toList();
        assertThat(jsonA).isEqualTo(jsonB);
    }
}
