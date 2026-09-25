package horus.application.benchmark;

import horus.application.port.IdGenerator;
import horus.application.screening.LoadScreeningConfig;
import horus.application.screening.ScreenAName;
import horus.application.screening.Screener;
import horus.domain.audit.ActorKind;
import horus.domain.shared.DecisionBand;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityType;
import horus.matching.PartialDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;

// Evidence of match quality (spec §19.3, §16.4, I-13). Every labelled case goes through the SAME
// screening use case a consumer would call, so what is measured is what ships.
//
// Definitions (per case, at a threshold t, "score" = composite score 0-100):
//   positive case  : an expected entity is labelled.  TP if its candidate scored >= t, else FN.
//   negative case  : expected = NONE.                  FP if any candidate scored >= t, else TN.
//   ERROR case     : the screening could not complete. Counted apart -- neither a detection, a
//                    miss, a false positive nor a clear (I-1).
//   recall         : TP / (TP + FN)                     -- the binding constraint, reported first (I-2)
//   precision      : TP / (TP + FP)
//   FP per 1,000   : FP * 1000 / (FP + TN)              -- alerts per 1,000 CLEAR names
// A positive case that alerted on some OTHER entity is a miss (FN) and is not counted in FP: FP
// is defined over negative cases so that the per-1,000 rate has a clean denominator.
//
// Reports carry corpus line numbers, never query names (I-11).
public final class RunBenchmark {

    private static final List<Integer> DEFAULT_SWEEP = java.util.stream.IntStream.rangeClosed(0, 20)
            .map(i -> i * 5).boxed().toList();

    public record Case(
            int line,
            String query,
            Optional<EntityType> entityType,
            Optional<PartialDate> dob,
            Set<String> countries,
            Set<String> identifiers,
            Optional<EntityId> expected,
            String caseClass) {

        public Case {
            if (query == null || query.isBlank() || entityType == null || dob == null || countries == null
                    || identifiers == null || expected == null || caseClass == null || caseClass.isBlank()) {
                throw new IllegalArgumentException("a benchmark case needs a query, a class and non-null attributes");
            }
        }

        public boolean positive() {
            return expected.isPresent();
        }
    }

    public record Command(String profileId, String requestedBy, List<Case> cases, List<Integer> sweepThresholds) {

        public Command {
            if (profileId == null || profileId.isBlank() || requestedBy == null || requestedBy.isBlank()) {
                throw new IllegalArgumentException("profileId and requestedBy are required");
            }
            if (cases == null || cases.isEmpty()) {
                // An empty corpus has no recall; 0/0 must never read as a pass.
                throw new IllegalArgumentException("the corpus must contain at least one case");
            }
            if (sweepThresholds == null || sweepThresholds.stream().anyMatch(t -> t < 0 || t > 100)) {
                throw new IllegalArgumentException("sweep thresholds must be between 0 and 100");
            }
            cases = List.copyOf(cases);
            sweepThresholds = List.copyOf(sweepThresholds);
        }
    }

    public enum MissReason {
        /** The expected entity was never a candidate: a blocking (stage 1) loss. */
        NOT_A_CANDIDATE,
        /** It was a candidate but scored below the operating threshold: a scoring (stage 2) loss. */
        SCORED_BELOW_THRESHOLD
    }

    public record Miss(int line, String caseClass, MissReason reason, OptionalInt expectedScore) {
    }

    public record CaseOutcome(
            int line, String caseClass, boolean positive, DecisionBand outcome, int topScore,
            OptionalInt expectedScore) {

        public boolean errored() {
            return outcome == DecisionBand.ERROR;
        }
    }

    public record ThresholdRow(
            int threshold, int tp, int fp, int fn, int tn, double precision, double recall, double f1,
            double falsePositivesPer1000) {

        public static ThresholdRow of(int threshold, int tp, int fp, int fn, int tn) {
            double precision = tp + fp == 0 ? Double.NaN : tp / (double) (tp + fp);
            double recall = tp + fn == 0 ? Double.NaN : tp / (double) (tp + fn);
            double f1 = 2 * tp + fp + fn == 0 ? Double.NaN : (2.0 * tp) / (2 * tp + fp + fn);
            double fpr = fp + tn == 0 ? Double.NaN : (fp * 1000.0) / (fp + tn);
            return new ThresholdRow(threshold, tp, fp, fn, tn, precision, recall, f1, fpr);
        }
    }

