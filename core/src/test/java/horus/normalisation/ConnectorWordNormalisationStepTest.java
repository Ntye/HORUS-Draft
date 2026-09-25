package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConnectorWordNormalisationStepTest {

    private final ConnectorWordNormalisationStep step = new ConnectorWordNormalisationStep();

    @Test
    void spellsOutAnAmpersandAsAnd() {
        assertThat(step.apply("smith & jones")).isEqualTo("smith and jones");
    }

    @Test
    void leavesTextWithoutAnAmpersandUnchanged() {
        assertThat(step.apply("no ampersand here")).isEqualTo("no ampersand here");
    }

    @Test
    void doesNotTouchParticles() {
        assertThat(step.apply("ahmed bin yousef")).isEqualTo("ahmed bin yousef");
        assertThat(step.apply("de la cruz")).isEqualTo("de la cruz");
    }

    @Test
    void hasAStableStepId() {
        assertThat(step.stepId()).isEqualTo("connector-word-normalisation");
    }
}
