package horus.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.DecisionBand;
import horus.matching.PartialDate;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class ScreenCommandTest {

    @Test
    void exitCodesNeverLetAFailureLookLikeAClear() {
        assertThat(ScreenCommand.exitCodeFor(DecisionBand.NO_MATCH)).isEqualTo(0);
        assertThat(ScreenCommand.exitCodeFor(DecisionBand.POSSIBLE_MATCH)).isEqualTo(1);
        assertThat(ScreenCommand.exitCodeFor(DecisionBand.STRONG_MATCH)).isEqualTo(2);
        // I-1: ERROR is non-zero, and distinct from a clear.
        assertThat(ScreenCommand.exitCodeFor(DecisionBand.ERROR)).isEqualTo(3);
    }

    @Test
    void parsesYearYearMonthAndFullDates() {
        assertThat(ScreenCommand.parseDob("1980"))
                .isEqualTo(new PartialDate(1980, OptionalInt.empty(), OptionalInt.empty()));
        assertThat(ScreenCommand.parseDob("1980-05"))
                .isEqualTo(new PartialDate(1980, OptionalInt.of(5), OptionalInt.empty()));
        assertThat(ScreenCommand.parseDob("1980-05-17"))
                .isEqualTo(new PartialDate(1980, OptionalInt.of(5), OptionalInt.of(17)));
    }

    @Test
    void rejectsMalformedDates() {
        assertThatThrownBy(() -> ScreenCommand.parseDob("abcd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScreenCommand.parseDob("1980-13")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScreenCommand.parseDob("1980-05-17-01")).isInstanceOf(IllegalArgumentException.class);
    }
}
