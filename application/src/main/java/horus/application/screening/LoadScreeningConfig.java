package horus.application.screening;

import horus.application.port.ActiveListVersion;
import horus.application.port.ActiveListVersionLookup;
import horus.application.port.MatchConfigRepository;
import horus.application.port.VersionedMatchConfig;
import horus.domain.shared.CanonicalSlot;
import horus.matching.Registry;
import java.time.Clock;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// I-7: resolves the approved configuration and the list versions to screen against, and validates
// the one against the other BEFORE anything is scored. An invalid configuration throws
// InvalidMatchConfigException here -- it never reaches a screening.
public final class LoadScreeningConfig {

    public record Loaded(VersionedMatchConfig config, List<ActiveListVersion> activeListVersions) {
    }

    private final MatchConfigRepository configRepository;
    private final ActiveListVersionLookup activeListVersionLookup;
    private final Registry registry;
    private final Clock clock;

    public LoadScreeningConfig(
            MatchConfigRepository configRepository,
            ActiveListVersionLookup activeListVersionLookup,
            Registry registry,
            Clock clock) {
        this.configRepository = configRepository;
        this.activeListVersionLookup = activeListVersionLookup;
        this.registry = registry;
        this.clock = clock;
    }

    public Loaded execute(String profileId, String pipelineVersion) {
        List<ActiveListVersion> lists = activeListVersionLookup.findAllActive();
        if (lists.isEmpty()) {
            // I-12 needs at least one list version, and with none there is nothing to screen against.
            throw new ScreeningUnavailableException("no active list version to screen against");
        }
        // I-10: stored names and the query must be normalised by the same pipeline version, or
        // blocking and scoring silently compare unlike things. Fail closed until re-ingested.
        for (ActiveListVersion list : lists) {
            if (!list.pipelineVersion().equals(pipelineVersion)) {
                throw new ScreeningUnavailableException("active list version " + list.listVersionId().value()
                        + " was normalised with pipeline " + list.pipelineVersion() + " but screening runs "
                        + pipelineVersion + "; re-ingest required");
            }
        }
        VersionedMatchConfig config = configRepository.findCurrent(profileId, clock.instant())
                .orElseThrow(() -> new ScreeningUnavailableException(
                        "no approved configuration for profile " + profileId));
        registry.validateAgainst(config.config(), capabilitiesOf(lists));
        return new Loaded(config, lists);
    }

    // The union across sources: a comparator is meaningful if at least one source can feed it. A
    // candidate from a source that cannot is compared neutrally and reported as a capability gap.
    static Set<CanonicalSlot> capabilitiesOf(List<ActiveListVersion> lists) {
        Set<CanonicalSlot> union = EnumSet.noneOf(CanonicalSlot.class);
        lists.forEach(l -> union.addAll(l.capabilities()));
        return union;
    }
}
