package horus.application.ingestion;

import horus.application.port.ActiveEntitySnapshot;
import horus.application.port.ActiveEntityVersionLookup;
import horus.application.port.IdGenerator;
import horus.application.port.ListVersionRepository;
import horus.application.port.RecordPersistenceException;
import horus.application.port.WatchEntityRepository;
import horus.application.port.WatchEntityVersionAggregate;
import horus.application.port.WatchEntityVersionWriter;
import horus.domain.audit.ActorKind;
import horus.domain.listversion.ListVersion;
import horus.domain.listversion.LoadStatus;
import horus.domain.listversion.SourceCapabilities;
import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.ListVersionId;
import horus.domain.watchentity.WatchEntity;
import horus.domain.watchentity.WatchEntityVersion;
import horus.ingestion.format.FormatReader;
import horus.ingestion.format.RawRecord;
import horus.ingestion.spi.AdapterRegistry;
import horus.ingestion.spi.CanonicalRecord;
import horus.ingestion.spi.CapabilityDeriver;
import horus.ingestion.spi.CapabilityGate;
import horus.ingestion.spi.ContentHasher;
import horus.ingestion.spi.RecordMappingException;
import horus.ingestion.spi.WatchlistSourceAdapter;
import horus.normalisation.NormalisationPipeline;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

// §13.1 stages 3-8 (PARSE through RECONCILE), plus 9-10 (INDEX/PROMOTE) when the delta is
// unremarkable. Streams the file exactly once: bounded memory except for two structures whose
// size scales with the source's currently-active entity count, not its record count -- the
// active snapshot (ActiveEntityVersionLookup) and the seen-keys set below. Both are a known,
// deliberately deferred cost for the real 5.99M-record feed (Step 5 plan, decision (c)).
public final class LoadWatchlistVersion {

    private static final double MAX_REJECT_FRACTION = 0.05;
    // Often enough to see a trend within the first minute of a 5.99M-record load.
    private static final long PROGRESS_EVERY = 25_000;
    // Consecutive-failure budget before a load is abandoned unread. Larger than any plausible run
    // of legitimately bad records at the head of a file; small enough that the answer arrives in
    // seconds rather than hours.
    private static final int EARLY_ABORT_AFTER = 1_000;
    // Entities per write transaction. A mechanical tuning parameter, not policy, so it is a constant
    // rather than part of ConfigVersion (I-7 governs thresholds, weights and list selection). At a
    // thousand, commits and round-trips both drop by three orders of magnitude while a chunk still
    // holds only a few thousand rows in memory, and a failed chunk is a few thousand rows to replay.
    private static final int CHUNK_SIZE = 1_000;
    private static final double ANOMALOUS_DELTA_FRACTION = 0.20;

    private final AdapterRegistry adapterRegistry;
    private final Map<String, FormatReader> formatReadersByFormatId;
    private final NormalisationPipeline normalisationPipeline;
    private final ListVersionRepository listVersionRepository;
    private final WatchEntityRepository watchEntityRepository;
    private final WatchEntityVersionWriter watchEntityVersionWriter;
    private final ActiveEntityVersionLookup activeEntityVersionLookup;
    private final ReconcileVersion reconcileVersion;
    private final PromoteListVersion promoteListVersion;
    private final RecordAuditEvent recordAuditEvent;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final LoadProgress progress;

    public LoadWatchlistVersion(
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
            Clock clock) {
        this(adapterRegistry, formatReadersByFormatId, normalisationPipeline,
                listVersionRepository, watchEntityRepository, watchEntityVersionWriter,
                activeEntityVersionLookup, reconcileVersion, promoteListVersion, recordAuditEvent,
                idGenerator, clock, LoadProgress.NONE);
    }

