package horus.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QueryNameTest {

    @Test
    void acceptsAValidName() {
        QueryName name = new QueryName("Mohammed Al Sayed");

        assertThat(name.value()).isEqualTo("Mohammed Al Sayed");
    }

    @Test
    void stripsSurroundingWhitespace() {
        QueryName name = new QueryName("  Mohammed Al Sayed  ");

        assertThat(name.value()).isEqualTo("Mohammed Al Sayed");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new QueryName(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new QueryName("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverLengthInput() {
        String tooLong = "a".repeat(QueryName.MAX_LENGTH + 1);

        assertThatThrownBy(() -> new QueryName(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsExactlyMaxLength() {
        String exact = "a".repeat(QueryName.MAX_LENGTH);

        assertThat(new QueryName(exact).value()).hasSize(QueryName.MAX_LENGTH);
    }
}
