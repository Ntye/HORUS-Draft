package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TokenSorterTest {

    private final TokenSorter sorter = new TokenSorter();

    @Test
    void sortsTokensAlphabetically() {
        assertThat(sorter.sort(List.of("mohammed", "al", "sayed")))
                .containsExactly("al", "mohammed", "sayed");
    }

    @Test
    void leavesAnAlreadySortedListUnchanged() {
        assertThat(sorter.sort(List.of("al", "mohammed", "sayed")))
                .containsExactly("al", "mohammed", "sayed");
    }

    @Test
    void returnsAnEmptyListForEmptyInput() {
        assertThat(sorter.sort(List.of())).isEmpty();
    }

    @Test
    void doesNotMutateTheInputList() {
        List<String> input = List.of("sayed", "al");

        sorter.sort(input);

        assertThat(input).containsExactly("sayed", "al");
    }
}
