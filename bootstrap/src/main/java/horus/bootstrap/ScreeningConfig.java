package horus.bootstrap;

import horus.application.benchmark.RunBenchmark;
import horus.application.ingestion.RecordAuditEvent;
import horus.application.port.ActiveListVersionLookup;
import horus.application.port.CandidateViewAssembler;
import horus.application.port.IdGenerator;
import horus.application.port.MatchConfigRepository;
import horus.application.port.ScreeningRepository;
import horus.application.port.WhitelistViewProvider;
import horus.application.screening.LoadScreeningConfig;
import horus.application.screening.PublishConfigVersion;
import horus.application.screening.ScreenAName;
import horus.blocking.CandidateGenerator;
import horus.matching.MatchingEngine;
import horus.matching.Registry;
import horus.matching.WhitelistView;
import horus.normalisation.NormalisationPipeline;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Composition root for screening (CLAUDE.md §4 ring 0). The interactors live in horus.application
// and cannot be component-scanned (no Spring there), so they are wired here.
@Configuration
public class ScreeningConfig {

    @Bean
    public Registry matchingRegistry() {
        return Registry.standard();
    }

    @Bean
    public MatchingEngine matchingEngine(Registry matchingRegistry) {
        return new MatchingEngine(matchingRegistry);
    }

    // Whitelist storage is out of scope for this iteration (CLAUDE.md §12); the engine's
    // flag-and-demote behaviour is wired, and an empty view means "nothing whitelisted".
    @Bean
    public WhitelistViewProvider whitelistViewProvider() {
        return query -> WhitelistView.empty();
    }

    @Bean
    public LoadScreeningConfig loadScreeningConfig(
            MatchConfigRepository matchConfigRepository,
            ActiveListVersionLookup activeListVersionLookup,
            Registry matchingRegistry,
            Clock clock) {
        return new LoadScreeningConfig(matchConfigRepository, activeListVersionLookup, matchingRegistry, clock);
    }

    @Bean
    public PublishConfigVersion publishConfigVersion(
            MatchConfigRepository matchConfigRepository,
            ActiveListVersionLookup activeListVersionLookup,
            Registry matchingRegistry,
            RecordAuditEvent recordAuditEvent,
            IdGenerator idGenerator,
            Clock clock) {
        return new PublishConfigVersion(
                matchConfigRepository, activeListVersionLookup, matchingRegistry, recordAuditEvent, idGenerator, clock);
    }

    @Bean
    public ScreenAName screenAName(
            NormalisationPipeline normalisationPipeline,
            CandidateGenerator candidateGenerator,
            CandidateViewAssembler candidateViewAssembler,
            MatchingEngine matchingEngine,
            LoadScreeningConfig loadScreeningConfig,
            WhitelistViewProvider whitelistViewProvider,
            ScreeningRepository screeningRepository,
            RecordAuditEvent recordAuditEvent,
            IdGenerator idGenerator,
            Clock clock) {
        return new ScreenAName(normalisationPipeline, candidateGenerator, candidateViewAssembler, matchingEngine,
                loadScreeningConfig, whitelistViewProvider, screeningRepository, recordAuditEvent, idGenerator, clock);
    }

    @Bean
    public RunBenchmark runBenchmark(
            ScreenAName screenAName,
            LoadScreeningConfig loadScreeningConfig,
            NormalisationPipeline normalisationPipeline,
            IdGenerator idGenerator) {
        return new RunBenchmark(screenAName, loadScreeningConfig, normalisationPipeline.pipelineVersion(), idGenerator);
    }
}
