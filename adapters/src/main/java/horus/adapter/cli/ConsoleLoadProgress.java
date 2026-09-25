package horus.adapter.cli;

import horus.application.ingestion.LoadProgress;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Prints a load's progress to stdout, so a long run says what it is doing instead of nothing.
 *
 * <p>The first attempt at the real feed ran for ninety minutes in complete silence and then died.
 * Telling "stuck" from "slow" needed queries against a running process; telling which stage was slow
 * needed guesswork. One line every 25,000 records answers both.
 *
 * <p>Counts, ordinals and timings only -- there is nothing here that could carry a name (I-11).
 *
 * <p>Set {@code HORUS_EXPECTED_RECORDS} to print a remaining-time estimate: for World-Check that is
 * 5990394 (CLAUDE.md §6). It is an operator hint, not a property of the source, so it is not wired
 * into the adapter.
 */
@Component
public final class ConsoleLoadProgress implements LoadProgress {

    private final long expectedRecords;

    public ConsoleLoadProgress() {
        this(System.getenv("HORUS_EXPECTED_RECORDS"));
    }

    ConsoleLoadProgress(String expectedRecords) {
        long parsed = 0;
        if (expectedRecords != null && !expectedRecords.isBlank()) {
            try {
                parsed = Long.parseLong(expectedRecords.trim());
            } catch (NumberFormatException e) {
                parsed = 0; // an unusable hint is no hint; never a reason to fail a load
            }
        }
        this.expectedRecords = parsed;
    }

    @Override
    public void onProgress(Snapshot s) {
        StringBuilder line = new StringBuilder(s.finished() ? "  done:     " : "  progress: ");
        line.append(String.format("%,d records", s.records()));
        if (expectedRecords > 0) {
            line.append(String.format(" (%.1f%% of %,d)", 100.0 * s.records() / expectedRecords, expectedRecords));
        }
        line.append(String.format(", %s elapsed, %,.0f rec/s", duration(s.elapsedNanos()), s.recordsPerSecond()));
        line.append(String.format(", map %.0f%% / persist %.0f%%", 100 * s.mapShare(), 100 * (1 - s.mapShare())));
        line.append(String.format(", added=%,d rejected=%,d", s.added(), s.rejected()));

        OptionalLong remaining = s.secondsRemaining(expectedRecords);
        if (remaining.isPresent()) {
            line.append(", ~").append(duration(TimeUnit.SECONDS.toNanos(remaining.getAsLong()))).append(" left");
        }
        System.out.println(line);
        System.out.flush();
    }

    private static String duration(long nanos) {
        long totalSeconds = TimeUnit.NANOSECONDS.toSeconds(nanos);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format("%dh%02dm", hours, minutes)
                : String.format("%dm%02ds", minutes, seconds);
    }
}
