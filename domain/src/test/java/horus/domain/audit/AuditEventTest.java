package horus.domain.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventTest {

    private static AuditEvent event() {
        return new AuditEvent(
                UUID.randomUUID(),
                "LIST_VERSION_PROMOTED",
                "svc-ingest",
                ActorKind.SYSTEM,
                Instant.parse("2026-09-21T01:00:00Z"),
                "ListVersion",
                UUID.randomUUID().toString(),
                Map.of("recordCount", "20"),
                Optional.of(UUID.randomUUID().toString()),
                Optional.empty());
    }

    @Test
    void acceptsAValidEvent() {
        AuditEvent event = event();

        assertThat(event.eventType()).isEqualTo("LIST_VERSION_PROMOTED");
        assertThat(event.actorKind()).isEqualTo(ActorKind.SYSTEM);
        assertThat(event.payload()).containsEntry("recordCount", "20");
    }

    @Test
    void rejectsBlankActor() {
        assertThatThrownBy(() -> new AuditEvent(
                UUID.randomUUID(),
                "LIST_VERSION_PROMOTED",
                " ",
                ActorKind.SYSTEM,
                Instant.parse("2026-09-21T01:00:00Z"),
                "ListVersion",
                UUID.randomUUID().toString(),
                Map.of(),
                Optional.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankSubjectId() {
        assertThatThrownBy(() -> new AuditEvent(
                UUID.randomUUID(),
                "LIST_VERSION_PROMOTED",
                "svc-ingest",
                ActorKind.SYSTEM,
                Instant.parse("2026-09-21T01:00:00Z"),
                "ListVersion",
                " ",
                Map.of(),
                Optional.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullPayload() {
        assertThatThrownBy(() -> new AuditEvent(
                UUID.randomUUID(),
                "LIST_VERSION_PROMOTED",
                "svc-ingest",
                ActorKind.SYSTEM,
                Instant.parse("2026-09-21T01:00:00Z"),
                "ListVersion",
                UUID.randomUUID().toString(),
                null,
                Optional.empty(),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void payloadIsDefensivelyCopied() {
        Map<String, String> mutable = new java.util.HashMap<>();
        mutable.put("key", "value");
        AuditEvent event = new AuditEvent(
                UUID.randomUUID(),
                "LIST_VERSION_PROMOTED",
                "svc-ingest",
                ActorKind.SYSTEM,
                Instant.parse("2026-09-21T01:00:00Z"),
                "ListVersion",
                UUID.randomUUID().toString(),
                mutable,
                Optional.empty(),
                Optional.empty());

        mutable.put("key2", "value2");

        assertThat(event.payload()).containsOnlyKeys("key");
    }
}
