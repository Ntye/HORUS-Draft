package horus.application.screening;

import horus.application.ingestion.RecordAuditEvent;
import horus.application.port.ActiveListVersion;
import horus.application.port.ActiveListVersionLookup;
import horus.application.port.IdGenerator;
import horus.application.port.MatchConfigRepository;
import horus.application.port.VersionedMatchConfig;
import horus.domain.audit.ActorKind;
import horus.matching.MatchConfig;
import horus.matching.Registry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// I-7: a configuration is validated when it is PUBLISHED, so an invalid one can never become what
// a screening runs on. Config rows are immutable: a change is a new version, never an edit. The
// approver is recorded but is asserted by the caller in this iteration -- there is no approval
// workflow yet (CLAUDE.md §12 descopes the admin surface); the audit event preserves who claimed
// what.
public final class PublishConfigVersion {

    public record Command(String profileId, MatchConfig config, String createdBy, String approvedBy) {

        public Command {
            if (profileId == null || profileId.isBlank() || createdBy == null || createdBy.isBlank()
                    || approvedBy == null || approvedBy.isBlank()) {
                throw new IllegalArgumentException("profileId, createdBy and approvedBy are required");
            }
            if (config == null) {
                throw new IllegalArgumentException("config must not be null");
            }
        }
    }

    private final MatchConfigRepository configRepository;
    private final ActiveListVersionLookup activeListVersionLookup;
    private final Registry registry;
    private final RecordAuditEvent recordAuditEvent;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public PublishConfigVersion(
            MatchConfigRepository configRepository,
            ActiveListVersionLookup activeListVersionLookup,
            Registry registry,
            RecordAuditEvent recordAuditEvent,
            IdGenerator idGenerator,
            Clock clock) {
        this.configRepository = configRepository;
        this.activeListVersionLookup = activeListVersionLookup;
        this.registry = registry;
        this.recordAuditEvent = recordAuditEvent;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public UUID execute(Command command) {
        List<ActiveListVersion> lists = activeListVersionLookup.findAllActive();
        if (lists.isEmpty()) {
            throw new ScreeningUnavailableException("no active list version to validate the configuration against");
        }
        registry.validateAgainst(command.config(), LoadScreeningConfig.capabilitiesOf(lists));

        UUID id = idGenerator.newId();
        Instant now = clock.instant();
        configRepository.save(new VersionedMatchConfig(id, command.profileId(), command.config()),
                command.createdBy(), now, command.approvedBy(), now);
        recordAuditEvent.execute(new RecordAuditEvent.Command(
                "CONFIG_VERSION_PUBLISHED", command.createdBy(), ActorKind.OPERATOR, "ConfigVersion", id.toString(),
                Map.of("profileId", command.profileId(), "approvedBy", command.approvedBy(),
                        "alertThreshold", String.valueOf(command.config().alertThreshold()),
                        "strongMatchThreshold", String.valueOf(command.config().strongMatchThreshold())),
                Optional.empty()));
        return id;
    }
}
