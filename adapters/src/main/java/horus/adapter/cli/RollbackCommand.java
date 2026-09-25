package horus.adapter.cli;

import horus.application.ingestion.RollBackVersion;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Component
@Command(name = "rollback", description = "Roll back the active list version for a source to the "
        + "previously active one (CLAUDE.md §13.1).")
public final class RollbackCommand implements Callable<Integer> {

    @Option(names = "--source", required = true)
    private String sourceId;

    @Option(names = "--reason", required = true)
    private String reason;

    private final RollBackVersion rollBackVersion;

    public RollbackCommand(RollBackVersion rollBackVersion) {
        this.rollBackVersion = rollBackVersion;
    }

    @Override
    public Integer call() {
        RollBackVersion.Result result = rollBackVersion.execute(
                new RollBackVersion.Command(sourceId, reason, operator()));
        System.out.println("rolledBackFrom: " + result.rolledBackFrom().value());
        System.out.println("nowActive: " + result.nowActive().map(id -> id.value().toString()).orElse("none"));
        return 0;
    }

    private static String operator() {
        String user = System.getenv("HORUS_OPERATOR");
        if (user == null || user.isBlank()) {
            throw new IllegalStateException("HORUS_OPERATOR must identify the operator requesting rollback");
        }
        return user;
    }
}
