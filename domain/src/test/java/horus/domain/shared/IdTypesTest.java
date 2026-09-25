package horus.domain.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdTypesTest {

    @Test
    void entityIdAcceptsAndRejects() {
        UUID value = UUID.randomUUID();

        assertThat(new EntityId(value).value()).isEqualTo(value);
        assertThatThrownBy(() -> new EntityId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void entityVersionIdAcceptsAndRejects() {
        UUID value = UUID.randomUUID();

        assertThat(new EntityVersionId(value).value()).isEqualTo(value);
        assertThatThrownBy(() -> new EntityVersionId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listVersionIdAcceptsAndRejects() {
        UUID value = UUID.randomUUID();

        assertThat(new ListVersionId(value).value()).isEqualTo(value);
        assertThatThrownBy(() -> new ListVersionId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void screeningIdAcceptsAndRejects() {
        UUID value = UUID.randomUUID();

        assertThat(new ScreeningId(value).value()).isEqualTo(value);
        assertThatThrownBy(() -> new ScreeningId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void screeningSubjectIdAcceptsAndRejects() {
        UUID value = UUID.randomUUID();

        assertThat(new ScreeningSubjectId(value).value()).isEqualTo(value);
        assertThatThrownBy(() -> new ScreeningSubjectId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void screeningCandidateIdAcceptsAndRejects() {
        UUID value = UUID.randomUUID();

        assertThat(new ScreeningCandidateId(value).value()).isEqualTo(value);
        assertThatThrownBy(() -> new ScreeningCandidateId(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whitelistIdAcceptsAndRejects() {
        UUID value = UUID.randomUUID();

        assertThat(new WhitelistId(value).value()).isEqualTo(value);
        assertThatThrownBy(() -> new WhitelistId(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
