package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PunctuationHandlingStepTest {

    private final PunctuationHandlingStep step = new PunctuationHandlingStep();

    @Test
    void replacesApostrophesAndHyphensWithSpaces() {
        assertThat(step.apply("O'Brien-Smith")).isEqualTo("O Brien Smith");
    }

    @Test
    void replacesCommonPunctuationWithSpaces() {
        assertThat(step.apply("Smith, Jones (Trading) Ltd.")).isEqualTo("Smith  Jones  Trading  Ltd ");
    }

    @Test
    void preservesAmpersandForTheConnectorWordStepToHandle() {
        assertThat(step.apply("Smith & Jones")).isEqualTo("Smith & Jones");
    }

    @Test
    void hasAStableStepId() {
        assertThat(step.stepId()).isEqualTo("punctuation-handling");
    }
}
