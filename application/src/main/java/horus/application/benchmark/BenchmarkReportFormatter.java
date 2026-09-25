package horus.application.benchmark;

import java.util.Locale;
import java.util.OptionalInt;

// Pure string building for the benchmark outputs (CSV and markdown). Fixed formats, Locale.ROOT
// numbers and stored order, so the same report always renders byte-identically (I-4). Nothing here
// can print a query name: the report does not hold one (I-11).
public final class BenchmarkReportFormatter {

    // Spec §19.3 wants a labelled corpus of 1,000 cases.
    private static final int SPEC_CORPUS_SIZE = 1000;

    private BenchmarkReportFormatter() {
    }

    public static String sweepCsv(RunBenchmark.Report report) {
        StringBuilder csv = new StringBuilder("threshold,tp,fp,fn,tn,precision,recall,f1,fp_per_1000_clear\n");
        for (RunBenchmark.ThresholdRow r : report.sweep()) {
            csv.append(r.threshold()).append(',').append(r.tp()).append(',').append(r.fp()).append(',')
                    .append(r.fn()).append(',').append(r.tn()).append(',').append(number(r.precision(), 4))
                    .append(',').append(number(r.recall(), 4)).append(',').append(number(r.f1(), 4)).append(',')
                    .append(number(r.falsePositivesPer1000(), 2)).append('\n');
        }
        return csv.toString();
    }

    public static String casesCsv(RunBenchmark.Report report) {
        StringBuilder csv = new StringBuilder("line,case_class,positive,outcome,top_score,expected_score\n");
        for (RunBenchmark.CaseOutcome o : report.caseOutcomes()) {
            csv.append(o.line()).append(',').append(o.caseClass()).append(',').append(o.positive()).append(',')
                    .append(o.outcome()).append(',').append(o.topScore()).append(',')
                    .append(o.expectedScore().isPresent() ? String.valueOf(o.expectedScore().getAsInt()) : "")
                    .append('\n');
        }
        return csv.toString();
    }

    public static String markdown(RunBenchmark.Report report) {
        RunBenchmark.ThresholdRow at = report.operating();
        StringBuilder md = new StringBuilder();
        md.append("# HORUS benchmark summary\n\n");
        md.append("Corpus: ").append(report.cases()).append(" cases (").append(report.positives())
                .append(" with an expected match, ").append(report.negatives()).append(" clear). Operating threshold: ")
                .append(report.operatingThreshold()).append(".\n\n");

        // I-2 / I-13: recall first, always.
        md.append("## Recall (the binding constraint)\n\n");
        md.append("- Recall at the operating threshold: ").append(number(at.recall(), 4)).append(" (")
                .append(at.tp()).append(" of ").append(at.tp() + at.fn()).append(" true positives found)\n");
        md.append("- Missed true positives: ").append(report.misses().size()).append("\n");
        md.append("- ERROR cases: ").append(report.errors())
                .append(" (screenings that could not complete; not counted as hits, misses or clears)\n\n");

        md.append("## Precision and alert volume\n\n");
        md.append("- Precision: ").append(number(at.precision(), 4)).append('\n');
        md.append("- F1: ").append(number(at.f1(), 4)).append('\n');
        md.append("- False positives per 1,000 clear names: ").append(number(at.falsePositivesPer1000(), 2)).append("\n\n");

        md.append("## Missed true positives\n\n");
        if (report.misses().isEmpty()) {
            md.append("None in this run.\n\n");
        } else {
            md.append("Each miss below must be reviewed individually against the corpus file by line number.\n\n");
            for (RunBenchmark.Miss miss : report.misses()) {
                md.append("- line ").append(miss.line()).append(" (").append(miss.caseClass()).append("): ")
                        .append(miss.reason());
                OptionalInt score = miss.expectedScore();
                if (score.isPresent()) {
                    md.append(", expected entity scored ").append(score.getAsInt());
                }
                md.append('\n');
            }
            md.append("\n**No recommendation to rely on HORUS may be made while an unexplained miss remains.**\n\n");
        }

        md.append("## Threshold sweep\n\n");
        md.append("| threshold | TP | FP | FN | TN | precision | recall | F1 | FP per 1,000 clear |\n");
        md.append("|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (RunBenchmark.ThresholdRow r : report.sweep()) {
            md.append("| ").append(r.threshold()).append(" | ").append(r.tp()).append(" | ").append(r.fp())
                    .append(" | ").append(r.fn()).append(" | ").append(r.tn()).append(" | ")
                    .append(number(r.precision(), 4)).append(" | ").append(number(r.recall(), 4)).append(" | ")
                    .append(number(r.f1(), 4)).append(" | ").append(number(r.falsePositivesPer1000(), 2))
                    .append(" |\n");
        }

        md.append("\n## Limits of this evidence\n\n");
        md.append("- This run used ").append(report.cases()).append(" cases; the specification (§19.3) asks for ")
                .append(String.format(Locale.ROOT, "%,d", SPEC_CORPUS_SIZE))
                .append(". Small corpora give wide error bars: one miss in ").append(Math.max(1, report.positives()))
                .append(" true positives moves recall by ")
                .append(number(1.0 / Math.max(1, report.positives()), 4)).append(".\n");
        md.append("- The threshold is a business risk decision owned by Compliance (§16.4); this report is the "
                + "evidence, not the decision.\n");
        return md.toString();
    }

    private static String number(double value, int decimals) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