    // Same collaborators plus a progress reporter, so a multi-hour load can say where its time goes.
    public LoadWatchlistVersion(
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
            LoadProgress progress) {
        this.adapterRegistry = adapterRegistry;
        this.formatReadersByFormatId = Map.copyOf(formatReadersByFormatId);
        this.normalisationPipeline = normalisationPipeline;
        this.listVersionRepository = listVersionRepository;
        this.watchEntityRepository = watchEntityRepository;
        this.watchEntityVersionWriter = watchEntityVersionWriter;
        this.activeEntityVersionLookup = activeEntityVersionLookup;
        this.reconcileVersion = reconcileVersion;
        this.promoteListVersion = promoteListVersion;
        this.recordAuditEvent = recordAuditEvent;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.progress = progress;
    }

    public enum Outcome {
        PROMOTED,
        ALREADY_LOADED,
        AWAITING_CONFIRMATION,
        REJECTED
    }

    public record Command(
            String sourceId,
            InputStream rawStream,
            String sourceFileName,
            String sourceFileChecksum,
            Optional<Instant> vendorPublishedAt,
            String loadedBy) {

        public Command {
            if (sourceId == null || sourceId.isBlank()) {
                throw new IllegalArgumentException("sourceId must not be blank");
            }
            if (rawStream == null) {
                throw new IllegalArgumentException("rawStream must not be null");
            }
            if (sourceFileName == null || sourceFileName.isBlank()) {
                throw new IllegalArgumentException("sourceFileName must not be blank");
            }
            if (sourceFileChecksum == null || sourceFileChecksum.isBlank()) {
                throw new IllegalArgumentException("sourceFileChecksum must not be blank");
            }
            if (vendorPublishedAt == null) {
                throw new IllegalArgumentException("vendorPublishedAt must not be null (use Optional.empty())");
            }
            if (loadedBy == null || loadedBy.isBlank()) {
                throw new IllegalArgumentException("loadedBy must not be blank");
            }
        }
    }

    public record Result(
            Outcome outcome,
            ListVersionId listVersionId,
            ReconciliationReport reconciliationReport,
            Optional<String> failureReason) {
    }

