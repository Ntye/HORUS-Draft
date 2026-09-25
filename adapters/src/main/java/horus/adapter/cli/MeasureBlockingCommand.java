package horus.adapter.cli;

import horus.application.blocking.MeasureBlockingRecall;
import horus.application.port.EntityIdResolver;
import horus.blocking.Strategy;
import horus.domain.shared.EntityId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

// Step 7: blocking recall measured in isolation. The corpus is the operator's own labelled file
// (never read by an agent, CLAUDE.md §3); the report names positions, not people (I-11).
@Component
@Command(name = "measure-blocking", description = "Measure candidate-generation recall on a labelled corpus.")
public final class MeasureBlockingCommand implements Callable<Integer> {

    @Option(names = "--source", required = true, description = "Source id the expected entities belong to.")
    private String sourceId;

    @Option(names = "--corpus", required = true, description = "TSV: query<TAB>sourceEntityId, one case per line.")
    private Path corpus;

    private final MeasureBlockingRecall measureBlockingRecall;
    private final EntityIdResolver entityIdResolver;
    private final BlockingCorpusReader corpusReader = new BlockingCorpusReader();

    public MeasureBlockingCommand(MeasureBlockingRecall measureBlockingRecall, EntityIdResolver entityIdResolver) {
        this.measureBlockingRecall = measureBlockingRecall;
        this.entityIdResolver = entityIdResolver;
    }

    @Override
    public Integer call() {
        List<BlockingCorpusReader.Row> rows = corpusReader.read(corpus);
        List<MeasureBlockingRecall.LabelledPair> pairs = new ArrayList<>();
        List<Integer> unresolvedLines = new ArrayList<>();
        for (BlockingCorpusReader.Row row : rows) {
            Optional<EntityId> expected = entityIdResolver.resolve(sourceId, row.sourceEntityId());
            if (expected.isPresent()) {
                pairs.add(new MeasureBlockingRecall.LabelledPair(row.query(), expected.get()));
            } else {
                unresolvedLines.add(row.line());
            }
        }
        if (!unresolvedLines.isEmpty()) {
            // Dropping these would inflate recall; refuse to report at all.
            System.err.println("expected entity not found for corpus lines: " + unresolvedLines);
            return 2;
        }

        MeasureBlockingRecall.Report report = measureBlockingRecall.execute(pairs);
        System.out.println("cases: " + report.cases());
        for (Strategy strategy : report.hitsByStrategy().keySet()) {
            System.out.printf("recall[%s]: %.4f (%d/%d)%n", strategy, report.recall(strategy),
                    report.hitsByStrategy().get(strategy), report.cases());
        }
        System.out.printf("recall[combined]: %.4f (%d/%d)%n", report.combinedRecall(), report.combinedHits(),
                report.cases());
        System.out.printf("meanCandidateSetSize: %.2f%n", report.meanCandidateSetSize());
        System.out.println("partialCases: " + report.partialCases());
        System.out.println("truncatedCases: " + report.truncatedCases());
        System.out.println("missedCaseIndexes (0-based, corpus order): " + report.missedCaseIndexes());
        // Non-zero when anything was missed or could not be completed: a run with unexplained
        // misses is not a pass (I-2).
        return report.missedCaseIndexes().isEmpty() && report.partialCases() == 0 ? 0 : 1;
    }
}
