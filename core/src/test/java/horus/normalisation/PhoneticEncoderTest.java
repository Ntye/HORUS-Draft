package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PhoneticEncoderTest {

    private final PhoneticEncoder encoder = new PhoneticEncoder();

    @Test
    void producesOneCodePerToken() {
        assertThat(encoder.encode(List.of("mohammed", "al", "sayed"))).hasSize(3);
    }

    @Test
    void commonSpellingVariantsProduceTheSameCode() {
        // The textbook Double Metaphone case: two legitimate spellings of one name.
        String mohammed = encoder.encode(List.of("mohammed")).get(0);
        String muhammad = encoder.encode(List.of("muhammad")).get(0);

        assertThat(mohammed).isEqualTo(muhammad);
    }

    @Test
    void returnsAnEmptyListForEmptyInput() {
        assertThat(encoder.encode(List.of())).isEmpty();
    }
}
