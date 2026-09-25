package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokeniserTest {

    private final Tokeniser tokeniser = new Tokeniser();

    @Test
    void splitsOnWhitespaceIntoOrderedTokens() {
        assertThat(tokeniser.tokenise("mohammed al sayed"))
                .containsExactly("mohammed", "al", "sayed");
    }

    @Test
    void returnsASingleTokenForASingleWord() {
        assertThat(tokeniser.tokenise("mohammed")).containsExactly("mohammed");
    }

    @Test
    void returnsAnEmptyListForBlankInput() {
        assertThat(tokeniser.tokenise("   ")).isEmpty();
    }
}
