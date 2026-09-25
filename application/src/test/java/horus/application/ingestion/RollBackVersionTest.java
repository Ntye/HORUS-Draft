package horus.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.application.ingestion.testsupport.FakeAuditEventPort;
import horus.application.ingestion.testsupport.FakeIdGenerator;
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

class RollBackVersionTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-21T01:00:00Z"), ZoneOffset.UTC);

    private static ListVersion listVersion(ListVersionId id, LoadStatus status, Instant loadedAt) {
        return new ListVersion(id, "worldcheck", "worldcheck-xml-v1", Optional.empty(),
                "wc.xml", "sha256:" + id.value(), Optional.empty(), loadedAt, 0L, status,
                "svc-ingest", Optional.empty(), "1.0.0", Optional.empty());
    }

    @Test
    void repointsToThePreviousVersionAndFailsTheBadOne() {
        FakeListVersionRepository listVersionRepository = new FakeListVersionRepository();
        FakeAuditEventPort auditEventPort = new FakeAuditEventPort();
        RollBackVersion rollBackVersion = new RollBackVersion(
                listVersionRepository, new RecordAuditEvent(auditEventPort, new FakeIdGenerator(), CLOCK));

        ListVersionId goodId = new ListVersionId(UUID.randomUUID());
        listVersionRepository.save(listVersion(goodId, LoadStatus.SUPERSEDED, Instant.parse("2026-09-01T00:00:00Z")));
        ListVersionId badId = new ListVersionId(UUID.randomUUID());
        listVersionRepository.save(listVersion(badId, LoadStatus.ACTIVE, Instant.parse("2026-09-21T00:00:00Z")));

        RollBackVersion.Result result = rollBackVersion.execute(
                new RollBackVersion.Command("worldcheck", "bad DOB coverage", "operator-1"));

        assertThat(result.rolledBackFrom()).isEqualTo(badId);
        assertThat(result.nowActive()).contains(goodId);
        assertThat(listVersionRepository.findById(badId).orElseThrow().status()).isEqualTo(LoadStatus.FAILED);
        assertThat(listVersionRepository.findById(goodId).orElseThrow().status()).isEqualTo(LoadStatus.ACTIVE);
        assertThat(auditEventPort.recorded()).anyMatch(e -> e.eventType().equals("LIST_VERSION_ROLLED_BACK"));
    }

    @Test
    void rollsBackToNothingWhenThereWasNoPriorVersion() {
        FakeListVersionRepository listVersionRepository = new FakeListVersionRepository();
        RollBackVersion rollBackVersion = new RollBackVersion(
                listVersionRepository, new RecordAuditEvent(new FakeAuditEventPort(), new FakeIdGenerator(), CLOCK));

        ListVersionId onlyId = new ListVersionId(UUID.randomUUID());
        listVersionRepository.save(listVersion(onlyId, LoadStatus.ACTIVE, Instant.parse("2026-09-21T00:00:00Z")));

        RollBackVersion.Result result = rollBackVersion.execute(
                new RollBackVersion.Command("worldcheck", "bad load", "operator-1"));

        assertThat(result.nowActive()).isEmpty();
        assertThat(listVersionRepository.findById(onlyId).orElseThrow().status()).isEqualTo(LoadStatus.FAILED);
    }

    @Test
    void refusesWhenThereIsNoActiveVersionForTheSource() {
        FakeListVersionRepository listVersionRepository = new FakeListVersionRepository();
        RollBackVersion rollBackVersion = new RollBackVersion(
                listVersionRepository, new RecordAuditEvent(new FakeAuditEventPort(), new FakeIdGenerator(), CLOCK));

        assertThatThrownBy(() -> rollBackVersion.execute(
                new RollBackVersion.Command("worldcheck", "bad load", "operator-1")))
                .isInstanceOf(IllegalStateException.class);
    }
}
