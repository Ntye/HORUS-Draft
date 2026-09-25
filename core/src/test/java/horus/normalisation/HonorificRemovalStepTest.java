package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HonorificRemovalStepTest {

    private final HonorificRemovalStep step = new HonorificRemovalStep();

    @Test
    void stripsALeadingTitleAndATrailingHonorific() {
        assertThat(step.apply("dr ahmed bey")).isEqualTo("ahmed");
    }

    @Test
    void stripsOnlyALeadingTitleWhenNoSuffixIsPresent() {
        assertThat(step.apply("mr john smith")).isEqualTo("john smith");
    }

    @Test
    void leavesANameWithNoHonorificsUnchanged() {
        assertThat(step.apply("john smith")).isEqualTo("john smith");
    }

    @Test
    void doesNotStripAWordThatOnlyContainsAnHonorificAsASubstring() {
        // "drake" starts with "dr" but is not the token "dr" -- must survive intact.
        assertThat(step.apply("drake jones")).isEqualTo("drake jones");
    }

    @Test
    void hasAStableStepId() {
        assertThat(step.stepId()).isEqualTo("honorific-removal");
    }
}
