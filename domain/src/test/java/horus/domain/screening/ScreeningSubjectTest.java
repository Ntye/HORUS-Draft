package horus.domain.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.DecisionBand;
import horus.domain.shared.QueryName;
import horus.domain.shared.ScreeningId;
import horus.domain.shared.ScreeningSubjectId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScreeningSubjectTest {

    @Test
    void acceptsAValidSubject() {
        ScreeningSubject subject = new ScreeningSubject(
                new ScreeningSubjectId(UUID.randomUUID()),
                new ScreeningId(UUID.randomUUID()),
                Optional.of("director-1"),
                new QueryName("Ahmed Ben Youssef"),
                3,
                84,
                DecisionBand.POSSIBLE_MATCH);

        assertThat(subject.candidateCount()).isEqualTo(3);
        assertThat(subject.topScore()).isEqualTo(84);
    }

    @Test
    void rejectsTopScoreAboveOneHundred() {
        assertThatThrownBy(() -> new ScreeningSubject(
                new ScreeningSubjectId(UUID.randomUUID()),
                new ScreeningId(UUID.randomUUID()),
                Optional.empty(),
                new QueryName("Ahmed Ben Youssef"),
                1,
                101,
                DecisionBand.STRONG_MATCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeCandidateCount() {
        assertThatThrownBy(() -> new ScreeningSubject(
                new ScreeningSubjectId(UUID.randomUUID()),
                new ScreeningId(UUID.randomUUID()),
                Optional.empty(),
                new QueryName("Ahmed Ben Youssef"),
                -1,
                0,
                DecisionBand.NO_MATCH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
