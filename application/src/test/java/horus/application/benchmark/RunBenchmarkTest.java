package horus.application.benchmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.application.port.ActiveListVersion;
import horus.application.port.MatchConfigRepository;
import horus.application.port.VersionedMatchConfig;
import horus.application.screening.LoadScreeningConfig;
import horus.application.screening.ScreenAName;
import horus.application.screening.Screener;
import horus.domain.audit.ActorKind;
import horus.domain.screening.ScreeningCandidate;
import horus.domain.screening.ScreeningRequest;
import horus.domain.screening.ScreeningSubject;
import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.DecisionBand;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.ListVersionId;
import horus.domain.shared.QueryName;
import horus.domain.shared.ScreeningCandidateId;
import horus.domain.shared.ScreeningId;
import horus.domain.shared.ScreeningSubjectId;
import horus.matching.MatchConfig;
import horus.matching.Registry;
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
import org.junit.jupiter.api.Test;

// Synthetic corpus only (CLAUDE.md §3). The screener is scripted so every expected metric below
// can be checked by hand; the maths, not the matcher, is what is under test.
class RunBenchmarkTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final int ALERT = MatchConfig.defaults().alertThreshold(); // 55

    private static EntityId entity(long n) {
        return new EntityId(new UUID(0, n));
    }

    /** The scripted answer for one case: the candidate scores it returns, by entity number. */
    private final Map<String, Map<Long, Integer>> scoresByQueryMarker = new HashMap<>();
    private final Map<String, DecisionBand> forcedOutcome = new HashMap<>();
    private final List<ScreenAName.Command> received = new ArrayList<>();

    private Screener screener() {
        return command -> {
            received.add(command);
            String query = command.subjects().get(0).name();
            Map<Long, Integer> scores = scoresByQueryMarker.getOrDefault(query, Map.of());
            ScreeningId screeningId = new ScreeningId(UUID.randomUUID());
            ScreeningSubjectId subjectId = new ScreeningSubjectId(UUID.randomUUID());
            List<Map.Entry<Long, Integer>> sorted = new ArrayList<>(scores.entrySet());
            sorted.sort(Map.Entry.<Long, Integer>comparingByValue().reversed());
            List<ScreenAName.ScoredCandidate> candidates = new ArrayList<>();
            int rank = 1;
            for (var entry : sorted) {
                int score = entry.getValue();
                DecisionBand band = score >= 75 ? DecisionBand.STRONG_MATCH
                        : score >= ALERT ? DecisionBand.POSSIBLE_MATCH : DecisionBand.NO_MATCH;
                candidates.add(new ScreenAName.ScoredCandidate(new ScreeningCandidate(
                        new ScreeningCandidateId(UUID.randomUUID()), subjectId, "s", entity(entry.getKey()),
                        new EntityVersionId(UUID.randomUUID()), score, "{}", "1", band, rank++), Optional.empty()));
            }
            int top = sorted.isEmpty() ? 0 : sorted.get(0).getValue();
            DecisionBand outcome = forcedOutcome.getOrDefault(query,
                    top >= 75 ? DecisionBand.STRONG_MATCH : top >= ALERT ? DecisionBand.POSSIBLE_MATCH
                            : DecisionBand.NO_MATCH);
            ScreeningRequest request = new ScreeningRequest(screeningId, "benchmark", Optional.empty(), "default",
                    "op", NOW, Set.of(new ListVersionId(UUID.randomUUID())), UUID.randomUUID(), "1.0.0", outcome,
                    Optional.empty(), command.idempotencyKey());
            ScreeningSubject subject = new ScreeningSubject(subjectId, screeningId, Optional.empty(),
                    new QueryName(query), candidates.size(), top, outcome);
            return new ScreenAName.Result(request,
                    List.of(new ScreenAName.SubjectResult(subject, candidates, Optional.empty())), false);
        };
    }

    private RunBenchmark benchmark() {
        MatchConfigRepository configs = new MatchConfigRepository() {
            @Override
            public Optional<VersionedMatchConfig> findCurrent(String profileId, Instant asOf) {
                return Optional.of(new VersionedMatchConfig(UUID.randomUUID(), "default", MatchConfig.defaults()));
            }

            @Override
            public void save(VersionedMatchConfig config, String createdBy, Instant createdAt, String approvedBy,
                    Instant effectiveFrom) {
                throw new UnsupportedOperationException();
            }
        };
        var lists = List.of(new ActiveListVersion(new ListVersionId(new UUID(7, 7)), "s", "1.0.0",
                EnumSet.allOf(CanonicalSlot.class)));
        return new RunBenchmark(screener(), new LoadScreeningConfig(configs, () -> lists, Registry.standard(), CLOCK),
                "1.0.0", () -> new UUID(5, 5));
    }

    private static RunBenchmark.Case positive(int line, String query, long expected, String caseClass) {
        return new RunBenchmark.Case(line, query, Optional.empty(), Optional.empty(), Set.of(), Set.of(),
                Optional.of(entity(expected)), caseClass);
    }

    private static RunBenchmark.Case negative(int line, String query, String caseClass) {
        return new RunBenchmark.Case(line, query, Optional.empty(), Optional.empty(), Set.of(), Set.of(),
                Optional.empty(), caseClass);
    }

    private RunBenchmark.Command command(List<Integer> sweep, RunBenchmark.Case... cases) {
        return new RunBenchmark.Command("default", "operator", List.of(cases), sweep);
    }

    @Test
    void computesRecallPrecisionAndFalsePositivesByHand() {
        // Positives: P1 expected entity 1 scores 90; P2 expected 2 scores 60; P3 expected 3 is not a candidate.
        scoresByQueryMarker.put("p1", Map.of(1L, 90));
        scoresByQueryMarker.put("p2", Map.of(2L, 60, 9L, 40));
        scoresByQueryMarker.put("p3", Map.of(8L, 30));
        // Negatives: N1 top 20 (clear); N2 top 70 (a false alert); N3 nothing found.
        scoresByQueryMarker.put("n1", Map.of(7L, 20));
        scoresByQueryMarker.put("n2", Map.of(6L, 70));

        RunBenchmark.Report report = benchmark().execute(command(List.of(50, 65, 95),
                positive(2, "p1", 1, "TRUE_MATCH"), positive(3, "p2", 2, "TRUE_MATCH"),
                positive(4, "p3", 3, "TRUE_MATCH"), negative(5, "n1", "CLEAR"), negative(6, "n2", "NEAR_MISS"),
                negative(7, "n3", "CLEAR")));

        assertThat(report.cases()).isEqualTo(6);
        assertThat(report.positives()).isEqualTo(3);
        assertThat(report.negatives()).isEqualTo(3);
        assertThat(report.operatingThreshold()).isEqualTo(ALERT);

        // Operating point (alert = 55): alerts are P1 (90), P2 (60), N2 (70).
        // TP = P1, P2 = 2; FP = N2 = 1; FN = P3 = 1; TN = N1, N3 = 2.
        RunBenchmark.ThresholdRow at = report.operating();
        assertThat(at.tp()).isEqualTo(2);
        assertThat(at.fp()).isEqualTo(1);
        assertThat(at.fn()).isEqualTo(1);
        assertThat(at.tn()).isEqualTo(2);
        assertThat(at.recall()).isEqualTo(2.0 / 3.0);
        assertThat(at.precision()).isEqualTo(2.0 / 3.0);
        assertThat(at.f1()).isEqualTo(2.0 / 3.0);
        // 1 of 3 negatives alerted -> 333.33 per 1,000 clear names.
        assertThat(at.falsePositivesPer1000()).isEqualTo(1000.0 / 3.0);
    }

    @Test
    void sweepRowsAreSortedDistinctAndIncludeTheOperatingThreshold() {
        scoresByQueryMarker.put("p1", Map.of(1L, 90));
        scoresByQueryMarker.put("n1", Map.of(7L, 70));

        RunBenchmark.Report report = benchmark().execute(command(List.of(95, 50, 50, 65),
                positive(2, "p1", 1, "TRUE_MATCH"), negative(3, "n1", "CLEAR")));

        assertThat(report.sweep()).extracting(RunBenchmark.ThresholdRow::threshold)
                .containsExactly(50, ALERT, 65, 95);
        // Raising the threshold can only lose recall, never gain it.
        assertThat(report.sweep()).extracting(RunBenchmark.ThresholdRow::recall).isSortedAccordingTo(
                java.util.Comparator.reverseOrder());
        RunBenchmark.ThresholdRow at95 = report.sweep().get(3);
        assertThat(at95.tp()).isEqualTo(0);
        assertThat(at95.fn()).isEqualTo(1);
    }

    @Test
    void everyMissedTruePositiveIsListedWithItsReason() {
        scoresByQueryMarker.put("p1", Map.of(1L, 90));
        scoresByQueryMarker.put("p2", Map.of(2L, 40));           // scored, but below the operating threshold
        scoresByQueryMarker.put("p3", Map.of(8L, 30));           // expected entity was never a candidate

        RunBenchmark.Report report = benchmark().execute(command(List.of(),
                positive(2, "p1", 1, "TRUE_MATCH"), positive(3, "p2", 2, "VARIANT"),
                positive(4, "p3", 3, "TRANSLITERATION")));

        assertThat(report.misses()).hasSize(2);
        RunBenchmark.Miss below = report.misses().get(0);
        assertThat(below.line()).isEqualTo(3);
        assertThat(below.caseClass()).isEqualTo("VARIANT");
        assertThat(below.reason()).isEqualTo(RunBenchmark.MissReason.SCORED_BELOW_THRESHOLD);
        assertThat(below.expectedScore()).hasValue(40);
        RunBenchmark.Miss notBlocked = report.misses().get(1);
        assertThat(notBlocked.line()).isEqualTo(4);
        assertThat(notBlocked.reason()).isEqualTo(RunBenchmark.MissReason.NOT_A_CANDIDATE);
        assertThat(notBlocked.expectedScore()).isEmpty();
    }

    @Test
    void errorCasesAreReportedSeparatelyAndNeverCountedAsHitsMissesOrClears() {
        // I-1: a screening that could not complete is neither a detection nor a clear.
        scoresByQueryMarker.put("p1", Map.of(1L, 90));
        forcedOutcome.put("p2", DecisionBand.ERROR);
        forcedOutcome.put("n1", DecisionBand.ERROR);

        RunBenchmark.Report report = benchmark().execute(command(List.of(),
                positive(2, "p1", 1, "TRUE_MATCH"), positive(3, "p2", 2, "TRUE_MATCH"),
                negative(4, "n1", "CLEAR"), negative(5, "n2", "CLEAR")));

        assertThat(report.errors()).isEqualTo(2);
        RunBenchmark.ThresholdRow at = report.operating();
        assertThat(at.tp()).isEqualTo(1);
        assertThat(at.fn()).isZero();       // the errored positive is not a miss
        assertThat(at.tn()).isEqualTo(1);   // the errored negative is not a clear
        assertThat(report.misses()).isEmpty();
    }

    @Test
    void perCaseOutcomesCarryNoQueryNames() {
        // I-11: the reports are shareable evidence.
        scoresByQueryMarker.put("secret-query", Map.of(1L, 90));

        RunBenchmark.Report report = benchmark().execute(command(List.of(),
                positive(2, "secret-query", 1, "TRUE_MATCH")));

        assertThat(report.toString()).doesNotContain("secret-query");
        assertThat(report.caseOutcomes()).hasSize(1);
        assertThat(report.caseOutcomes().get(0).toString()).doesNotContain("secret-query");
    }

    @Test
    void everyCaseIsScreenedThroughTheRealUseCaseWithADistinctIdempotencyKey() {
        scoresByQueryMarker.put("p1", Map.of(1L, 90));

        benchmark().execute(command(List.of(),
                positive(2, "p1", 1, "TRUE_MATCH"), negative(3, "n1", "CLEAR")));

        assertThat(received).hasSize(2);
        assertThat(received).extracting(ScreenAName.Command::idempotencyKey).doesNotHaveDuplicates()
                .allSatisfy(key -> assertThat(key).startsWith("benchmark-"));
        assertThat(received).allSatisfy(c -> {
            assertThat(c.actorKind()).isEqualTo(ActorKind.OPERATOR);
            assertThat(c.subjects()).hasSize(1);
        });
    }

    @Test
    void anEmptyOrOnePolarityCorpusIsRejectedRatherThanReportedAsPerfect() {
        assertThatThrownBy(() -> new RunBenchmark.Command("default", "op", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        scoresByQueryMarker.put("p1", Map.of(1L, 90));
        // With no negatives a false-positive rate is undefined; the run must say so, not report 0.
        RunBenchmark.Report report = benchmark().execute(command(List.of(), positive(2, "p1", 1, "TRUE_MATCH")));
        assertThat(report.negatives()).isZero();
        assertThat(report.operating().falsePositivesPer1000()).isNaN();
    }

    @Test
    void identicalRunsGiveIdenticalReports() {
        // I-4
        scoresByQueryMarker.put("p1", Map.of(1L, 90));
        scoresByQueryMarker.put("n1", Map.of(7L, 20));
        RunBenchmark.Command cmd = command(List.of(50, 60),
                positive(2, "p1", 1, "TRUE_MATCH"), negative(3, "n1", "CLEAR"));

        assertThat(benchmark().execute(cmd)).isEqualTo(benchmark().execute(cmd));
    }
}
