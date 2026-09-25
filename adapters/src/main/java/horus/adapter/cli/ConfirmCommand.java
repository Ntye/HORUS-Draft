package horus.adapter.cli;

import horus.application.ingestion.ConfirmStagedVersion;
import horus.domain.shared.ListVersionId;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "confirm", description = "Confirm or reject a STAGED list version that a load left "
        + "awaiting an operator decision -- an anomalous delta (CLAUDE.md §13.1) or a capability "
        + "deviation. CONFIRM promotes it; REJECT marks it FAILED. Both record the reason.")
public final class ConfirmCommand implements Callable<Integer> {

    @Option(names = "--list-version-id", required = true)
    private UUID listVersionId;

    @Option(names = "--decision", required = true, description = "CONFIRM or REJECT")
    private ConfirmStagedVersion.Decision decision;

    @Option(names = "--awaiting", required = true,
            description = "What the load was waiting on: ANOMALOUS_DELTA or CAPABILITY_DEVIATION. "
                    + "It is printed as the load's failureReason and in its audit event.")
    private ConfirmStagedVersion.AwaitingReason awaiting;

    @Option(names = "--reason", required = true,
            description = "Why this decision is correct. Recorded in the audit trail: this is the one "
                    + "path that overrides a control that has just fired, and an override with no "
                    + "recorded justification is indistinguishable from having no control.")
    private String reason;

    private final ConfirmStagedVersion confirmStagedVersion;

    public ConfirmCommand(ConfirmStagedVersion confirmStagedVersion) {
        this.confirmStagedVersion = confirmStagedVersion;
    }

    @Override
    public Integer call() {
        confirmStagedVersion.execute(new ConfirmStagedVersion.Command(
                new ListVersionId(listVersionId), decision, awaiting, reason, operator()));
        System.out.println("decision recorded: " + decision + " (" + awaiting + ")");
        return 0;
    }

    private static String operator() {
        String user = System.getenv("HORUS_OPERATOR");
        if (user == null || user.isBlank()) {
            throw new IllegalStateException("HORUS_OPERATOR must identify the confirming operator");
        }
        return user;
    }
}
