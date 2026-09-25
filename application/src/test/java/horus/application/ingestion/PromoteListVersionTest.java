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

class PromoteListVersionTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-21T01:00:00Z"), ZoneOffset.UTC);

    private static ListVersion listVersion(ListVersionId id, LoadStatus status, Instant loadedAt) {
        return new ListVersion(id, "worldcheck", "worldcheck-xml-v1", Optional.empty(),
                "wc.xml", "sha256:" + id.value(), Optional.empty(), loadedAt, 0L, status,
                "svc-ingest", Optional.empty(), "1.0.0", Optional.empty());
    }

    @Test
    void promotesAStagedVersionAndSupersedesThePreviousActiveOne() {
        FakeListVersionRepository listVersionRepository = new FakeListVersionRepository();
        FakeAuditEventPort auditEventPort = new FakeAuditEventPort();
        PromoteListVersion promoteListVersion = new PromoteListVersion(
                listVersionRepository, new FakeIndexReconciliation(),
                new RecordAuditEvent(auditEventPort, new FakeIdGenerator(), CLOCK));

        ListVersionId oldActiveId = new ListVersionId(UUID.randomUUID());
        listVersionRepository.save(listVersion(oldActiveId, LoadStatus.ACTIVE, Instant.parse("2026-09-01T00:00:00Z")));
        ListVersionId newStagedId = new ListVersionId(UUID.randomUUID());
        listVersionRepository.save(listVersion(newStagedId, LoadStatus.STAGED, Instant.parse("2026-09-21T00:00:00Z")));

        promoteListVersion.execute(new PromoteListVersion.Command(newStagedId, "worldcheck", "svc-ingest"));

        assertThat(listVersionRepository.findById(newStagedId).orElseThrow().status()).isEqualTo(LoadStatus.ACTIVE);
        assertThat(listVersionRepository.findById(oldActiveId).orElseThrow().status()).isEqualTo(LoadStatus.SUPERSEDED);
        assertThat(auditEventPort.recorded()).anyMatch(e -> e.eventType().equals("LIST_VERSION_PROMOTED"));
    }

    @Test
    void promotesTheFirstEverLoadWithNoPreviousActiveVersion() {
        FakeListVersionRepository listVersionRepository = new FakeListVersionRepository();
        PromoteListVersion promoteListVersion = new PromoteListVersion(
                listVersionRepository, new FakeIndexReconciliation(),
                new RecordAuditEvent(new FakeAuditEventPort(), new FakeIdGenerator(), CLOCK));

        ListVersionId stagedId = new ListVersionId(UUID.randomUUID());
        listVersionRepository.save(listVersion(stagedId, LoadStatus.STAGED, Instant.parse("2026-09-21T00:00:00Z")));

        promoteListVersion.execute(new PromoteListVersion.Command(stagedId, "worldcheck", "svc-ingest"));

        assertThat(listVersionRepository.findById(stagedId).orElseThrow().status()).isEqualTo(LoadStatus.ACTIVE);
    }

    @Test
    void refusesToPromoteAVersionThatIsNotStaged() {
        FakeListVersionRepository listVersionRepository = new FakeListVersionRepository();
        PromoteListVersion promoteListVersion = new PromoteListVersion(
                listVersionRepository, new FakeIndexReconciliation(),
                new RecordAuditEvent(new FakeAuditEventPort(), new FakeIdGenerator(), CLOCK));

        ListVersionId activeId = new ListVersionId(UUID.randomUUID());
        listVersionRepository.save(listVersion(activeId, LoadStatus.ACTIVE, Instant.parse("2026-09-21T00:00:00Z")));

        assertThatThrownBy(() -> promoteListVersion.execute(
                new PromoteListVersion.Command(activeId, "worldcheck", "svc-ingest")))
                .isInstanceOf(IllegalStateException.class);
    }

    // I-1: a load that fails the completeness check must never become visible, and the
    // previously ACTIVE version must be left untouched, still serving.
    @Test
    void failsClosedWhenTheCompletenessCheckFailsAndLeavesThePreviousVersionActive() {
        FakeListVersionRepository listVersionRepository = new FakeListVersionRepository();
        FakeIndexReconciliation indexReconciliation = new FakeIndexReconciliation();
        FakeAuditEventPort auditEventPort = new FakeAuditEventPort();
        PromoteListVersion promoteListVersion = new PromoteListVersion(
                listVersionRepository, indexReconciliation,
                new RecordAuditEvent(auditEventPort, new FakeIdGenerator(), CLOCK));

        ListVersionId oldActiveId = new ListVersionId(UUID.randomUUID());
        listVersionRepository.save(listVersion(oldActiveId, LoadStatus.ACTIVE, Instant.parse("2026-09-01T00:00:00Z")));
        ListVersionId brokenStagedId = new ListVersionId(UUID.randomUUID());
        listVersionRepository.save(listVersion(brokenStagedId, LoadStatus.STAGED, Instant.parse("2026-09-21T00:00:00Z")));
        indexReconciliation.forceIncomplete(brokenStagedId);

        assertThatThrownBy(() -> promoteListVersion.execute(
                new PromoteListVersion.Command(brokenStagedId, "worldcheck", "svc-ingest")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(listVersionRepository.findById(brokenStagedId).orElseThrow().status()).isEqualTo(LoadStatus.FAILED);
        assertThat(listVersionRepository.findById(oldActiveId).orElseThrow().status()).isEqualTo(LoadStatus.ACTIVE);
        assertThat(auditEventPort.recorded()).anyMatch(e -> e.eventType().equals("LIST_VERSION_PROMOTION_FAILED"));
    }
}
