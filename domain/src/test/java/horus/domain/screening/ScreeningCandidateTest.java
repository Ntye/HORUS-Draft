package horus.domain.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.DecisionBand;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.ScreeningCandidateId;
import horus.domain.shared.ScreeningSubjectId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScreeningCandidateTest {

    private static ScreeningCandidate valid(int rank) {
        return new ScreeningCandidate(
                new ScreeningCandidateId(UUID.randomUUID()),
                new ScreeningSubjectId(UUID.randomUUID()),
                "worldcheck",
                new EntityId(UUID.randomUUID()),
                new EntityVersionId(UUID.randomUUID()),
                84,
                "{\"featureScores\":{}}",
                "1.0",
                DecisionBand.POSSIBLE_MATCH,
                rank);
    }

    @Test
    void acceptsAValidCandidate() {
        ScreeningCandidate candidate = valid(1);

        assertThat(candidate.compositeScore()).isEqualTo(84);
        assertThat(candidate.rank()).isEqualTo(1);
    }

    @Test
    void rejectsRankBelowOne() {
        assertThatThrownBy(() -> valid(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCompositeScoreOutOfRange() {
        assertThatThrownBy(() -> new ScreeningCandidate(
                new ScreeningCandidateId(UUID.randomUUID()),
                new ScreeningSubjectId(UUID.randomUUID()),
                "worldcheck",
                new EntityId(UUID.randomUUID()),
                new EntityVersionId(UUID.randomUUID()),
                101,
                "{}",
                "1.0",
                DecisionBand.STRONG_MATCH,
                1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankExplanationJson() {
        assertThatThrownBy(() -> new ScreeningCandidate(
                new ScreeningCandidateId(UUID.randomUUID()),
                new ScreeningSubjectId(UUID.randomUUID()),
                "worldcheck",
                new EntityId(UUID.randomUUID()),
                new EntityVersionId(UUID.randomUUID()),
                50,
                " ",
                "1.0",
                DecisionBand.NO_MATCH,
                1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