    public Result execute(Command command) {
        WatchlistSourceAdapter adapter = adapterRegistry.resolve(command.sourceId());
        FormatReader reader = formatReadersByFormatId.get(adapter.formatId());
        if (reader == null) {
            throw new IllegalStateException("no FormatReader registered for formatId " + adapter.formatId());
        }

        // §13.2 IDEMPOTENCY: re-running the exact same file must not produce duplicate state.
        Optional<ListVersion> currentActive = listVersionRepository.findActive(command.sourceId());
        if (currentActive.isPresent()
                && currentActive.get().sourceFileChecksum().equals(command.sourceFileChecksum())) {
            return new Result(
                    Outcome.ALREADY_LOADED,
                    currentActive.get().listVersionId(),
                    new ReconciliationReport(command.sourceId(), 0, 0, 0, 0, List.of()),
                    Optional.empty());
        }

        ListVersionId listVersionId = new ListVersionId(idGenerator.newId());
        Instant loadedAt = clock.instant();
        listVersionRepository.save(new ListVersion(
                listVersionId, command.sourceId(), adapter.formatId(), Optional.empty(),
                command.sourceFileName(), command.sourceFileChecksum(), command.vendorPublishedAt(),
                loadedAt, 0L, LoadStatus.STAGED, command.loadedBy(), Optional.empty(),
                normalisationPipeline.pipelineVersion(), Optional.empty()));

        Map<String, ActiveEntitySnapshot> activeSnapshot = activeEntityVersionLookup.loadActiveSnapshot(command.sourceId());
        CanonicalRecordMapper mapper = new CanonicalRecordMapper(normalisationPipeline, idGenerator);
        CapabilityDeriver capabilityDeriver = new CapabilityDeriver();
        Set<String> seen = new TreeSet<>();
        List<ReconciliationReport.RejectedRecord> rejected = new ArrayList<>();
        long totalRecords = 0;
        int added = 0;
        int amended = 0;
        int unchanged = 0;
        boolean abortedEarly = false;
        List<PendingWrite> pending = new ArrayList<>(CHUNK_SIZE);
        // nanoTime, not the injected Clock: this measures a duration, and only to report it.
        // Nothing timed here reaches the report, so the load stays reproducible (I-4).
        long loopStart = System.nanoTime();
        long mapNanos = 0;
        long persistNanos = 0;

        Iterator<RawRecord> rawRecords = reader.open(command.rawStream());
        try {
            while (rawRecords.hasNext()) {
                // Checked here, at the top, because the quarantine paths below all `continue` --
                // a load failing at the MAPPING stage would never reach a check at the bottom, and
                // that is the commonest way for a whole file to fail (D12 B-6).
                //
                // Stop reading once the first EARLY_ABORT_AFTER records have produced nothing but
                // rejections. The reject-fraction gate already fails such a load, but only after
                // the whole file has streamed: on 2026-09-25 a 100,000-record extract spent three
                // minutes proving a condition that was true at record 0, and the same run against
                // the 9.88 GiB feed would have spent hours doing it. Requiring ZERO successes makes
                // a false trip effectively impossible, and the outcome is the one the gate would
                // have reached anyway -- REJECTED, nothing promoted, the previous version still
                // serving (I-1).
                // pending counts too: a record that mapped and reached a decision is a success,
                // even though its row has not been sent yet. Without it, a file whose only good
                // records are still in the buffer would be abandoned as if nothing worked -- which
                // is exactly what aSingleSuccessWithinTheBudgetLetsTheLoadRunToTheEnd caught.
                if (rejected.size() >= EARLY_ABORT_AFTER
                        && added + amended + unchanged + pending.size() == 0) {
                    abortedEarly = true;
                    break;
                }
                RawRecord raw = rawRecords.next();
                totalRecords++;
                CanonicalRecord canonical;
                long mapStart = System.nanoTime();
                try {
                    canonical = adapter.toCanonical(raw);
                    mapNanos += System.nanoTime() - mapStart;
                } catch (RuntimeException e) {
                    mapNanos += System.nanoTime() - mapStart;
                    // §13.1 VALIDATE: unmapped/malformed records are quarantined with a line
                    // reference, never silently dropped and never corrupting the rest of the run.
                    // The reason is the adapter's own controlled code where it has one: 5,979,934
                    // records were once rejected as an undifferentiated "IllegalStateException"
                    // (D12 B-6). It is never the exception's message, which would interpolate the
                    // offending value -- in this feed, a name or a date of birth (I-11).
                    rejected.add(new ReconciliationReport.RejectedRecord(
                            String.valueOf(raw.ordinal()), RecordMappingException.reasonCodeOf(e)));
                    continue;
                }
                // The adapter declares the slots it cannot do without, and until now nothing read
                // that declaration: a record with no primary name was ingested as a success so
                // long as it carried one alias -- effectively unfindable by name blocking. This is
                // B-5's lesson in a second place: a control is not in force until something
                // asserts the path the application actually takes. Enforcing it here means a feed
                // that changes shape trips the 5% reject gate and fails closed (I-1), instead of
                // quietly degrading recall.
                Optional<CanonicalSlot> missing = firstMissingRequiredSlot(adapter, canonical);
                if (missing.isPresent()) {
                    rejected.add(new ReconciliationReport.RejectedRecord(
                            String.valueOf(raw.ordinal()), "MISSING_REQUIRED_SLOT:" + missing.get()));
                    continue;
                }
                capabilityDeriver.observe(canonical);
                String sourceEntityId = canonical.sourceRecordId();
                seen.add(sourceEntityId);
                String contentHash = ContentHasher.hash(canonical);
                Optional<ActiveEntitySnapshot> existing = Optional.ofNullable(activeSnapshot.get(sourceEntityId));

                switch (reconcileVersion.decide(existing, contentHash)) {
                    case UNCHANGED -> unchanged++;
                    case ADD -> pending.add(new PendingWrite(
                            raw.ordinal(), sourceEntityId, canonical, contentHash, Optional.empty()));
                    case AMEND -> pending.add(new PendingWrite(
                            raw.ordinal(), sourceEntityId, canonical, contentHash,
                            Optional.of(existing.orElseThrow().entityId())));
                }

                // Writes are held back and sent a chunk at a time: one transaction and one statement
                // per table for a thousand entities, instead of one transaction and ten round-trips
                // for each. See WatchEntityVersionWriter.write for the measurements. The buffer is
                // what makes per-record quarantine survive that change -- a chunk that fails is
                // replayed one record at a time, and the ordinals are still here to name the
                // culprit (I-1).
                if (pending.size() >= CHUNK_SIZE) {
                    long persistStart = System.nanoTime();
                    FlushResult flushed = flush(pending, mapper, command.sourceId(), listVersionId);
                    persistNanos += System.nanoTime() - persistStart;
                    added += flushed.added();
                    amended += flushed.amended();
                    rejected.addAll(flushed.rejected());
                    pending.clear();
                }

                if (totalRecords % PROGRESS_EVERY == 0) {
                    progress.onProgress(new LoadProgress.Snapshot(totalRecords, added, amended, unchanged,
                            rejected.size(), mapNanos, persistNanos, System.nanoTime() - loopStart, false));
                }
            }
            // The tail: whatever the last full chunk left over. Without this, up to CHUNK_SIZE - 1
            // records at the end of every file would be silently dropped -- and the version-count
            // check below would catch it, but as a failed load rather than a correct one.
            if (!pending.isEmpty()) {
                long persistStart = System.nanoTime();
                FlushResult flushed = flush(pending, mapper, command.sourceId(), listVersionId);
                persistNanos += System.nanoTime() - persistStart;
                added += flushed.added();
                amended += flushed.amended();
                rejected.addAll(flushed.rejected());
                pending.clear();
            }
        } finally {
            closeQuietly(command.rawStream());
            progress.onProgress(new LoadProgress.Snapshot(totalRecords, added, amended, unchanged,
                    rejected.size(), mapNanos, persistNanos, System.nanoTime() - loopStart, true));
        }

        ReconciliationReport failedReport =
                new ReconciliationReport(command.sourceId(), added, amended, unchanged, 0, rejected);
        if (abortedEarly) {
            String dominantReason = failedReport.reasonCounts().entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("UNKNOWN");
            listVersionRepository.updateStatus(listVersionId, LoadStatus.FAILED);
            recordAuditEvent.execute(new RecordAuditEvent.Command(
                    "LIST_VERSION_LOAD_REJECTED", command.loadedBy(), ActorKind.SYSTEM, "ListVersion",
                    listVersionId.value().toString(),
                    Map.of("reason", "abandoned after consecutive failures",
                            "recordsRead", String.valueOf(totalRecords),
                            "dominantReason", dominantReason),
                    Optional.empty()));
            return new Result(Outcome.REJECTED, listVersionId, failedReport,
                    Optional.of("abandoned after " + totalRecords + " records, none of which succeeded; "
                            + "most common reason " + dominantReason));
        }

        double rejectFraction = totalRecords == 0 ? 0.0 : rejected.size() / (double) totalRecords;
        if (rejectFraction > MAX_REJECT_FRACTION) {
            listVersionRepository.updateStatus(listVersionId, LoadStatus.FAILED);
            recordAuditEvent.execute(new RecordAuditEvent.Command(
                    "LIST_VERSION_LOAD_REJECTED", command.loadedBy(), ActorKind.SYSTEM, "ListVersion",
                    listVersionId.value().toString(),
                    Map.of("reason", "reject fraction exceeded threshold", "rejectedCount", String.valueOf(rejected.size())),
                    Optional.empty()));
            return new Result(
                    Outcome.REJECTED, listVersionId, failedReport,
                    Optional.of("more than " + (int) (MAX_REJECT_FRACTION * 100) + "% of records failed validation"));
        }

        int delisted = 0;
        LocalDate today = LocalDate.now(clock);
        // Tombstones are chunked for the same reason the rest is. A full de-listing wave can be
        // large: 22.77% of sanctioned records in the feed are already off every list (CLAUDE.md §6).
        List<WatchEntityVersionAggregate> tombstones = new ArrayList<>(CHUNK_SIZE);
        for (String sourceEntityId : reconcileVersion.delistedKeys(activeSnapshot, seen)) {
            WatchEntityVersion previous = activeEntityVersionLookup
                    .findActiveVersion(command.sourceId(), sourceEntityId)
                    .orElseThrow(() -> new IllegalStateException(
                            "active version vanished mid-load for a delisted entity"));
            EntityVersionId tombstoneId = new EntityVersionId(idGenerator.newId());
            WatchEntityVersion tombstone = WatchEntityVersion.tombstone(
                    previous, tombstoneId, listVersionId, previous.contentHash(), today);
            tombstones.add(new WatchEntityVersionAggregate(
                    tombstone, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of()));
            delisted++;
            if (tombstones.size() >= CHUNK_SIZE) {
                watchEntityVersionWriter.write(tombstones);
                tombstones.clear();
            }
        }

        watchEntityVersionWriter.write(tombstones);
        tombstones.clear();

        SourceCapabilities capabilities = new SourceCapabilities(
                listVersionId, command.sourceId(), capabilityDeriver.derive(), clock.instant());
        listVersionRepository.updateMeasuredTotals(listVersionId, totalRecords, capabilities);

        ReconciliationReport report =
                new ReconciliationReport(command.sourceId(), added, amended, unchanged, delisted, rejected);

        // The store must hold exactly the version rows this load believes it wrote. Every ADD,
        // AMEND and DELIST writes one version; UNCHANGED writes none. A shortfall means rows went
        // missing without an exception reaching here -- and an entity with no version row is never
        // screened, which is a recall failure no benchmark would reveal (I-1, I-2). Checked before
        // any return path that could lead to a promotion, including AWAITING_CONFIRMATION.
        //
        // IndexReconciliation cannot do this: at promote time it has no independent expectation to
        // compare against, and list_version.record_count counts records READ, which on a reload
        // exceeds the versions written by however many were UNCHANGED.
        long expectedVersions = (long) added + amended + delisted;
        long storedVersions = watchEntityVersionWriter.countVersionsIn(listVersionId);
        if (storedVersions != expectedVersions) {
            listVersionRepository.updateStatus(listVersionId, LoadStatus.FAILED);
            recordAuditEvent.execute(new RecordAuditEvent.Command(
                    "LIST_VERSION_LOAD_REJECTED", command.loadedBy(), ActorKind.SYSTEM, "ListVersion",
                    listVersionId.value().toString(),
                    Map.of("reason", "version row count mismatch",
                            "expectedVersions", String.valueOf(expectedVersions),
                            "storedVersions", String.valueOf(storedVersions)),
                    Optional.empty()));
            return new Result(Outcome.REJECTED, listVersionId, report,
                    Optional.of("stored version rows (" + storedVersions + ") do not match the "
                            + expectedVersions + " this load wrote"));
        }

        recordAuditEvent.execute(new RecordAuditEvent.Command(
                "LIST_VERSION_STAGED", command.loadedBy(), ActorKind.SYSTEM, "ListVersion",
                listVersionId.value().toString(),
                Map.of("added", String.valueOf(added), "amended", String.valueOf(amended),
                        "unchanged", String.valueOf(unchanged), "delisted", String.valueOf(delisted)),
                Optional.empty()));

        // A capability deviation HOLDS the load; it does not fail it. The rows are written and
        // consistent -- the version count above has just been checked -- and what is in doubt is the
        // SHAPE of the file against the adapter's expectation. That is a judgement, not a defect.
        //
        // It used to mark the load FAILED, and nothing moves a FAILED version back to STAGED
        // (PromoteListVersion accepts STAGED only), so a deviation discarded the entire load. On the
        // real feed that is hours of work thrown away over a figure a person has to rule on anyway,
        // and the IDENTIFIER tolerance is 0.0074 +/- 0.005 -- a narrow band, compared against
        // whole-file figures profiled from a DIFFERENT snapshot of the file than the one being
        // loaded (CLAUDE.md §6).
        //
        // Still closed by default (I-1): STAGED does not screen, the previous ACTIVE version keeps
        // serving, and only ConfirmStagedVersion can promote it -- with a recorded reason.
        CapabilityGate gate = CapabilityGate.evaluate(adapter.expectation(), capabilityDeriver.derive());
        if (!gate.passes()) {
            recordAuditEvent.execute(new RecordAuditEvent.Command(
                    "CAPABILITY_DEVIATION_AWAITING_CONFIRMATION", command.loadedBy(), ActorKind.SYSTEM,
                    "ListVersion", listVersionId.value().toString(),
                    Map.of("deviations", String.valueOf(gate.deviations().size())),
                    Optional.empty()));
            return new Result(Outcome.AWAITING_CONFIRMATION, listVersionId, report,
                    Optional.of("measured capabilities fell outside tolerance: " + gate.deviations()));
        }

        // A source's first-ever load has no baseline to be anomalous relative to -- only a
        // change against an existing active version can be "too large".
        boolean isFirstEverLoadForSource = activeSnapshot.isEmpty();
        if (!isFirstEverLoadForSource && report.deltaFraction(activeSnapshot.size()) > ANOMALOUS_DELTA_FRACTION) {
            recordAuditEvent.execute(new RecordAuditEvent.Command(
                    "ANOMALOUS_DELTA_AWAITING_CONFIRMATION", command.loadedBy(), ActorKind.SYSTEM,
                    "ListVersion", listVersionId.value().toString(), Map.of(), Optional.empty()));
            return new Result(Outcome.AWAITING_CONFIRMATION, listVersionId, report, Optional.empty());
        }

        promoteListVersion.execute(
                new PromoteListVersion.Command(listVersionId, command.sourceId(), command.loadedBy()));
        return new Result(Outcome.PROMOTED, listVersionId, report, Optional.empty());
    }

