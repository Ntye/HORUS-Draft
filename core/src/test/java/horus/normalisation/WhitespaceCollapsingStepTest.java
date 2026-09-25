package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WhitespaceCollapsingStepTest {

    private final WhitespaceCollapsingStep step = new WhitespaceCollapsingStep();

    @Test
    void collapsesRunsOfSpacesIntoOne() {
        assertThat(step.apply("a   b")).isEqualTo("a b");
    }

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        assertThat(step.apply("  leading and trailing  ")).isEqualTo("leading and trailing");
    }

    @Test
    void collapsesTabsAndNewlinesToo() {
        assertThat(step.apply("a\tb\nc")).isEqualTo("a b c");
    }

    @Test
    void hasAStableStepId() {
        assertThat(step.stepId()).isEqualTo("whitespace-collapsing");
    }
}
