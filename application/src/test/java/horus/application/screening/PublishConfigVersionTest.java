package horus.application.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import horus.application.ingestion.RecordAuditEvent;
import horus.application.ingestion.testsupport.FakeAuditEventPort;
import horus.application.ingestion.testsupport.FakeIdGenerator;
import horus.application.port.ActiveListVersion;
import horus.application.port.MatchConfigRepository;
import horus.application.port.VersionedMatchConfig;
import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.ListVersionId;
import horus.matching.InvalidMatchConfigException;
import horus.matching.MatchConfig;
import horus.matching.Registry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublishConfigVersionTest {

    private static final Instant NOW = Instant.parse("2026-09-24T10:00:00Z");

    private final List<VersionedMatchConfig> saved = new ArrayList<>();
    private final List<String> approvers = new ArrayList<>();
    private final FakeAuditEventPort audit = new FakeAuditEventPort();
    private List<ActiveListVersion> lists = List.of(new ActiveListVersion(
            new ListVersionId(new UUID(7, 7)), "test-source", "1.0.0", EnumSet.allOf(CanonicalSlot.class)));

    private PublishConfigVersion interactor() {
        MatchConfigRepository repository = new MatchConfigRepository() {
            @Override
            public Optional<VersionedMatchConfig> findCurrent(String profileId, Instant asOf) {
                return Optional.empty();
            }

            @Override
            public void save(VersionedMatchConfig config, String createdBy, Instant createdAt, String approvedBy,
                    Instant effectiveFrom) {
                saved.add(config);
                approvers.add(approvedBy);
            }
        };
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        FakeIdGenerator ids = new FakeIdGenerator();
        return new PublishConfigVersion(repository, () -> lists, Registry.standard(),
                new RecordAuditEvent(audit, ids, clock), ids, clock);
    }

    @Test
    void aValidConfigIsSavedAndAudited() {
        UUID id = interactor().execute(new PublishConfigVersion.Command(
                "default", MatchConfig.defaults(), "operator-1", "compliance-lead"));

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).configVersionId()).isEqualTo(id);
        assertThat(approvers).containsExactly("compliance-lead");
        assertThat(audit.recorded()).hasSize(1);
        assertThat(audit.recorded().get(0).eventType()).isEqualTo("CONFIG_VERSION_PUBLISHED");
        assertThat(audit.recorded().get(0).subjectId()).isEqualTo(id.toString());
    }

    @Test
    void anInvalidConfigIsRefusedAndNothingIsSavedOrAudited() {
        // I-7: it fails here, at publication -- it can never become the config a screening uses.
        assertThatThrownBy(() -> interactor().execute(new PublishConfigVersion.Command(
                "default", MatchConfig.defaults().withThresholds(90, 70), "operator-1", "compliance-lead")))
                .isInstanceOf(InvalidMatchConfigException.class);

        assertThat(saved).isEmpty();
        assertThat(audit.recorded()).isEmpty();
    }

    @Test
    void publishingNeedsAnActiveListVersionToValidateAgainst() {
        lists = List.of();

        assertThatThrownBy(() -> interactor().execute(new PublishConfigVersion.Command(
                "default", MatchConfig.defaults(), "operator-1", "compliance-lead")))
                .isInstanceOf(ScreeningUnavailableException.class);
        assertThat(saved).isEmpty();
    }

    @Test
    void approverAndCreatorAreRequired() {
        assertThatThrownBy(() -> new PublishConfigVersion.Command("default", MatchConfig.defaults(), "op", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublishConfigVersion.Command("default", MatchConfig.defaults(), "", "lead"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
