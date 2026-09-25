package horus.domain.listversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.domain.shared.ListVersionId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListVersionTest {

    private static ListVersion staged() {
        return new ListVersion(
                new ListVersionId(UUID.randomUUID()),
                "worldcheck",
                "worldcheck-xml-v1",
                Optional.empty(),
                "wc_2026_09_21.xml",
                "sha256:abc123",
                Optional.of(Instant.parse("2026-09-21T00:00:00Z")),
                Instant.parse("2026-09-21T01:00:00Z"),
                5_990_394L,
                LoadStatus.STAGED,
                "svc-ingest",
                Optional.empty(),
                "1.0.0",
                Optional.empty());
    }

    @Test
    void acceptsAValidStagedLoad() {
        ListVersion version = staged();

        assertThat(version.status()).isEqualTo(LoadStatus.STAGED);
        assertThat(version.isActive()).isFalse();
        assertThat(version.pipelineVersion()).isEqualTo("1.0.0");
        assertThat(version.indexBinding()).isEmpty();
    }

    @Test
    void withStatusReturnsANewInstanceWithoutMutatingTheOriginal() {
        ListVersion original = staged();

        ListVersion promoted = original.withStatus(LoadStatus.ACTIVE);

        assertThat(promoted.status()).isEqualTo(LoadStatus.ACTIVE);
        assertThat(promoted.isActive()).isTrue();
        assertThat(original.status()).isEqualTo(LoadStatus.STAGED);
        assertThat(promoted.listVersionId()).isEqualTo(original.listVersionId());
        assertThat(promoted.pipelineVersion()).isEqualTo(original.pipelineVersion());
    }

    @Test
    void withCapabilitiesReturnsANewInstanceWithoutMutatingTheOriginal() {
        ListVersion original = staged();
        SourceCapabilities capabilities = new SourceCapabilities(
                original.listVersionId(), "worldcheck", java.util.Map.of(), Instant.parse("2026-09-21T01:05:00Z"));

        ListVersion measured = original.withCapabilities(capabilities);

        assertThat(measured.capabilities()).contains(capabilities);
        assertThat(original.capabilities()).isEmpty();
    }

    @Test
    void withRecordCountReturnsANewInstanceWithoutMutatingTheOriginal() {
        ListVersion original = staged().withRecordCount(0);

        ListVersion corrected = original.withRecordCount(20);

        assertThat(corrected.recordCount()).isEqualTo(20);
        assertThat(original.recordCount()).isEqualTo(0);
    }

    @Test
    void rejectsNegativeRecordCount() {
        assertThatThrownBy(() -> new ListVersion(
                new ListVersionId(UUID.randomUUID()),
                "worldcheck",
                "worldcheck-xml-v1",
                Optional.empty(),
                "wc.xml",
                "sha256:abc123",
                Optional.empty(),
                Instant.parse("2026-09-21T01:00:00Z"),
                -1L,
                LoadStatus.STAGED,
                "svc-ingest",
                Optional.empty(),
                "1.0.0",
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankSourceFileChecksum() {
        assertThatThrownBy(() -> new ListVersion(
                new ListVersionId(UUID.randomUUID()),
                "worldcheck",
                "worldcheck-xml-v1",
                Optional.empty(),
                "wc.xml",
                " ",
                Optional.empty(),
                Instant.parse("2026-09-21T01:00:00Z"),
                100L,
                LoadStatus.STAGED,
                "svc-ingest",
                Optional.empty(),
                "1.0.0",
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankPipelineVersion() {
        assertThatThrownBy(() -> new ListVersion(
                new ListVersionId(UUID.randomUUID()),
                "worldcheck",
                "worldcheck-xml-v1",
                Optional.empty(),
                "wc.xml",
                "sha256:abc123",
                Optional.empty(),
                Instant.parse("2026-09-21T01:00:00Z"),
                100L,
                LoadStatus.STAGED,
                "svc-ingest",
                Optional.empty(),
                " ",
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullIndexBinding() {
        assertThatThrownBy(() -> new ListVersion(
                new ListVersionId(UUID.randomUUID()),
                "worldcheck",
                "worldcheck-xml-v1",
                Optional.empty(),
                "wc.xml",
                "sha256:abc123",
                Optional.empty(),
                Instant.parse("2026-09-21T01:00:00Z"),
                100L,
                LoadStatus.STAGED,
                "svc-ingest",
                Optional.empty(),
                "1.0.0",
                null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
