package horus.adapter.cli;

import horus.application.ingestion.AcknowledgeLoadReview;
import horus.domain.shared.ListVersionId;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "acknowledge-review", description = "Sign off a capability deviation that an "
        + "unattended load waived and promoted. Does not change any watchlist data -- the load is "
        + "already serving; this records that a human has seen why.")
public final class AcknowledgeReviewCommand implements Callable<Integer> {

    @Option(names = "--list-version-id", required = true)
    private UUID listVersionId;

    @Option(names = "--reason", required = true,
            description = "Why the waived deviation is acceptable. Recorded in the append-only audit "
                    + "trail: a waiver nobody has to justify is the same as no control at all.")
    private String reason;

    private final AcknowledgeLoadReview acknowledgeLoadReview;

    public AcknowledgeReviewCommand(AcknowledgeLoadReview acknowledgeLoadReview) {
        this.acknowledgeLoadReview = acknowledgeLoadReview;
    }

    @Override
    public Integer call() {
        acknowledgeLoadReview.execute(new AcknowledgeLoadReview.Command(
                new ListVersionId(listVersionId), reason, operator()));
        System.out.println("review acknowledged for list version " + listVersionId);
        return 0;
    }

    private static String operator() {
        String user = System.getenv("HORUS_OPERATOR");
        if (user == null || user.isBlank()) {
            throw new IllegalStateException("HORUS_OPERATOR must identify the acknowledging operator");
        }
        return user;
    }
}
