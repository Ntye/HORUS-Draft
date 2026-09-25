package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class NormalisedNameTest {

    private static NormalisedName valid() {
        return new NormalisedName(
                "Mohammed Al Sayed",
                "mohammed al sayed",
                List.of("mohammed", "al", "sayed"),
                List.of("al", "mohammed", "sayed"),
                List.of("MHMT", "AL", "SYT"),
                List.of("moh", "oha", "ham"),
                "LATIN");
    }

    @Test
    void acceptsAValidNormalisedName() {
        NormalisedName name = valid();

        assertThat(name.raw()).isEqualTo("Mohammed Al Sayed");
        assertThat(name.tokens()).containsExactly("mohammed", "al", "sayed");
        assertThat(name.sortedTokens()).containsExactly("al", "mohammed", "sayed");
    }

    @Test
    void rejectsNullRaw() {
        assertThatThrownBy(() -> new NormalisedName(
                null, "x", List.of(), List.of(), List.of(), List.of(), "LATIN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullNormalised() {
        assertThatThrownBy(() -> new NormalisedName(
                "raw", null, List.of(), List.of(), List.of(), List.of(), "LATIN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listsAreDefensivelyImmutable() {
        NormalisedName name = valid();

        assertThatThrownBy(() -> name.tokens().add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
