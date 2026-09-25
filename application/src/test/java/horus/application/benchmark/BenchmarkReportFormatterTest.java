package horus.application.benchmark;

import static org.assertj.core.api.Assertions.assertThat;

import horus.domain.shared.DecisionBand;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class BenchmarkReportFormatterTest {

    private static RunBenchmark.ThresholdRow row(int threshold, int tp, int fp, int fn, int tn) {
        return RunBenchmark.ThresholdRow.of(threshold, tp, fp, fn, tn);
    }

    private static RunBenchmark.Report report(List<RunBenchmark.Miss> misses, int errors) {
        RunBenchmark.ThresholdRow operating = row(55, 8, 2, 2, 88);
        return new RunBenchmark.Report(55, 100, 10, 90, errors, operating,
                List.of(row(40, 9, 6, 1, 84), operating, row(70, 5, 0, 5, 90)),
                misses,
                List.of(new RunBenchmark.CaseOutcome(2, "TRUE_MATCH", true, DecisionBand.STRONG_MATCH, 90,
                                OptionalInt.of(90)),
                        new RunBenchmark.CaseOutcome(3, "CLEAR", false, DecisionBand.NO_MATCH, 12, OptionalInt.empty())));
    }

    @Test
    void theSweepCsvHasAHeaderAndOneRowPerThreshold() {
        String csv = BenchmarkReportFormatter.sweepCsv(report(List.of(), 0));

        String[] lines = csv.strip().split("\n");
        assertThat(lines[0]).isEqualTo("threshold,tp,fp,fn,tn,precision,recall,f1,fp_per_1000_clear");
        assertThat(lines).hasSize(4);
        assertThat(lines[2]).startsWith("55,8,2,2,88,0.8000,0.8000,0.8000,");
    }

    @Test
    void theCasesCsvCarriesPositionsAndScoresButNoNames() {
        String csv = BenchmarkReportFormatter.casesCsv(report(List.of(), 0));

        assertThat(csv).startsWith("line,case_class,positive,outcome,top_score,expected_score\n");
        assertThat(csv).contains("2,TRUE_MATCH,true,STRONG_MATCH,90,90").contains("3,CLEAR,false,NO_MATCH,12,");
    }

    @Test
    void theMarkdownLeadsWithRecallAndListsEveryMiss() {
        // I-2: recall is reported first, always.
        List<RunBenchmark.Miss> misses = List.of(
                new RunBenchmark.Miss(7, "TRANSLITERATION", RunBenchmark.MissReason.NOT_A_CANDIDATE, OptionalInt.empty()),
                new RunBenchmark.Miss(9, "VARIANT", RunBenchmark.MissReason.SCORED_BELOW_THRESHOLD, OptionalInt.of(40)));

        String md = BenchmarkReportFormatter.markdown(report(misses, 0));

        assertThat(md.indexOf("Recall")).isLessThan(md.indexOf("Precision"));
        assertThat(md).contains("Missed true positives: 2").contains("line 7").contains("line 9")
                .contains("NOT_A_CANDIDATE").contains("SCORED_BELOW_THRESHOLD");
    }

    @Test
    void anyUnexplainedMissBlocksAnyRecommendationToRelyOnTheSystem() {
        String withMiss = BenchmarkReportFormatter.markdown(report(
                List.of(new RunBenchmark.Miss(7, "VARIANT", RunBenchmark.MissReason.NOT_A_CANDIDATE,
                        OptionalInt.empty())), 0));
        String clean = BenchmarkReportFormatter.markdown(report(List.of(), 0));

        assertThat(withMiss).containsIgnoringCase("no recommendation to rely");
        assertThat(clean).doesNotContainIgnoringCase("no recommendation to rely");
    }

    @Test
    void errorsAreCalledOutAndTheCorpusSizeCaveatIsStated() {
        String md = BenchmarkReportFormatter.markdown(report(List.of(), 3));

        assertThat(md).contains("ERROR cases: 3");
        // The spec (§19.3) wants 1,000 labelled cases; a smaller corpus must say so.
        assertThat(md).contains("100 cases").containsIgnoringCase("1,000");
    }
}
