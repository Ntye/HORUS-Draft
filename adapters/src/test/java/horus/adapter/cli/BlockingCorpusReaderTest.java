package horus.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class BlockingCorpusReaderTest {

    private final BlockingCorpusReader reader = new BlockingCorpusReader();

    @Test
    void parsesTabSeparatedCasesSkippingBlanksAndComments() {
        List<BlockingCorpusReader.Row> rows = reader.parse(List.of(
                "# synthetic", "", "Alpha Person\tsrc-1", "Beta Person\tsrc-2"));

        assertThat(rows).containsExactly(
                new BlockingCorpusReader.Row(3, "Alpha Person", "src-1"),
                new BlockingCorpusReader.Row(4, "Beta Person", "src-2"));
    }

    @Test
    void aMalformedLineFailsTheWholeReadAndCitesOnlyItsNumber() {
        // Skipping a bad case would shrink the denominator and inflate recall.
        assertThatThrownBy(() -> reader.parse(List.of("Alpha Person\tsrc-1", "Secretname Only")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 2")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("Secretname"));
    }

    @Test
    void anEmptyCorpusIsRejected() {
        assertThatThrownBy(() -> reader.parse(List.of("# only a comment", "")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extraColumnsAreRejectedRatherThanIgnored() {
        assertThatThrownBy(() -> reader.parse(List.of("Alpha\tsrc-1\textra")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
