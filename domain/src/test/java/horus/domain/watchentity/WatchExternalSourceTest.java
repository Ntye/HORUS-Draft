package horus.domain.watchentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityVersionId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchExternalSourceTest {

    @Test
    void acceptsAValidUri() {
        WatchExternalSource source = new WatchExternalSource(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                "https://example.org/case/123");

        assertThat(source.uri()).isEqualTo("https://example.org/case/123");
    }

    @Test
    void rejectsBlankUri() {
        assertThatThrownBy(() -> new WatchExternalSource(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
