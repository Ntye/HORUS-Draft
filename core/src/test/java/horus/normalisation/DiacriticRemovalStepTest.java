package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiacriticRemovalStepTest {

    private final DiacriticRemovalStep step = new DiacriticRemovalStep();

    @Test
    void stripsCombiningMarksLeftByNfkdDecomposition() {
        assertThat(step.apply("é")).isEqualTo("e");
    }

    @Test
    void leavesTextWithoutDiacriticsUnchanged() {
        assertThat(step.apply("jose")).isEqualTo("jose");
    }

    @Test
    void hasAStableStepId() {
        assertThat(step.stepId()).isEqualTo("diacritic-removal");
    }
}
