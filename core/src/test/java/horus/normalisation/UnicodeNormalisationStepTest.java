package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UnicodeNormalisationStepTest {

    private final UnicodeNormalisationStep step = new UnicodeNormalisationStep();

    @Test
    void caseFoldsUpperCaseInput() {
        assertThat(step.apply("JOSE")).isEqualTo("jose");
    }

    @Test
    void decomposesPrecomposedAccentedCharactersIntoBaseAndCombiningMark() {
        String result = step.apply("É"); // Latin Capital Letter E with Acute

        // NFKD decomposition only -- the combining mark is still present; diacritic
        // removal is a separate step (2) so this step's output is verifiably decomposed.
        assertThat(result).isEqualTo("é");
    }

    @Test
    void hasAStableStepId() {
        assertThat(step.stepId()).isEqualTo("unicode-normalisation");
    }
}
