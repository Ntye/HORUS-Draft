package horus.ingestion.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.EntityType;
import horus.domain.watchentity.NameType;
import horus.ingestion.format.RawRecord;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AdapterRegistryTest {

    private static WatchlistSourceAdapter fakeAdapter(String sourceId) {
        return new WatchlistSourceAdapter() {
            @Override
            public String sourceId() {
                return sourceId;
            }

            @Override
            public String formatId() {
                return "xml";
            }

            @Override
            public CanonicalRecord toCanonical(RawRecord r) {
                return new CanonicalRecordBuilder()
                        .sourceId(sourceId)
                        .sourceRecordId("x")
                        .entityType(EntityType.INDIVIDUAL, "person@e-i")
                        .name("Fake Name", NameType.PRIMARY, "person/last_name")
                        .build();
            }

            @Override
            public CapabilityExpectation expectation() {
                return new CapabilityExpectation(sourceId, Map.of(), Map.of());
            }

            @Override
            public Set<CanonicalSlot> requiredSlots() {
                return Set.of(CanonicalSlot.PRIMARY_NAME);
            }
        };
    }

    @Test
    void resolvesARegisteredAdapterBySourceId() {
        AdapterRegistry registry = new AdapterRegistry();
        registry.register(fakeAdapter("worldcheck"));

        assertThat(registry.resolve("worldcheck").sourceId()).isEqualTo("worldcheck");
    }

    @Test
    void rejectsResolvingAnUnregisteredSourceId() {
        AdapterRegistry registry = new AdapterRegistry();

        assertThatThrownBy(() -> registry.resolve("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRegisteringTheSameSourceIdTwice() {
        AdapterRegistry registry = new AdapterRegistry();
        registry.register(fakeAdapter("worldcheck"));

        assertThatThrownBy(() -> registry.register(fakeAdapter("worldcheck")))
                .isInstanceOf(IllegalStateException.class);
    }
}