    // The deepest cause's CLASS NAME, never its message. A JDBC message quotes the statement and
    // often the offending value, which in this feed is a name, an address or a date of birth
    // (I-11); a class name says enough to tell a constraint violation from a lost connection.
    // Reached through getCause() alone, because horus.application may not import java.sql or
    // Spring (ArchUnit rule 1) and so cannot read an SQLState.
    private static String persistenceReason(RuntimeException e) {
        if (e instanceof RecordPersistenceException persistence) {
            return "PERSIST_FAILED:" + persistence.reasonCode();
        }
        Throwable deepest = e;
        while (deepest.getCause() != null && deepest.getCause() != deepest) {
            deepest = deepest.getCause();
        }
        String name = deepest.getClass().getSimpleName();
        return "PERSIST_FAILED:" + (name.length() <= 60 ? name : name.substring(0, 60));
    }

    // Sorted so the same record always reports the same reason, whatever the Set's iteration
    // order happens to be (I-4: same input, same list version, same config => same result).
    private static Optional<CanonicalSlot> firstMissingRequiredSlot(
            WatchlistSourceAdapter adapter, CanonicalRecord canonical) {
        return adapter.requiredSlots().stream()
                .sorted()
                .filter(slot -> !canonical.has(slot))
                .findFirst();
    }

