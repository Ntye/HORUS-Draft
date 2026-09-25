package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TrigramGeneratorTest {

    private final TrigramGenerator generator = new TrigramGenerator();

    @Test
    void generatesOneTrigramForAThreeCharacterString() {
        assertThat(generator.generate("abc")).containsExactly("abc");
    }

    @Test
    void slidesAWindowOfThreeAcrossLongerStrings() {
        assertThat(generator.generate("abcd")).containsExactly("abc", "bcd");
    }

    @Test
    void includesTrigramsThatSpanAWordBoundary() {
        assertThat(generator.generate("al sayed")).contains("al ", "l s");
    }

    @Test
    void returnsAnEmptyListWhenShorterThanThreeCharacters() {
        assertThat(generator.generate("ab")).isEmpty();
    }
}
