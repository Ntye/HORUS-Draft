package horus.adapter.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BenchmarkCorpusReaderTest {

    private static final String H = BenchmarkCorpusReader.HEADER;
    private final BenchmarkCorpusReader reader = new BenchmarkCorpusReader();

    @Test
    void parsesAFullyPopulatedRowAndABlankOne() {
        List<BenchmarkCorpusReader.Row> rows = reader.parse(List.of(
                "# synthetic corpus", H,
                "Zorvan Talmesc\tINDIVIDUAL\t1980-05\tEG,KE\tP1,P2\twc-demo-1\tVARIANT",
                "Wexler Pomfrett\t\t\t\t\tNONE\tCLEAR"));

        assertThat(rows).hasSize(2);
        BenchmarkCorpusReader.Row full = rows.get(0);
        assertThat(full.line()).isEqualTo(3);
        assertThat(full.entityType()).contains(EntityType.INDIVIDUAL);
        assertThat(full.dob()).isPresent();
        assertThat(full.countries()).containsExactly("EG", "KE");
        assertThat(full.identifiers()).containsExactly("P1", "P2");
        assertThat(full.expectedSourceEntityId()).contains("wc-demo-1");
        BenchmarkCorpusReader.Row blank = rows.get(1);
        assertThat(blank.entityType()).isEmpty();
        assertThat(blank.dob()).isEmpty();
        assertThat(blank.countries()).isEqualTo(Set.of());
        assertThat(blank.expectedSourceEntityId()).isEqualTo(Optional.empty());
        assertThat(blank.caseClass()).isEqualTo("CLEAR");
    }

    @Test
    void theHeaderIsRequired() {
        assertThatThrownBy(() -> reader.parse(List.of("Zorvan\t\t\t\t\tNONE\tCLEAR")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("header");
    }

    @Test
    void aMalformedRowFailsTheWholeReadAndCitesOnlyItsLineNumber() {
        // Skipping a case would shrink the denominator and inflate recall.
        assertThatThrownBy(() -> reader.parse(List.of(H, "Secretname\tINDIVIDUAL\tnot-a-date\t\t\tNONE\tCLEAR")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line 2")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("Secretname").doesNotContain("not-a-date"));
        assertThatThrownBy(() -> reader.parse(List.of(H, "Secretname\tBOGUS_TYPE\t\t\t\tNONE\tCLEAR")))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("Secretname").doesNotContain("BOGUS"));
        assertThatThrownBy(() -> reader.parse(List.of(H, "only two\tcolumns")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("line 2");
    }

    @Test
    void aMissingExpectedOrClassIsRejectedRatherThanTreatedAsNone() {
        // An empty "expected" must not silently become a negative case.
        assertThatThrownBy(() -> reader.parse(List.of(H, "Name\t\t\t\t\t\tCLEAR")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.parse(List.of(H, "Name\t\t\t\t\tNONE\t")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aCorpusWithNoCasesIsRejected() {
        assertThatThrownBy(() -> reader.parse(List.of(H))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reader.parse(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
