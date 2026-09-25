package horus.adapter.cli;

import horus.application.benchmark.BenchmarkReportFormatter;
import horus.application.benchmark.RunBenchmark;
import horus.application.port.EntityIdResolver;
import horus.application.screening.ScreeningUnavailableException;
import horus.domain.shared.EntityId;
import horus.matching.InvalidMatchConfigException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

// Step 10. Runs a labelled corpus through the real screening use case and writes evidence:
//   benchmark-summary.md  recall first, then precision, misses, sweep, and the limits of the run
//   benchmark-sweep.csv   precision / recall across thresholds
//   benchmark-cases.csv   per-case outcome by corpus line number
// The corpus is the operator's own file (an agent never reads it, CLAUDE.md §3), taken from
// --corpus or HORUS_BENCHMARK_PATH. Exit codes: 0 no misses and no errors; 1 misses; 2 unresolved
// expected entities; 3 errors or screening unavailable; 64 bad input.
@Component
@Command(name = "benchmark", description = "Run a labelled corpus through screening and report recall/precision.")
public final class BenchmarkCommand implements Callable<Integer> {

    @Option(names = "--source", required = true, description = "Source id the expected entities belong to.")
    private String sourceId;

    @Option(names = "--corpus", description = "Labelled corpus TSV. Defaults to HORUS_BENCHMARK_PATH.")
    private Path corpus;

    @Option(names = "--out-dir", required = true, description = "Directory to write the report files into.")
    private Path outDir;

    @Option(names = "--profile", defaultValue = "default")
    private String profile;

    @Option(names = "--sweep", split = ",", description = "Thresholds to sweep, e.g. 40,50,60,70. Default: 0..100 by 5.")
    private List<Integer> sweep = new ArrayList<>();

    private final RunBenchmark runBenchmark;
    private final EntityIdResolver entityIdResolver;
    private final BenchmarkCorpusReader corpusReader = new BenchmarkCorpusReader();

    public BenchmarkCommand(RunBenchmark runBenchmark, EntityIdResolver entityIdResolver) {
        this.runBenchmark = runBenchmark;
        this.entityIdResolver = entityIdResolver;
    }

    @Override
    public Integer call() {
        Path corpusPath = corpus != null ? corpus : fromEnvironment();
        if (corpusPath == null) {
            System.err.println("give --corpus or set HORUS_BENCHMARK_PATH");
            return 64;
        }

        List<RunBenchmark.Case> cases = new ArrayList<>();
        List<Integer> unresolvedLines = new ArrayList<>();
        try {
            for (BenchmarkCorpusReader.Row row : corpusReader.read(corpusPath)) {
                Optional<EntityId> expected = Optional.empty();
                if (row.expectedSourceEntityId().isPresent()) {
                    expected = entityIdResolver.resolve(sourceId, row.expectedSourceEntityId().get());
                    if (expected.isEmpty()) {
                        unresolvedLines.add(row.line());
                        continue;
                    }
                }
                cases.add(new RunBenchmark.Case(row.line(), row.query(), row.entityType(), row.dob(),
                        row.countries(), row.identifiers(), expected, row.caseClass()));
            }
        } catch (IllegalArgumentException e) {
            System.err.println("invalid corpus: " + e.getMessage());
            return 64;
        }
        if (!unresolvedLines.isEmpty()) {
            // Dropping these would inflate recall; refuse to report at all.
            System.err.println("expected entity not found for corpus lines: " + unresolvedLines);
            return 2;
        }

        RunBenchmark.Report report;
        try {
            report = runBenchmark.execute(new RunBenchmark.Command(profile, operator(), cases, sweep));
        } catch (ScreeningUnavailableException | InvalidMatchConfigException e) {
            System.err.println("ERROR: screening unavailable: " + e.getMessage());
            return 3;
        }

        try {
            Files.createDirectories(outDir);
            write("benchmark-summary.md", BenchmarkReportFormatter.markdown(report));
            write("benchmark-sweep.csv", BenchmarkReportFormatter.sweepCsv(report));
            write("benchmark-cases.csv", BenchmarkReportFormatter.casesCsv(report));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the report files", e);
        }

        RunBenchmark.ThresholdRow at = report.operating();
        System.out.printf("recall: %.4f (%d of %d true positives) at threshold %d%n", at.recall(), at.tp(),
                at.tp() + at.fn(), report.operatingThreshold());
        System.out.printf("precision: %.4f  f1: %.4f  fp-per-1000-clear: %.2f%n", at.precision(), at.f1(),
                at.falsePositivesPer1000());
        System.out.println("cases: " + report.cases() + " (positives " + report.positives() + ", clear "
                + report.negatives() + ")");
        System.out.println("missedTruePositives: " + report.misses().size());
        System.out.println("errorCases: " + report.errors());
        System.out.println("written to: " + outDir.toAbsolutePath());
        if (report.errors() > 0) {
            return 3;
        }
        return report.misses().isEmpty() ? 0 : 1;
    }

    private void write(String fileName, String content) throws IOException {
        Files.writeString(outDir.resolve(fileName), content, StandardCharsets.UTF_8);
    }

    private static Path fromEnvironment() {
        String value = System.getenv("HORUS_BENCHMARK_PATH");
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static String operator() {
        String user = System.getenv("HORUS_OPERATOR");
        return user == null || user.isBlank() ? "svc-benchmark" : user;
    }
}
