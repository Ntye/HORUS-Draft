package horus.domain.watchentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityVersionId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchNameTest {

    @Test
    void acceptsAValidName() {
        WatchName name = new WatchName(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                NameType.ALIAS,
                "Ahmad Bin Yousef",
                "ahmad bin yousef",
                List.of("ahmad", "bin", "yousef"),
                List.of("A530"),
                List.of("ahm", "hma"),
                "Latin",
                "en",
                Optional.empty());

        assertThat(name.rawName()).isEqualTo("Ahmad Bin Yousef");
        assertThat(name.nameTokens()).containsExactly("ahmad", "bin", "yousef");
    }

    @Test
    void rejectsBlankRawName() {
        assertThatThrownBy(() -> new WatchName(
                UUID.randomUUID(),
                new EntityVersionId(UUID.randomUUID()),
                NameType.PRIMARY,
                " ",
                "",
                List.of(),
                List.of(),
                List.of(),
                "Latin",
                "en",
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullEntityVersionId() {
        assertThatThrownBy(() -> new WatchName(
                UUID.randomUUID(),
                null,
                NameType.PRIMARY,
                "Name",
                "name",
                List.of(),
                List.of(),
                List.of(),
                "Latin",
                "en",
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
