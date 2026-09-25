package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LegalFormNormalisationStepTest {

    private final LegalFormNormalisationStep step = new LegalFormNormalisationStep();

    @Test
    void foldsLimitedToTheCanonicalForm() {
        assertThat(step.apply("abc limited")).isEqualTo("abc ltd");
    }

    @Test
    void leavesTheAlreadyCanonicalFormUnchanged() {
        assertThat(step.apply("abc ltd")).isEqualTo("abc ltd");
    }

    @Test
    void foldsIncorporatedToInc() {
        assertThat(step.apply("abc incorporated")).isEqualTo("abc inc");
    }

    @Test
    void foldsCorporationToCorp() {
        assertThat(step.apply("abc corporation")).isEqualTo("abc corp");
    }

    @Test
    void foldsCompanyToCo() {
        assertThat(step.apply("abc company")).isEqualTo("abc co");
    }

    @Test
    void leavesSarlUnchanged() {
        assertThat(step.apply("abc sarl")).isEqualTo("abc sarl");
    }

    @Test
    void leavesPlcLlcAndGmbhUnchanged() {
        assertThat(step.apply("abc plc")).isEqualTo("abc plc");
        assertThat(step.apply("abc llc")).isEqualTo("abc llc");
        assertThat(step.apply("abc gmbh")).isEqualTo("abc gmbh");
    }

    @Test
    void hasAStableStepId() {
        assertThat(step.stepId()).isEqualTo("legal-form-normalisation");
    }
}