    // A decision taken but not yet written. Holds the ordinal, because that is the only thing the
    // report may say about a record that fails (I-11), and the canonical record, because the
    // aggregate cannot be built until the entity id is known.
    private record PendingWrite(
            long ordinal,
            String sourceEntityId,
            CanonicalRecord canonical,
            String contentHash,
            Optional<EntityId> existingEntityId) {
    }

    private record FlushResult(int added, int amended, List<ReconciliationReport.RejectedRecord> rejected) {
    }

    /**
     * Writes one chunk: identities first, in one round-trip, then every version and child row in one
     * transaction.
     *
     * <p>If the chunk write fails, the chunk is replayed one record at a time. That is the only way
     * to keep the guarantee the per-record path used to give for free -- a single bad row quarantines
     * a single record and the load carries on (I-1) -- and it costs nothing on the normal path.
     */
    private FlushResult flush(
            List<PendingWrite> pending, CanonicalRecordMapper mapper, String sourceId,
            ListVersionId listVersionId) {
        List<WatchEntity> candidates = pending.stream()
                .filter(p -> p.existingEntityId().isEmpty())
                .map(p -> new WatchEntity(new EntityId(idGenerator.newId()), sourceId, p.sourceEntityId()))
                .toList();

        Map<String, EntityId> identities;
        try {
            identities = watchEntityRepository.ensureAll(candidates);
        } catch (RuntimeException e) {
            // The identity write is the whole chunk's, so its failure says nothing about which
            // record caused it. Replay to find out.
            return replayOneByOne(pending, mapper, sourceId, listVersionId);
        }

        List<WatchEntityVersionAggregate> aggregates = new ArrayList<>(pending.size());
        List<PendingWrite> written = new ArrayList<>(pending.size());
        List<ReconciliationReport.RejectedRecord> rejected = new ArrayList<>();
        for (PendingWrite p : pending) {
            EntityId entityId = p.existingEntityId().orElseGet(() -> identities.get(p.sourceEntityId()));
            if (entityId == null) {
                // An identity that did not come back is a record that cannot be written. Never
                // written-and-forgotten: it is quarantined and counted (I-1).
                rejected.add(new ReconciliationReport.RejectedRecord(
                        String.valueOf(p.ordinal()), "IDENTITY_UNRESOLVED"));
                continue;
            }
            aggregates.add(mapper.toAggregate(p.canonical(), entityId,
                    new EntityVersionId(idGenerator.newId()), listVersionId, p.contentHash()));
            written.add(p);
        }

        try {
            watchEntityVersionWriter.write(aggregates);
        } catch (RuntimeException e) {
            FlushResult replayed = replayOneByOne(written, mapper, sourceId, listVersionId);
            List<ReconciliationReport.RejectedRecord> all = new ArrayList<>(rejected);
            all.addAll(replayed.rejected());
            return new FlushResult(replayed.added(), replayed.amended(), List.copyOf(all));
        }

        int added = (int) written.stream().filter(p -> p.existingEntityId().isEmpty()).count();
        return new FlushResult(added, written.size() - added, List.copyOf(rejected));
    }

    private FlushResult replayOneByOne(
            List<PendingWrite> pending, CanonicalRecordMapper mapper, String sourceId,
            ListVersionId listVersionId) {
        int added = 0;
        int amended = 0;
        List<ReconciliationReport.RejectedRecord> rejected = new ArrayList<>();
        for (PendingWrite p : pending) {
            try {
                EntityId entityId = p.existingEntityId().orElseGet(() -> watchEntityRepository.ensure(
                        new WatchEntity(new EntityId(idGenerator.newId()), sourceId, p.sourceEntityId())));
                watchEntityVersionWriter.write(List.of(mapper.toAggregate(
                        p.canonical(), entityId, new EntityVersionId(idGenerator.newId()),
                        listVersionId, p.contentHash())));
                if (p.existingEntityId().isEmpty()) {
                    added++;
                } else {
                    amended++;
                }
            } catch (RuntimeException e) {
                rejected.add(new ReconciliationReport.RejectedRecord(
                        String.valueOf(p.ordinal()), persistenceReason(e)));
            }
        }
        return new FlushResult(added, amended, List.copyOf(rejected));
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
