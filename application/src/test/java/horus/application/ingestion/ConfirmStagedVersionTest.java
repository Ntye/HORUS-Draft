package horus.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.application.ingestion.testsupport.FakeAuditEventPort;
import horus.application.ingestion.testsupport.FakeIdGenerator;
import horus.application.ingestion.testsupport.FakeIndexReconciliation;
import horus.application.ingestion.testsupport.FakeListVersionRepository;
import horus.domain.listversion.ListVersion;
import horus.domain.listversion.LoadStatus;
import horus.domain.shared.ListVersionId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmStagedVersionTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-21T01:00:00Z"), ZoneOffset.UTC);

    private static ListVersion listVersion(ListVersionId id, LoadStatus status) {
        return new ListVersion(id, "worldcheck", "worldcheck-xml-v1", Optional.empty(),
                "wc.xml", "sha256:" + id.value(), Optional.empty(), Instant.parse("2026-09-21T00:00:00Z"),
                0L, status, "svc-ingest", Optional.empty(), "1.0.0", Optional.empty());
    }

    @Test
    void confirmingPromotesTheStagedVersion() {
        FakeListVersionRepository listVersionRepository = new FakeListVersionRepository();
        FakeAuditEventPort auditEventPort = new FakeAuditEventPort();
        RecordAuditEvent recordAuditEvent = new RecordAuditEvent(auditEventPort, new FakeIdGenerator(), CLOCK);
        ConfirmStagedVersion confirmStagedVersion = new ConfirmStagedVersion(
                listVersionRepository,
                new PromoteListVersion(listVersionRepository, new FakeIndexReconciliation(), recordAuditEvent),
                recordAuditEvent);

        ListVersionId stagedId = new ListVersionId(UUID.randomUUID());
        listVersionRepository.save(listVersion(stagedId, LoadStatus.STAGED));

        confirmStagedVersion.execute(new ConfirmStagedVersion.Command(
                stagedId, ConfirmStagedVersion.Decision.CONFIRM, ConfirmStagedVersion.AwaitingReason.ANOMALOUS_DELTA,
                "delta reviewed against the vendor bulletin", "operator-1"));

        assertThat(listVersionRepository.findById(stagedId).orElseThrow().status()).isEqualTo(LoadStatus.ACTIVE);
        assertThat(auditEventPort.recorded()).anyMatch(e -> e.eventType().equals("STAGED_VERSION_CONFIRMED"));
    }

    @Test
    void rejectingFailsTheStagedVersionWithoutPromotingIt() {
        FakeListVersionRepository listVersionRepository = new FakeListVersionRepository();
        FakeAuditEventPort auditEventPort = new FakeAuditEventPort();
        RecordAuditEvent recordAuditEvent = new RecordAuditEvent(auditEventPort, new FakeIdGenerator(), CLOCK);
        ConfirmStagedVersion confirmStagedVersion = new ConfirmStagedVersion(
                listVersionRepository,
                new PromoteListVersion(listVersionRepository, new FakeIndexReconciliation(), recordAuditEvent),
                recordAuditEvent);

        ListVersionId stagedId = new ListVersionId(UUID.randomUUID());
        listVersionRepository.save(listVersion(stagedId, LoadStatus.STAGED));

        confirmStagedVersion.execute(new ConfirmStagedVersion.Command(
                stagedId, ConfirmStagedVersion.Decision.REJECT, ConfirmStagedVersion.AwaitingReason.ANOMALOUS_DELTA,
                "delta unexplained; awaiting vendor", "operator-1"));

        assertThat(listVersionRepository.findById(stagedId).orElseThrow().status()).isEqualTo(LoadStatus.FAILED);
        assertThat(auditEventPort.recorded()).anyMatch(e -> e.eventType().equals("STAGED_VERSION_REJECTED"));
    }

    @Test
    void refusesToActOnAVersionThatIsNotAwaitingConfirmation() {
        FakeListVersionRepository listVersionRepository = new FakeListVersionRepository();
        RecordAuditEvent recordAuditEvent =
                new RecordAuditEvent(new FakeAuditEventPort(), new FakeIdGenerator(), CLOCK);
        ConfirmStagedVersion confirmStagedVersion = new ConfirmStagedVersion(
                listVersionRepository,
                new PromoteListVersion(listVersionRepository, new FakeIndexReconciliation(), recordAuditEvent),
                recordAuditEvent);

        ListVersionId activeId = new ListVersionId(UUID.randomUUID());
        listVersionRepository.save(listVersion(activeId, LoadStatus.ACTIVE));

        assertThatThrownBy(() -> confirmStagedVersion.execute(new ConfirmStagedVersion.Command(
                activeId, ConfirmStagedVersion.Decision.CONFIRM, ConfirmStagedVersion.AwaitingReason.ANOMALOUS_DELTA,
                "delta reviewed against the vendor bulletin", "operator-1")))
                .isInstanceOf(IllegalStateException.class);
    }

    // The one path that overrides a control that has just fired. An override with no recorded
    // justification is indistinguishable from having no control (D12 H-5).
    @Test
    void aDecisionWithoutAReasonIsNotAccepted() {
        assertThatThrownBy(() -> new ConfirmStagedVersion.Command(
                new ListVersionId(UUID.randomUUID()), ConfirmStagedVersion.Decision.CONFIRM,
                ConfirmStagedVersion.AwaitingReason.CAPABILITY_DEVIATION, "   ", "operator-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
