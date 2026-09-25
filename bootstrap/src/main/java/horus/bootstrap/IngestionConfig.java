package horus.bootstrap;

import horus.adapter.source.worldcheck.WorldCheckAdapter;
import horus.application.ingestion.ConfirmStagedVersion;
import horus.application.ingestion.LoadProgress;
import horus.application.ingestion.LoadWatchlistVersion;
import horus.application.ingestion.PromoteListVersion;
import horus.application.ingestion.ReconcileVersion;
import horus.application.ingestion.RecordAuditEvent;
import horus.application.ingestion.RollBackVersion;
import horus.application.ingestion.VerifySourceFixture;
import horus.application.port.ActiveEntityVersionLookup;
import horus.application.port.AuditEventPort;
import horus.application.port.IdGenerator;
import horus.application.port.IndexReconciliation;
import horus.application.port.ListVersionRepository;
import horus.application.port.WatchEntityRepository;
import horus.application.port.WatchEntityVersionWriter;
import horus.ingestion.format.FormatReader;
import horus.ingestion.format.XmlFormatReader;
import horus.ingestion.spi.AdapterRegistry;
import horus.normalisation.NormalisationPipeline;
import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Composition root for the ingestion use cases (CLAUDE.md §4 ring 0): the only place that wires
// horus.application interactors, which cannot be @Component-scanned themselves because
// horus.application depends on nothing but core and domain (ArchUnit rule 5) -- no Spring.
@Configuration
public class IngestionConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public NormalisationPipeline normalisationPipeline() {
        return NormalisationPipeline.standard();
    }

    @Bean
    public AdapterRegistry adapterRegistry() {
        AdapterRegistry registry = new AdapterRegistry();
        registry.register(new WorldCheckAdapter());
        return registry;
    }

    @Bean
    public Map<String, FormatReader> formatReadersByFormatId() {
        // Keyed by WatchlistSourceAdapter.formatId() (what LoadWatchlistVersion looks up by),
        // not FormatReader.formatId() (which just names the generic shape, "xml"). "record" is
        // the fixture's repeating element (adapters/src/test/resources/worldcheck/fixture-20-records.xml).
        return Map.of(WorldCheckAdapter.FORMAT_ID, new XmlFormatReader("record"));
    }

    @Bean
    public RecordAuditEvent recordAuditEvent(AuditEventPort auditEventPort, IdGenerator idGenerator, Clock clock) {
        return new RecordAuditEvent(auditEventPort, idGenerator, clock);
    }

    @Bean
    public ReconcileVersion reconcileVersion() {
        return new ReconcileVersion();
    }

    @Bean
    public VerifySourceFixture verifySourceFixture() {
        return new VerifySourceFixture();
    }

    @Bean
    public PromoteListVersion promoteListVersion(
            ListVersionRepository listVersionRepository,
            IndexReconciliation indexReconciliation,
            RecordAuditEvent recordAuditEvent) {
        return new PromoteListVersion(listVersionRepository, indexReconciliation, recordAuditEvent);
    }

    @Bean
    public RollBackVersion rollBackVersion(
            ListVersionRepository listVersionRepository, RecordAuditEvent recordAuditEvent) {
        return new RollBackVersion(listVersionRepository, recordAuditEvent);
    }

    @Bean
    public ConfirmStagedVersion confirmStagedVersion(
            ListVersionRepository listVersionRepository,
            PromoteListVersion promoteListVersion,
            RecordAuditEvent recordAuditEvent) {
        return new ConfirmStagedVersion(listVersionRepository, promoteListVersion, recordAuditEvent);
    }

    @Bean
    public LoadWatchlistVersion loadWatchlistVersion(
            AdapterRegistry adapterRegistry,
            Map<String, FormatReader> formatReadersByFormatId,
            NormalisationPipeline normalisationPipeline,
            ListVersionRepository listVersionRepository,
            WatchEntityRepository watchEntityRepository,
            WatchEntityVersionWriter watchEntityVersionWriter,
            ActiveEntityVersionLookup activeEntityVersionLookup,
            ReconcileVersion reconcileVersion,
            PromoteListVersion promoteListVersion,
            RecordAuditEvent recordAuditEvent,
            IdGenerator idGenerator,
            Clock clock,
            LoadProgress loadProgress) {
        return new LoadWatchlistVersion(
                adapterRegistry, formatReadersByFormatId, normalisationPipeline, listVersionRepository,
                watchEntityRepository, watchEntityVersionWriter, activeEntityVersionLookup,
                reconcileVersion, promoteListVersion, recordAuditEvent, idGenerator, clock, loadProgress);
    }
}
