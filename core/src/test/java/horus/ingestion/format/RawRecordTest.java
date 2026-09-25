package horus.ingestion.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RawRecordTest {

    @Test
    void getReturnsTheValuesAtAPath() {
        RawRecord record = new RawRecord(
                0L, Map.of("person/first_name", List.of("John")), Map.of(), "record#0");

        assertThat(record.get("person/first_name")).containsExactly("John");
    }

    @Test
    void getReturnsAnEmptyListForAnUnknownPath() {
        RawRecord record = new RawRecord(0L, Map.of(), Map.of(), "record#0");

        assertThat(record.get("nope")).isEmpty();
    }

    @Test
    void isAbsentIsTrueOnlyWhenTheNilMarkerIsSet() {
        RawRecord nilled = new RawRecord(
                0L, Map.of(), Map.of("date_of_birth/day@nil", "true"), "record#0");
        RawRecord present = new RawRecord(
                0L, Map.of("date_of_birth/day", List.of("12")), Map.of(), "record#0");
        RawRecord simplyMissing = new RawRecord(0L, Map.of(), Map.of(), "record#0");

        assertThat(nilled.isAbsent("date_of_birth/day")).isTrue();
        assertThat(present.isAbsent("date_of_birth/day")).isFalse();
        // Simply missing (never appeared) is not the same claim as "asserted absent".
        assertThat(simplyMissing.isAbsent("date_of_birth/day")).isFalse();
    }

    @Test
    void rejectsBlankSourceRef() {
        assertThatThrownBy(() -> new RawRecord(0L, Map.of(), Map.of(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pathsAndAttributesAreDefensivelyImmutable() {
        RawRecord record = new RawRecord(
                0L, Map.of("a", List.of("1")), Map.of("b", "2"), "record#0");

        assertThatThrownBy(() -> record.paths().put("x", List.of("y")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> record.get("a").add("2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
