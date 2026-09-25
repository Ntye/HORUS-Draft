package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NormalisationPipelineTest {

    private final NormalisationPipeline pipeline = NormalisationPipeline.standard();

    @Test
    void carriesItsPipelineVersion() {
        assertThat(pipeline.pipelineVersion()).isNotBlank();
    }

    @Test
    void stripsATitleAndAnHonorificEndToEnd() {
        // Spec §15.2's own worked example.
        NormalisedName result = pipeline.normalise("Dr. Ahmed Bey");

        assertThat(result.normalised()).isEqualTo("ahmed");
    }

    @Test
    void handlesPunctuationAndCaseEndToEnd() {
        // Spec §15.2's own worked example.
        NormalisedName result = pipeline.normalise("O'Brien-Smith");

        assertThat(result.normalised()).isEqualTo("o brien smith");
        assertThat(result.tokens()).containsExactly("o", "brien", "smith");
    }

    @Test
    void tokenisesIntoOrderedAndSortedForms() {
        // Spec §15.2's own worked example.
        NormalisedName result = pipeline.normalise("Mohammed Al Sayed");

        assertThat(result.tokens()).containsExactly("mohammed", "al", "sayed");
        assertThat(result.sortedTokens()).containsExactly("al", "mohammed", "sayed");
    }

    @Test
    void collapsesLegalFormVariantsToTheSameNormalisedString() {
        assertThat(pipeline.normalise("Sahara Trading Limited").normalised())
                .isEqualTo(pipeline.normalise("Sahara Trading Ltd.").normalised())
                .isEqualTo(pipeline.normalise("Sahara Trading LTD").normalised());
    }

    @Test
    void recognisesSarlAsAnAlreadyCanonicalLegalForm() {
        assertThat(pipeline.normalise("Comptoir General SARL").normalised())
                .endsWith("sarl");
    }

    @Test
    void preservesArabicAndLatinParticlesAsTokens() {
        assertThat(pipeline.normalise("Ahmed Bin Yousef Al Sayed").tokens())
                .contains("bin", "al");
        assertThat(pipeline.normalise("Ibn Khaldun").tokens()).contains("ibn");
        assertThat(pipeline.normalise("Willem de Vries van der Berg").tokens())
                .contains("de", "van");
    }

    @Test
    void transliteratesArabicScriptToLatinWithoutThrowing() {
        // Same common given name used across this test suite -- invented context, not a
        // real listed name (CLAUDE.md §3).
        NormalisedName result = pipeline.normalise("محمد");

        assertThat(result.normalised()).matches("[\\p{ASCII}]*");
        assertThat(result.script()).isEqualTo("ARABIC");
    }

    @Test
    void transliteratesCyrillicScriptToLatinWithoutThrowing() {
        NormalisedName result = pipeline.normalise("Иван Петров");

        assertThat(result.normalised()).matches("[\\p{ASCII}]*");
        assertThat(result.script()).isEqualTo("CYRILLIC");
    }

    @Test
    void stripsAccentsFromLatinScriptNames() {
        NormalisedName result = pipeline.normalise("José García");

        assertThat(result.normalised()).isEqualTo("jose garcia");
        assertThat(result.script()).isEqualTo("LATIN");
    }

    @Test
    void documentsRatherThanCorrectsHomoglyphSubstitution() {
        // Known, documented limitation: digit/letter look-alike substitution (leetspeak-style
        // evasion) is not detected by any of the 12 spec'd steps. This is the same philosophy
        // as the CJK limitation below -- proven and documented, not silently ignored.
        NormalisedName result = pipeline.normalise("M0hammed");

        assertThat(result.normalised()).contains("0");
    }

    @Test
    void documentsRatherThanPretendsToHandleCjkScript() {
        // A common, widely-shared given name (millions of people share it), invented context.
        // CJK transliteration quality is not verified or tuned -- §15.2's WARNINGS explicitly
        // call for documenting this rather than pretending to handle it.
        NormalisedName result = pipeline.normalise("王伟");

        assertThat(result.normalised()).isNotBlank();
    }

    @Test
    void overNormalisationGuardAliMustNotEqualAlia() {
        NormalisedName ali = pipeline.normalise("Ali");
        NormalisedName alia = pipeline.normalise("Alia");

        assertThat(ali.normalised()).isNotEqualTo(alia.normalised());
        assertThat(ali.tokens()).isNotEqualTo(alia.tokens());
    }
}
