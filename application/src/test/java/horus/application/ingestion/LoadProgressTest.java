package horus.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

// The arithmetic an operator will act on. If the remaining-time estimate is wrong, someone either
// abandons a load that was nearly done or waits all night for one that was never going to finish --
// which is exactly the judgement that could not be made during the first real attempt.
class LoadProgressTest {

    @Test
    void rateIsRecordsOverElapsedTime() {
        LoadProgress.Snapshot s = new LoadProgress.Snapshot(
                50_000, 50_000, 0, 0, 0, 1, 1, TimeUnit.SECONDS.toNanos(100), false);

        assertThat(s.recordsPerSecond()).isEqualTo(500.0);
    }

    @Test
    void rateIsZeroRatherThanInfiniteBeforeAnyTimeHasPassed() {
        LoadProgress.Snapshot s = new LoadProgress.Snapshot(10, 10, 0, 0, 0, 0, 0, 0, false);

        assertThat(s.recordsPerSecond()).isZero();
        assertThat(s.mapShare()).isZero();
    }

    // The number that decides whether to batch the inserts or speed up the narrative parser.
    @Test
    void mapShareSplitsAccountedTimeBetweenMappingAndPersistence() {
        LoadProgress.Snapshot mostlyMapping = new LoadProgress.Snapshot(
                100, 100, 0, 0, 0, 900, 100, TimeUnit.SECONDS.toNanos(1), false);
        LoadProgress.Snapshot mostlyPersisting = new LoadProgress.Snapshot(
                100, 100, 0, 0, 0, 100, 900, TimeUnit.SECONDS.toNanos(1), false);

        assertThat(mostlyMapping.mapShare()).isEqualTo(0.9);
        assertThat(mostlyPersisting.mapShare()).isEqualTo(0.1);
    }

    @Test
    void remainingTimeUsesTheCurrentRate() {
        // 500 rec/s, 5,990,394 expected, 990,394 done => 5,000,000 left => 10,000 seconds.
        LoadProgress.Snapshot s = new LoadProgress.Snapshot(
                990_394, 990_394, 0, 0, 0, 1, 1, TimeUnit.SECONDS.toNanos(1980), false);

        assertThat(s.recordsPerSecond()).isCloseTo(500.2, org.assertj.core.data.Offset.offset(0.1));
        assertThat(s.secondsRemaining(5_990_394)).hasValue(9_996);
    }

    @Test
    void remainingTimeIsAbsentWhenTheTotalIsUnknownOrAlreadyReached() {
        LoadProgress.Snapshot s = new LoadProgress.Snapshot(
                1_000, 1_000, 0, 0, 0, 1, 1, TimeUnit.SECONDS.toNanos(10), false);

        assertThat(s.secondsRemaining(0)).isEmpty();
        assertThat(s.secondsRemaining(1_000)).isEmpty();
    }

    @Test
    void theNoOpListenerAcceptsSnapshotsWithoutComplaint() {
        LoadProgress.NONE.onProgress(new LoadProgress.Snapshot(1, 1, 0, 0, 0, 1, 1, 1, true));
    }
}
