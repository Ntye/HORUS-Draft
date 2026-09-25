package horus.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import horus.application.ingestion.testsupport.FakeAuditEventPort;
import horus.application.ingestion.testsupport.FakeIdGenerator;
import horus.domain.audit.ActorKind;
import horus.domain.audit.AuditEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecordAuditEventTest {

    @Test
    void recordsAnEventWithInjectedIdAndClock() {
        FakeAuditEventPort port = new FakeAuditEventPort();
        Clock fixedClock = Clock.fixed(Instant.parse("2026-09-21T01:00:00Z"), ZoneOffset.UTC);
        RecordAuditEvent recordAuditEvent = new RecordAuditEvent(port, new FakeIdGenerator(), fixedClock);

        recordAuditEvent.execute(new RecordAuditEvent.Command(
                "LIST_VERSION_PROMOTED",
                "svc-ingest",
                ActorKind.SYSTEM,
                "ListVersion",
                "list-version-1",
                Map.of("recordCount", "20"),
                Optional.empty()));

        assertThat(port.recorded()).hasSize(1);
        AuditEvent event = port.recorded().get(0);
        assertThat(event.eventType()).isEqualTo("LIST_VERSION_PROMOTED");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-09-21T01:00:00Z"));
        assertThat(event.subjectId()).isEqualTo("list-version-1");
        assertThat(event.payload()).containsEntry("recordCount", "20");
    }
}
