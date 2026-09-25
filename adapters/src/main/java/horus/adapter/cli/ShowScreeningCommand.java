package horus.adapter.cli;

import horus.application.port.ScreeningRecord;
import horus.application.port.ScreeningRepository;
import horus.domain.shared.ScreeningId;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

// Reads a stored screening back exactly as it was recorded: provenance, outcomes, and (with
// --explain) each candidate's stored explanation. Nothing is recomputed (I-3); the screened name
// is not printed (I-11).
@Component
@Command(name = "show-screening", description = "Show a stored screening and its evidence.")
public final class ShowScreeningCommand implements Callable<Integer> {

    @Option(names = "--id", required = true, description = "The screeningId to show.")
    private UUID id;

    @Option(names = "--explain", description = "Also print each candidate's stored explanation JSON.")
    private boolean explain;

    private final ScreeningRepository screeningRepository;

    public ShowScreeningCommand(ScreeningRepository screeningRepository) {
        this.screeningRepository = screeningRepository;
    }

    @Override
    public Integer call() {
        var found = screeningRepository.findById(new ScreeningId(id));
        if (found.isEmpty()) {
            System.err.println("no such screening");
            return 1;
        }
        ScreeningRecord record = found.get();
        var request = record.request();
        System.out.println("screeningId: " + request.screeningId().value());
        System.out.println("outcome: " + request.outcome());
        System.out.println("requestedAt: " + request.requestedAt());
        System.out.println("requestedBy: " + request.requestedBy());
        System.out.println("listVersionIds: " + request.listVersionIds().stream()
                .map(v -> v.value().toString()).toList());
        System.out.println("configVersionId: " + request.configVersionId());
        System.out.println("pipelineVersion: " + request.pipelineVersion());
        request.failureReason().ifPresent(reason -> System.out.println("failure: " + reason));
        int position = 1;
        for (ScreeningRecord.SubjectRecord subject : record.subjects()) {
            System.out.println("subject " + position++ + ": " + subject.subject().outcome() + ", candidates="
                    + subject.subject().candidateCount() + ", topScore=" + subject.subject().topScore());
            for (var candidate : subject.candidates()) {
                System.out.println("  #" + candidate.rank() + " score=" + candidate.compositeScore() + " "
                        + candidate.decisionBand() + " entity=" + candidate.entityId().value());
                if (explain) {
                    System.out.println("    " + candidate.explanationJson());
                }
            }
        }
        return 0;
    }
}