    public record Report(
            int operatingThreshold,
            int cases,
            int positives,
            int negatives,
            int errors,
            ThresholdRow operating,
            List<ThresholdRow> sweep,
            List<Miss> misses,
            List<CaseOutcome> caseOutcomes) {

        public Report {
            sweep = List.copyOf(sweep);
            misses = List.copyOf(misses);
            caseOutcomes = List.copyOf(caseOutcomes);
        }
    }

    private final Screener screener;
    private final LoadScreeningConfig loadScreeningConfig;
    private final String pipelineVersion;
    private final IdGenerator idGenerator;

    public RunBenchmark(
            Screener screener, LoadScreeningConfig loadScreeningConfig, String pipelineVersion,
            IdGenerator idGenerator) {
        this.screener = screener;
        this.loadScreeningConfig = loadScreeningConfig;
        this.pipelineVersion = pipelineVersion;
        this.idGenerator = idGenerator;
    }

    public Report execute(Command command) {
        int operatingThreshold = loadScreeningConfig.execute(command.profileId(), pipelineVersion)
                .config().config().alertThreshold();
        String runId = idGenerator.newId().toString();

        List<CaseOutcome> outcomes = new ArrayList<>();
        for (Case benchmarkCase : command.cases()) {
            outcomes.add(screen(benchmarkCase, command, runId));
        }

        TreeSet<Integer> thresholds = new TreeSet<>(
                command.sweepThresholds().isEmpty() ? DEFAULT_SWEEP : command.sweepThresholds());
        thresholds.add(operatingThreshold);
        List<ThresholdRow> sweep = thresholds.stream().map(t -> rowAt(t, outcomes)).toList();
        ThresholdRow operating = sweep.stream().filter(r -> r.threshold() == operatingThreshold).findFirst()
                .orElseThrow();

        List<Miss> misses = new ArrayList<>();
        for (CaseOutcome o : outcomes) {
            if (o.positive() && !o.errored()
                    && !(o.expectedScore().isPresent() && o.expectedScore().getAsInt() >= operatingThreshold)) {
                misses.add(new Miss(o.line(), o.caseClass(),
                        o.expectedScore().isPresent() ? MissReason.SCORED_BELOW_THRESHOLD : MissReason.NOT_A_CANDIDATE,
                        o.expectedScore()));
            }
        }
        misses.sort(Comparator.comparingInt(Miss::line));

        int positives = (int) outcomes.stream().filter(CaseOutcome::positive).count();
        int errors = (int) outcomes.stream().filter(CaseOutcome::errored).count();
        return new Report(operatingThreshold, outcomes.size(), positives, outcomes.size() - positives, errors,
                operating, sweep, misses, outcomes);
    }

    private CaseOutcome screen(Case benchmarkCase, Command command, String runId) {
        ScreenAName.Command screening = new ScreenAName.Command(
                "benchmark", Optional.of("line-" + benchmarkCase.line()), command.profileId(), command.requestedBy(),
                ActorKind.OPERATOR, "benchmark-" + runId + "-" + benchmarkCase.line(),
                List.of(new ScreenAName.SubjectInput(Optional.empty(), benchmarkCase.query(),
                        benchmarkCase.entityType(), benchmarkCase.dob(), benchmarkCase.countries(),
                        benchmarkCase.identifiers())));
        ScreenAName.SubjectResult subject = screener.execute(screening).subjects().get(0);

        OptionalInt expectedScore = OptionalInt.empty();
        if (benchmarkCase.expected().isPresent()) {
            for (ScreenAName.ScoredCandidate candidate : subject.candidates()) {
                if (candidate.candidate().entityId().equals(benchmarkCase.expected().get())) {
                    expectedScore = OptionalInt.of(candidate.candidate().compositeScore());
                    break;
                }
            }
        }
        return new CaseOutcome(benchmarkCase.line(), benchmarkCase.caseClass(), benchmarkCase.positive(),
                subject.subject().outcome(), subject.subject().topScore(), expectedScore);
    }

    private static ThresholdRow rowAt(int threshold, List<CaseOutcome> outcomes) {
        int tp = 0;
        int fp = 0;
        int fn = 0;
        int tn = 0;
        for (CaseOutcome o : outcomes) {
            if (o.errored()) {
                continue; // I-1: neither a detection, a miss, a false positive nor a clear
            }
            if (o.positive()) {
                if (o.expectedScore().isPresent() && o.expectedScore().getAsInt() >= threshold) {
                    tp++;
                } else {
                    fn++;
                }
            } else if (o.topScore() >= threshold) {
                fp++;
            } else {
                tn++;
            }
        }
        return ThresholdRow.of(threshold, tp, fp, fn, tn);
    }
}
