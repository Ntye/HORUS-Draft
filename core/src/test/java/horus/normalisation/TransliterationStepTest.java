package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransliterationStepTest {

    private final TransliterationStep step = new TransliterationStep();

    @Test
    void transliteratesArabicScriptToAsciiLatin() {
        // A common given name, invented context -- see CLAUDE.md §3, never a real listed name.
        // The exact transliteration ICU4J produces isn't pinned here (that's ICU4J's contract,
        // not ours); what this step owns is that no Arabic codepoints survive.
        String result = step.apply("محمد"); // "Mohammed" in Arabic script

        assertThat(result).matches("[\\p{ASCII}]+");
    }

    @Test
    void transliteratesCyrillicScriptToAsciiLatin() {
        String result = step.apply("Иван"); // "Ivan" in Cyrillic script

        assertThat(result).matches("[\\p{ASCII}]+");
    }

    @Test
    void leavesPlainAsciiLatinTextUnchanged() {
        assertThat(step.apply("Mohammed")).isEqualTo("Mohammed");
    }

    @Test
    void hasAStableStepId() {
        assertThat(step.stepId()).isEqualTo("transliteration");
    }
}
