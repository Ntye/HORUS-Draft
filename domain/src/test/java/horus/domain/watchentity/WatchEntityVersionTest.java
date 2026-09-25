package horus.domain.watchentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.EntityId;
import horus.domain.shared.EntityType;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.Gender;
import horus.domain.shared.ListVersionId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WatchEntityVersionTest {

    @Test
    void acceptsAValidActiveVersion() {
        WatchEntityVersion version = new WatchEntityVersion(
                new EntityVersionId(UUID.randomUUID()),
                new EntityId(UUID.randomUUID()),
                new ListVersionId(UUID.randomUUID()),
                "hash-1",
                EntityType.INDIVIDUAL,
                Optional.of(Gender.MALE),
                "Mohammed Al Sayed",
                EntityStatus.ACTIVE,
                List.of("SANCTIONS", "PEP"),
                List.of("UN_CONSOLIDATED"),
                Optional.of(LocalDate.of(2020, 1, 1)),
                Optional.empty());

        assertThat(version.primaryName()).isEqualTo("Mohammed Al Sayed");
        assertThat(version.status()).isEqualTo(EntityStatus.ACTIVE);
    }

    @Test
    void sortsCategoriesAndListSourcesForDeterminism() {
        WatchEntityVersion version = new WatchEntityVersion(
                new EntityVersionId(UUID.randomUUID()),
                new EntityId(UUID.randomUUID()),
                new ListVersionId(UUID.randomUUID()),
                "hash-1",
                EntityType.ORGANISATION,
                Optional.empty(),
                "Sahara Trading Ltd",
                EntityStatus.ACTIVE,
                List.of("SANCTIONS", "ASSET_FREEZE"),
                List.of("UN_CONSOLIDATED", "EU_CONSOLIDATED"),
                Optional.empty(),
                Optional.empty());

        assertThat(version.categories()).containsExactly("ASSET_FREEZE", "SANCTIONS");
        assertThat(version.listSources()).containsExactly("EU_CONSOLIDATED", "UN_CONSOLIDATED");
    }

    @Test
    void rejectsBlankContentHash() {
        assertThatThrownBy(() -> new WatchEntityVersion(
                new EntityVersionId(UUID.randomUUID()),
                new EntityId(UUID.randomUUID()),
                new ListVersionId(UUID.randomUUID()),
                " ",
                EntityType.INDIVIDUAL,
                Optional.of(Gender.MALE),
                "Name",
                EntityStatus.ACTIVE,
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delistedStatusRequiresDelistedAt() {
        assertThatThrownBy(() -> new WatchEntityVersion(
                new EntityVersionId(UUID.randomUUID()),
                new EntityId(UUID.randomUUID()),
                new ListVersionId(UUID.randomUUID()),
                "hash-1",
                EntityType.INDIVIDUAL,
                Optional.of(Gender.MALE),
                "Name",
                EntityStatus.DELISTED,
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void tombstoneProducesADelistedVersionCarryingIdentityForward() {
        EntityId entityId = new EntityId(UUID.randomUUID());
        WatchEntityVersion active = new WatchEntityVersion(
                new EntityVersionId(UUID.randomUUID()),
                entityId,
                new ListVersionId(UUID.randomUUID()),
                "hash-1",
                EntityType.INDIVIDUAL,
                Optional.of(Gender.MALE),
                "Mohammed Al Sayed",
                EntityStatus.ACTIVE,
                List.of("SANCTIONS"),
                List.of("UN_CONSOLIDATED"),
                Optional.of(LocalDate.of(2020, 1, 1)),
                Optional.empty());

        EntityVersionId tombstoneVersionId = new EntityVersionId(UUID.randomUUID());
        ListVersionId newLoadId = new ListVersionId(UUID.randomUUID());
        LocalDate delistedAt = LocalDate.of(2026, 6, 1);

        WatchEntityVersion tombstone = WatchEntityVersion.tombstone(
                active, tombstoneVersionId, newLoadId, "hash-2", delistedAt);

        assertThat(tombstone.entityVersionId()).isEqualTo(tombstoneVersionId);
        assertThat(tombstone.entityId()).isEqualTo(entityId);
        assertThat(tombstone.listVersionId()).isEqualTo(newLoadId);
        assertThat(tombstone.status()).isEqualTo(EntityStatus.DELISTED);
        assertThat(tombstone.delistedAt()).contains(delistedAt);
        assertThat(tombstone.primaryName()).isEqualTo(active.primaryName());
        // I-6: the prior version is untouched, still constructible and queryable.
        assertThat(active.status()).isEqualTo(EntityStatus.ACTIVE);
    }
}
