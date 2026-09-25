package horus.adapter.cli;

import horus.application.ingestion.LoadWatchlistVersion;
import horus.application.ingestion.ReconciliationReport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

// CLAUDE.md §8: `bootRun --args="ingest --source=worldcheck"`, reading HORUS_FEED_PATH. Reads
// the file twice -- once to checksum, once to stream -- so LoadWatchlistVersion's idempotency
// short-circuit can run before any parsing starts. A known cost for the real feed (§6: ~229s
// per pass), deliberately accepted for this step; see Step 5 plan decision (c) for the sibling
// tradeoff on the active-entity snapshot.
@Component
@Command(name = "ingest", description = "Load a watchlist source file from HORUS_FEED_PATH and reconcile it "
        + "against the currently active version.")
public final class IngestCommand implements Callable<Integer> {

    private static final int SAMPLE_ORDINALS = 3;

    @Option(names = "--source", required = true, description = "Registered source id, e.g. worldcheck")
    private String sourceId;

    private final LoadWatchlistVersion loadWatchlistVersion;

    public IngestCommand(LoadWatchlistVersion loadWatchlistVersion) {
        this.loadWatchlistVersion = loadWatchlistVersion;
    }

    @Override
    public Integer call() throws IOException, NoSuchAlgorithmException {
        String feedPath = System.getenv("HORUS_FEED_PATH");
        if (feedPath == null || feedPath.isBlank()) {
            System.err.println("HORUS_FEED_PATH is not set");
            return 2;
        }
        Path path = Path.of(feedPath);
        String checksum = checksumOf(path);

        try (InputStream in = Files.newInputStream(path)) {
            LoadWatchlistVersion.Result result = loadWatchlistVersion.execute(new LoadWatchlistVersion.Command(
                    sourceId, in, path.getFileName().toString(), checksum, Optional.empty(), operator()));
            printReport(result);
            return switch (result.outcome()) {
                case PROMOTED, ALREADY_LOADED -> 0;
                case AWAITING_CONFIRMATION -> 3;
                case REJECTED -> 1;
            };
        }
    }

    private static String checksumOf(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path);
                DigestInputStream digestStream = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[8192];
            while (digestStream.read(buffer) != -1) {
                // Consumed for the side effect of updating the digest; the bytes themselves
                // are not needed on this pass.
            }
        }
        return "sha256:" + HexFormat.of().formatHex(digest.digest());
    }

    private static void printReport(LoadWatchlistVersion.Result result) {
        System.out.println("outcome: " + result.outcome());
        System.out.println("listVersionId: " + result.listVersionId().value());
        ReconciliationReport report = result.reconciliationReport();
        System.out.println("added=" + report.added() + " amended=" + report.amended()
                + " unchanged=" + report.unchanged() + " delisted=" + report.delisted()
                + " rejected=" + report.rejected().size());
        printRejectionReasons(report);
        result.failureReason().ifPresent(reason -> System.out.println("failureReason: " + reason));
    }

    // Without this the report says only HOW MANY records failed. The first real load of the real
    // feed rejected 5,979,934 of 5,990,394 and printed one number: diagnosing it needed a code
    // reading rather than the report (D12 B-6). Reason codes are a controlled vocabulary and the
    // examples are file ordinals, so nothing here can carry a name (I-11).
    private static void printRejectionReasons(ReconciliationReport report) {
        if (report.rejected().isEmpty()) {
            return;
        }
        Map<String, List<String>> samples = report.sampleOrdinalsByReason(SAMPLE_ORDINALS);
        System.out.println("rejections by reason:");
        report.reasonCounts().forEach((reason, count) -> System.out.println(
                "  " + count + "  " + reason + "  (e.g. record " + String.join(", ", samples.get(reason)) + ")"));
    }

    private static String operator() {
        String user = System.getenv("HORUS_OPERATOR");
        return user == null || user.isBlank() ? "svc-ingest" : user;
    }
}
