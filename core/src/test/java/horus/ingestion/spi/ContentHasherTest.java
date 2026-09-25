package horus.ingestion.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityType;
import horus.domain.watchentity.NameType;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ContentHasherTest {

    private static CanonicalRecordBuilder base() {
        return new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Ahmed Yousef", NameType.PRIMARY, "person/last_name")
                .name("A. Yousef", NameType.ALIAS, "person/aka");
    }

    @Test
    void sameContentProducesTheSameHashTwice() {
        String first = ContentHasher.hash(base().build());
        String second = ContentHasher.hash(base().build());

        assertThat(first).isEqualTo(second);
    }

    @Test
    void aChangedNameProducesADifferentHash() {
        String original = ContentHasher.hash(base().build());
        String changed = ContentHasher.hash(new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Ahmed Yousuf", NameType.PRIMARY, "person/last_name")
                .name("A. Yousef", NameType.ALIAS, "person/aka")
                .build());

        assertThat(changed).isNotEqualTo(original);
    }

    @Test
    void reorderingAliasesDoesNotChangeTheHash() {
        String first = ContentHasher.hash(new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Ahmed Yousef", NameType.PRIMARY, "person/last_name")
                .name("A. Yousef", NameType.ALIAS, "person/aka")
                .name("Ahmad Yousef", NameType.ALIAS, "person/aka")
                .build());

        String reordered = ContentHasher.hash(new CanonicalRecordBuilder()
                .sourceId("worldcheck")
                .sourceRecordId("wc-1")
                .entityType(EntityType.INDIVIDUAL, "person@e-i")
                .name("Ahmed Yousef", NameType.PRIMARY, "person/last_name")
                .name("Ahmad Yousef", NameType.ALIAS, "person/aka")
                .name("A. Yousef", NameType.ALIAS, "person/aka")
                .build());

        assertThat(reordered).isEqualTo(first);
    }

    @Test
    void aChangedDobProducesADifferentHash() {
        String original = ContentHasher.hash(base().build());
        String changed = ContentHasher.hash(base()
                .dob(new CanonicalDob(Optional.of(1980), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty(), "date_of_birth/year"))
                .build());

        assertThat(changed).isNotEqualTo(original);
    }

    @Test
    void rejectsANullRecord() {
        assertThatThrownBy(() -> ContentHasher.hash(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
