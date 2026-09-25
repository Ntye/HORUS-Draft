package horus.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProvenanceTest {

    @Test
    void acceptsAValidRange() {
        Provenance provenance = new Provenance("designation.action", 10, 20);

        assertThat(provenance.ruleId()).isEqualTo("designation.action");
        assertThat(provenance.start()).isEqualTo(10);
        assertThat(provenance.end()).isEqualTo(20);
    }

    @Test
    void rejectsBlankRuleId() {
        assertThatThrownBy(() -> new Provenance(" ", 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeStart() {
        assertThatThrownBy(() -> new Provenance("rule", -1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> new Provenance("rule", 10, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shiftMovesBothOffsetsByTheSameAmount() {
        Provenance provenance = new Provenance("rule", 10, 20);

        Provenance shifted = provenance.shift(5);

        assertThat(shifted.start()).isEqualTo(15);
        assertThat(shifted.end()).isEqualTo(25);
        assertThat(shifted.ruleId()).isEqualTo("rule");
    }
}
