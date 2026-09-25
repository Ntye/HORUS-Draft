package horus.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import horus.application.port.ActiveEntitySnapshot;
import horus.domain.shared.EntityId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconcileVersionTest {

    private final ReconcileVersion reconcileVersion = new ReconcileVersion();

    @Test
    void decidesAddWhenNoExistingSnapshot() {
        assertThat(reconcileVersion.decide(Optional.empty(), "sha256:new")).isEqualTo(ReconcileVersion.Decision.ADD);
    }

    @Test
    void decidesUnchangedWhenContentHashMatches() {
        ActiveEntitySnapshot existing = new ActiveEntitySnapshot(new EntityId(UUID.randomUUID()), "sha256:same");

        assertThat(reconcileVersion.decide(Optional.of(existing), "sha256:same"))
                .isEqualTo(ReconcileVersion.Decision.UNCHANGED);
    }

    @Test
    void decidesAmendWhenContentHashDiffers() {
        ActiveEntitySnapshot existing = new ActiveEntitySnapshot(new EntityId(UUID.randomUUID()), "sha256:old");

        assertThat(reconcileVersion.decide(Optional.of(existing), "sha256:new"))
                .isEqualTo(ReconcileVersion.Decision.AMEND);
    }

    @Test
    void delistedKeysAreActiveEntitiesNeverSeenInTheNewLoad() {
        Map<String, ActiveEntitySnapshot> active = Map.of(
                "wc-1", new ActiveEntitySnapshot(new EntityId(UUID.randomUUID()), "sha256:1"),
                "wc-2", new ActiveEntitySnapshot(new EntityId(UUID.randomUUID()), "sha256:2"));

        Set<String> delisted = reconcileVersion.delistedKeys(active, Set.of("wc-1"));

        assertThat(delisted).containsExactly("wc-2");
    }

    @Test
    void noDelistedKeysWhenEverythingWasSeen() {
        Map<String, ActiveEntitySnapshot> active = Map.of(
                "wc-1", new ActiveEntitySnapshot(new EntityId(UUID.randomUUID()), "sha256:1"));

        assertThat(reconcileVersion.delistedKeys(active, Set.of("wc-1"))).isEmpty();
    }

    @Test
    void aDelistedEntityThatReappearsIsAmendedEvenWhenContentHashIsIdentical() {
        // A tombstone carries the previous contentHash forward, so hash equality alone would call
        // a re-listed entity UNCHANGED and leave it delisted. I-2: keep it screenable.
        ActiveEntitySnapshot tombstoned =
                new ActiveEntitySnapshot(new EntityId(UUID.randomUUID()), "sha256:same", true);

        assertThat(reconcileVersion.decide(Optional.of(tombstoned), "sha256:same"))
                .isEqualTo(ReconcileVersion.Decision.AMEND);
    }

    @Test
    void anAlreadyDelistedEntityIsNotTombstonedAgain() {
        Map<String, ActiveEntitySnapshot> current = Map.of(
                "wc-1", new ActiveEntitySnapshot(new EntityId(UUID.randomUUID()), "sha256:1", true),
                "wc-2", new ActiveEntitySnapshot(new EntityId(UUID.randomUUID()), "sha256:2"));

        assertThat(reconcileVersion.delistedKeys(current, Set.of())).containsExactly("wc-2");
    }
}
