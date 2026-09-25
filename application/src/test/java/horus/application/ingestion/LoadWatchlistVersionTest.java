package horus.application.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import horus.application.ingestion.testsupport.FakeActiveEntityVersionLookup;
import horus.application.ingestion.testsupport.FakeAuditEventPort;
import horus.application.ingestion.testsupport.FakeIdGenerator;
import horus.application.ingestion.testsupport.FakeIndexReconciliation;
import horus.application.ingestion.testsupport.FakeListVersionRepository;
import horus.application.ingestion.testsupport.FakeWatchEntityRepository;
import horus.application.ingestion.testsupport.FakeWatchEntityVersionWriter;
import horus.application.port.WatchEntityVersionAggregate;
import horus.domain.listversion.LoadStatus;
import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityType;
import horus.domain.watchentity.NameType;
import horus.domain.watchentity.WatchEntity;
import horus.ingestion.format.FormatReader;
import horus.ingestion.format.RawRecord;
import horus.ingestion.spi.AdapterRegistry;
import horus.ingestion.spi.CanonicalRecord;
import horus.ingestion.spi.CanonicalRecordBuilder;
import horus.ingestion.spi.CapabilityExpectation;
import horus.ingestion.spi.RecordMappingException;
import horus.ingestion.spi.WatchlistSourceAdapter;
import horus.normalisation.NormalisationPipeline;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LoadWatchlistVersionTest {

    private static final String SOURCE_ID = "test-source";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-21T01:00:00Z"), ZoneOffset.UTC);
    private static final NormalisationPipeline PIPELINE = NormalisationPipeline.standard();

    private static final WatchlistSourceAdapter ADAPTER = new WatchlistSourceAdapter() {
        @Override
        public String sourceId() {
            return SOURCE_ID;
        }

        @Override
        public String formatId() {
            return "test-format";
        }

        @Override
        public CanonicalRecord toCanonical(RawRecord r) {
            return new CanonicalRecordBuilder()
                    .sourceId(SOURCE_ID)
                    .sourceRecordId(r.get("id").get(0))
                    .entityType(EntityType.INDIVIDUAL, "type")
                    .name(r.get("name").get(0), NameType.PRIMARY, "name")
                    .build();
        }

        @Override
        public CapabilityExpectation expectation() {
            // Empty expected map: CapabilityExpectation.compare() has nothing to check against,
            // so the gate always passes -- capability-gate behaviour has its own dedicated tests.
            return new CapabilityExpectation(SOURCE_ID, Map.of(), Map.of());
        }

        @Override
        public Set<CanonicalSlot> requiredSlots() {
            return Set.of(CanonicalSlot.PRIMARY_NAME);
        }
    };

    /** ADAPTER, except that mapping a record whose id is not {@code goodId} fails. */
    private static WatchlistSourceAdapter failsToMapUnless(String goodId) {
        return new WatchlistSourceAdapter() {
            @Override
            public String sourceId() {
                return SOURCE_ID;
            }

            @Override
            public String formatId() {
                return "test-format";
            }

            @Override
            public CanonicalRecord toCanonical(RawRecord r) {
                if (goodId != null && goodId.equals(r.get("id").get(0))) {
                    return ADAPTER.toCanonical(r);
                }
                throw new RecordMappingException("MAPPING_FAILED");
            }

            @Override
            public CapabilityExpectation expectation() {
                return new CapabilityExpectation(SOURCE_ID, Map.of(), Map.of());
            }

            @Override
            public Set<CanonicalSlot> requiredSlots() {
                return Set.of(CanonicalSlot.PRIMARY_NAME);
            }
        };
    }

    private static WatchlistSourceAdapter alwaysFailsToMap() {
        return failsToMapUnless(null);
    }

    /**
     * ADAPTER, but expecting a slot coverage the records will not produce, so the capability gate
     * deviates. Every test record carries a primary name and nothing else, so expecting DATE_OF_BIRTH
     * on every record is a guaranteed deviation of 1.0 against a tolerance of 0.05.
     */
    private static WatchlistSourceAdapter expectingDobOnEveryRecord() {
        return new WatchlistSourceAdapter() {
            @Override
            public String sourceId() {
                return SOURCE_ID;
            }

            @Override
            public String formatId() {
                return "test-format";
            }

            @Override
            public CanonicalRecord toCanonical(RawRecord r) {
                // absent(), explicitly: CapabilityDeriver reads has(slot) as
                // !absentSlots.contains(slot), so a slot an adapter never mentions measures as 100%
                // covered, not 0% (D12 M-12). Declaring the absence is what WorldCheckAdapter does
                // and what makes the derived coverage mean anything.
                return new CanonicalRecordBuilder()
                        .sourceId(SOURCE_ID)
                        .sourceRecordId(r.get("id").get(0))
                        .entityType(EntityType.INDIVIDUAL, "type")
                        .name(r.get("name").get(0), NameType.PRIMARY, "name")
                        .absent(CanonicalSlot.DATE_OF_BIRTH, "not in fixture")
                        .build();
            }

            @Override
            public CapabilityExpectation expectation() {
                return new CapabilityExpectation(SOURCE_ID,
                        Map.of(CanonicalSlot.DATE_OF_BIRTH, 1.0),
                        Map.of(CanonicalSlot.DATE_OF_BIRTH, 0.05));
            }

            @Override
            public Set<CanonicalSlot> requiredSlots() {
                return Set.of(CanonicalSlot.PRIMARY_NAME);
            }
        };
    }

    private record TestHarness(
            FakeListVersionRepository listVersionRepository,
            FakeWatchEntityRepository watchEntityRepository,
            FakeWatchEntityVersionWriter watchEntityVersionWriter,
            FakeActiveEntityVersionLookup activeEntityVersionLookup,
            FakeAuditEventPort auditEventPort,
            FakeIdGenerator idGenerator) {
    }

    private static TestHarness harness() {
        return new TestHarness(
                new FakeListVersionRepository(),
                new FakeWatchEntityRepository(),
                new FakeWatchEntityVersionWriter(),
                new FakeActiveEntityVersionLookup(),
                new FakeAuditEventPort(),
                new FakeIdGenerator());
    }

    // Every fake in the harness persists across calls (as a real database would); only the
    // FormatReader is rebuilt per call, closed over that test's fixed record list, since a real
    // FormatReader streams from bytes this test has no interest in producing.
    private static LoadWatchlistVersion withRecords(TestHarness h, List<RawRecord> records) {
        return withRecords(h, records, ADAPTER);
    }

    private static LoadWatchlistVersion withRecords(
            TestHarness h, List<RawRecord> records, WatchlistSourceAdapter adapter) {
        AdapterRegistry adapterRegistry = new AdapterRegistry();
        adapterRegistry.register(adapter);
        FormatReader reader = new FormatReader() {
            @Override
            public String formatId() {
                return "test-format";
            }

            @Override
            public Iterator<RawRecord> open(InputStream stream) {
                return records.iterator();
            }
        };
        RecordAuditEvent recordAuditEvent =
                new RecordAuditEvent(h.auditEventPort(), h.idGenerator(), CLOCK);
        PromoteListVersion promoteListVersion = new PromoteListVersion(
                h.listVersionRepository(), new FakeIndexReconciliation(), recordAuditEvent);
        return new LoadWatchlistVersion(
                adapterRegistry, Map.of("test-format", reader), PIPELINE, h.listVersionRepository(),
                h.watchEntityRepository(), h.watchEntityVersionWriter(), h.activeEntityVersionLookup(),
                new ReconcileVersion(), promoteListVersion, recordAuditEvent, h.idGenerator(), CLOCK);
    }

    private static RawRecord record(long ordinal, String id, String name) {
        return new RawRecord(ordinal, Map.of("id", List.of(id), "name", List.of(name)), Map.of(), "ref-" + ordinal);
    }

    private static LoadWatchlistVersion.Command command(TestHarness h, String checksum) {
        return new LoadWatchlistVersion.Command(
                SOURCE_ID, InputStream.nullInputStream(), "fixture.xml", checksum, Optional.empty(), "svc-ingest");
    }

    @Test
    void firstLoadAddsEveryRecordAndPromotesImmediately() {
        TestHarness h = harness();
        LoadWatchlistVersion interactor = withRecords(h, List.of(
                record(1, "e-1", "Ahmed Yousef"),
                record(2, "e-2", "Sara Ali")));

        LoadWatchlistVersion.Result result = interactor.execute(command(h, "sha256:v1"));

        assertThat(result.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.PROMOTED);
        assertThat(result.reconciliationReport().added()).isEqualTo(2);
        assertThat(result.reconciliationReport().amended()).isZero();
        assertThat(result.reconciliationReport().unchanged()).isZero();
        assertThat(result.reconciliationReport().delisted()).isZero();
        assertThat(h.watchEntityRepository().saved()).hasSize(2);
        assertThat(h.watchEntityVersionWriter().written()).hasSize(2);
        assertThat(h.listVersionRepository().findById(result.listVersionId()).orElseThrow().status())
                .isEqualTo(LoadStatus.ACTIVE);
    }

    // "Done when": loading the same fixture twice produces no new versions the second time.
    @Test
    void reloadingTheIdenticalFileShortCircuitsWithoutTouchingWatchlistTables() {
        TestHarness h = harness();
        LoadWatchlistVersion first = withRecords(h, List.of(record(1, "e-1", "Ahmed Yousef")));
        first.execute(command(h, "sha256:v1"));

        LoadWatchlistVersion second = withRecords(h, List.of(record(1, "e-1", "Ahmed Yousef")));
        LoadWatchlistVersion.Result result = second.execute(command(h, "sha256:v1"));

        assertThat(result.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.ALREADY_LOADED);
        assertThat(h.watchEntityRepository().saved()).hasSize(1);
        assertThat(h.watchEntityVersionWriter().written()).hasSize(1);
    }

    // "Done when", the deeper form: even a new file (different checksum) whose record content
    // is unchanged must not create new WatchEntityVersion rows -- only the per-entity
    // contentHash comparison decides that, not the file-level checksum shortcut above.
    @Test
    void aNewFileWithUnchangedContentProducesNoNewEntityVersions() {
        TestHarness h = harness();
        LoadWatchlistVersion first = withRecords(h, List.of(
                record(1, "e-1", "Ahmed Yousef"),
                record(2, "e-2", "Sara Ali")));
        first.execute(command(h, "sha256:v1"));
        registerAsActive(h);

        LoadWatchlistVersion second = withRecords(h, List.of(
                record(1, "e-1", "Ahmed Yousef"),
                record(2, "e-2", "Sara Ali")));
        LoadWatchlistVersion.Result result = second.execute(command(h, "sha256:v2-same-content"));

        assertThat(result.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.PROMOTED);
        assertThat(result.reconciliationReport().added()).isZero();
        assertThat(result.reconciliationReport().amended()).isZero();
        assertThat(result.reconciliationReport().unchanged()).isEqualTo(2);
        assertThat(h.watchEntityVersionWriter().written()).hasSize(2); // only the first load's writes
    }

    // "Done when": a fixture with one record removed produces exactly one tombstone.
    @Test
    void anEntityMissingFromTheNewFileProducesExactlyOneTombstone() {
        TestHarness h = harness();
        List<RawRecord> tenRecords = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            tenRecords.add(record(i, "e-" + i, "Person " + i));
        }
        LoadWatchlistVersion first = withRecords(h, tenRecords);
        first.execute(command(h, "sha256:v1"));
        registerAsActive(h);

        List<RawRecord> nineRecords = tenRecords.subList(0, 9); // e-10 is gone
        LoadWatchlistVersion second = withRecords(h, nineRecords);
        LoadWatchlistVersion.Result result = second.execute(command(h, "sha256:v2-nine-records"));

        assertThat(result.reconciliationReport().delisted()).isEqualTo(1);
        assertThat(result.reconciliationReport().added()).isZero();
        assertThat(result.reconciliationReport().amended()).isZero();
        long tombstonesWritten = h.watchEntityVersionWriter().written().stream()
                .filter(a -> a.version().status() == horus.domain.watchentity.EntityStatus.DELISTED)
                .count();
        assertThat(tombstonesWritten).isEqualTo(1);
    }

    // I-1/I-6: a capability collapse must FAIL the load, not silently promote degraded data --
    // and the previous active version must be left serving.
    @Test
    void aRejectFractionAboveThresholdFailsTheLoadAndLeavesThePreviousVersionServing() {
        TestHarness h = harness();
        LoadWatchlistVersion first = withRecords(h, List.of(record(1, "e-1", "Ahmed Yousef")));
        first.execute(command(h, "sha256:v1"));
        registerAsActive(h);

        // An adapter that always throws simulates >5% of records failing VALIDATE/TRANSFORM.
        WatchlistSourceAdapter throwingAdapter = new WatchlistSourceAdapter() {
            @Override
            public String sourceId() {
                return SOURCE_ID;
            }

            @Override
            public String formatId() {
                return "test-format";
            }

            @Override
            public CanonicalRecord toCanonical(RawRecord r) {
                throw new IllegalStateException("simulated mapping failure");
            }

            @Override
            public CapabilityExpectation expectation() {
                return new CapabilityExpectation(SOURCE_ID, Map.of(), Map.of());
            }

            @Override
            public Set<CanonicalSlot> requiredSlots() {
                return Set.of();
            }
        };
        AdapterRegistry adapterRegistry = new AdapterRegistry();
        adapterRegistry.register(throwingAdapter);
        FormatReader reader = new FormatReader() {
            @Override
            public String formatId() {
                return "test-format";
            }

            @Override
            public Iterator<RawRecord> open(InputStream stream) {
                return List.of(record(1, "e-2", "New Person")).iterator();
            }
        };
        RecordAuditEvent recordAuditEvent = new RecordAuditEvent(h.auditEventPort(), h.idGenerator(), CLOCK);
        LoadWatchlistVersion broken = new LoadWatchlistVersion(
                adapterRegistry, Map.of("test-format", reader), PIPELINE, h.listVersionRepository(),
                h.watchEntityRepository(), h.watchEntityVersionWriter(), h.activeEntityVersionLookup(),
                new ReconcileVersion(),
                new PromoteListVersion(h.listVersionRepository(), new FakeIndexReconciliation(), recordAuditEvent),
                recordAuditEvent, h.idGenerator(), CLOCK);

        LoadWatchlistVersion.Result result = broken.execute(command(h, "sha256:broken"));

        assertThat(result.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.REJECTED);
        assertThat(result.failureReason()).isPresent();
        assertThat(h.listVersionRepository().findById(result.listVersionId()).orElseThrow().status())
                .isEqualTo(LoadStatus.FAILED);
        assertThat(h.listVersionRepository().findActive(SOURCE_ID)).isPresent();
        assertThat(h.listVersionRepository().findActive(SOURCE_ID).orElseThrow().sourceFileChecksum())
                .isEqualTo("sha256:v1");
    }

    // I-1, I-2: a version row that never reached the store is an entity that will never be
    // screened -- a recall hole no benchmark would show, because the entity is simply not there.
    // The loader's own tally cannot see it, so the count is read back from the store and compared.
    @Test
    void aVersionRowMissingFromTheStoreFailsTheLoadInsteadOfPromotingIt() {
        TestHarness h = harness();
        h.watchEntityVersionWriter().loseWrites(1); // one write silently does not land
        LoadWatchlistVersion interactor = withRecords(h, List.of(
                record(1, "e-1", "Ahmed Yousef"),
                record(2, "e-2", "Sara Ali")));

        LoadWatchlistVersion.Result result = interactor.execute(command(h, "sha256:v1"));

        assertThat(result.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.REJECTED);
        assertThat(result.failureReason()).contains("stored version rows (1) do not match the 2 this load wrote");
        assertThat(h.listVersionRepository().findById(result.listVersionId()).orElseThrow().status())
                .isEqualTo(LoadStatus.FAILED);
        assertThat(h.listVersionRepository().findActive(SOURCE_ID)).isEmpty();
        assertThat(h.auditEventPort().recorded())
                .anySatisfy(e -> assertThat(e.eventType()).isEqualTo("LIST_VERSION_LOAD_REJECTED"));
    }

    // D12 H-9, the defect that has now cost two real runs. A load that fails leaves its
    // watch_entity identity rows committed, and current_watch_entity_version excludes FAILED and
    // STAGED loads -- so on the retry the entity is absent from the active snapshot while its
    // identity row is already there. Not registering the first load as active is exactly that
    // state: rows written, versions invisible.
    @Test
    void aRetryAfterALoadThatNeverBecameActiveReusesTheIdentityInsteadOfCollidingWithIt() {
        TestHarness h = harness();
        List<RawRecord> records = List.of(
                record(1, "e-1", "Ahmed Yousef"),
                record(2, "e-2", "Sara Ali"));
        withRecords(h, records).execute(command(h, "sha256:attempt-1"));
        // deliberately NOT registerAsActive(h) -- the first attempt never got promoted
        List<EntityId> identitiesAfterFirstAttempt =
                h.watchEntityRepository().saved().stream().map(WatchEntity::entityId).toList();

        LoadWatchlistVersion.Result retry =
                withRecords(h, records).execute(command(h, "sha256:attempt-2"));

        assertThat(retry.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.PROMOTED);
        assertThat(retry.reconciliationReport().rejected()).isEmpty();
        assertThat(retry.reconciliationReport().added()).isEqualTo(2);
        // One identity per source key, no matter how many attempts it took.
        assertThat(h.watchEntityRepository().saved()).hasSize(2);
        // And the retry's versions hang off the identities that were already there, so the failed
        // attempt's rows and the retry's belong to the same entities (I-6).
        assertThat(h.watchEntityVersionWriter().written().stream()
                        .skip(2)
                        .map(a -> a.version().entityId())
                        .toList())
                .containsExactlyElementsOf(identitiesAfterFirstAttempt);
    }

    // I-1: a load whose every record fails is abandoned unread rather than streamed to the end.
    // The reject gate would reach the same verdict, but only after the whole file -- hours, on the
    // real feed, to learn something that was true at record 0.
    @Test
    void aLoadWhoseFirstThousandRecordsAllFailIsAbandonedWithoutReadingTheRest() {
        TestHarness h = harness();
        List<RawRecord> manyRecords = java.util.stream.IntStream.rangeClosed(1, 5_000)
                .mapToObj(i -> record(i, "e-" + i, "Name " + i))
                .toList();
        LoadWatchlistVersion interactor = withRecords(h, manyRecords, alwaysFailsToMap());

        LoadWatchlistVersion.Result result = interactor.execute(command(h, "sha256:all-bad"));

        assertThat(result.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.REJECTED);
        // Exactly the budget, not all 5,000: it stopped reading.
        assertThat(result.reconciliationReport().rejected()).hasSize(1_000);
        assertThat(result.failureReason()).contains(
                "abandoned after 1000 records, none of which succeeded; most common reason MAPPING_FAILED");
        assertThat(h.listVersionRepository().findById(result.listVersionId()).orElseThrow().status())
                .isEqualTo(LoadStatus.FAILED);
        assertThat(h.listVersionRepository().findActive(SOURCE_ID)).isEmpty();
    }

    // One success inside the budget is enough to keep reading: the guard is for a systemically
    // broken load, not for a file with bad records at the front.
    @Test
    void aSingleSuccessWithinTheBudgetLetsTheLoadRunToTheEnd() {
        TestHarness h = harness();
        List<RawRecord> records = new java.util.ArrayList<>(
                java.util.stream.IntStream.rangeClosed(1, 1_500)
                        .mapToObj(i -> record(i, "e-" + i, "Name " + i))
                        .toList());
        LoadWatchlistVersion interactor = withRecords(h, records, failsToMapUnless("e-500"));

        LoadWatchlistVersion.Result result = interactor.execute(command(h, "sha256:one-good"));

        // Read all 1,500 -- so it failed on the reject fraction, not on the consecutive-failure
        // budget, and the report covers the whole file.
        assertThat(result.reconciliationReport().rejected()).hasSize(1_499);
        assertThat(result.reconciliationReport().added()).isEqualTo(1);
        assertThat(result.failureReason()).contains("more than 5% of records failed validation");
    }

    // Writes are chunked (CHUNK_SIZE = 1,000) to cut commits and round-trips by three orders of
    // magnitude, so the counts must survive a file that is not a whole number of chunks: two full
    // chunks and a 500-record tail. A dropped tail was the obvious way to get this wrong.
    @Test
    void everyRecordIsWrittenWhenTheFileIsNotAWholeNumberOfChunks() {
        TestHarness h = harness();
        List<RawRecord> records = java.util.stream.IntStream.rangeClosed(1, 2_500)
                .mapToObj(i -> record(i, "e-" + i, "Name " + i))
                .toList();

        LoadWatchlistVersion.Result result = withRecords(h, records).execute(command(h, "sha256:2500"));

        assertThat(result.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.PROMOTED);
        assertThat(result.reconciliationReport().added()).isEqualTo(2_500);
        assertThat(result.reconciliationReport().rejected()).isEmpty();
        assertThat(h.watchEntityVersionWriter().written()).hasSize(2_500);
        assertThat(h.watchEntityRepository().saved()).hasSize(2_500);
    }

    // I-1. A chunk write is all-or-nothing, so one bad row rolls back a thousand good records. The
    // loader replays the chunk one record at a time: the culprit is quarantined by ordinal and
    // everything else is written. Without the replay, chunking would have traded a 158x speed-up for
    // losing 999 entities per bad row -- a silent recall hole, which is the trade this system may
    // never make (I-2).
    @Test
    void aChunkThatFailsIsReplayedSoOnlyTheOffendingRecordIsQuarantined() {
        TestHarness h = harness();
        List<RawRecord> records = java.util.stream.IntStream.rangeClosed(1, 1_200)
                .mapToObj(i -> record(i, "e-" + i, "Name " + i))
                .toList();
        // Fails any chunk containing e-742, and on the replay only e-742's own single-record chunk.
        h.watchEntityVersionWriter().failOnAggregate(a -> a.version().primaryName().equals("Name 742"));

        LoadWatchlistVersion.Result result = withRecords(h, records).execute(command(h, "sha256:one-bad"));

        assertThat(result.reconciliationReport().rejected())
                .extracting(ReconciliationReport.RejectedRecord::sourceRecordId)
                .containsExactly("742");
        assertThat(result.reconciliationReport().added()).isEqualTo(1_199);
        assertThat(h.watchEntityVersionWriter().written()).hasSize(1_199);
        // Under 5% rejected, and the version count agrees with the tally, so it still promotes.
        assertThat(result.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.PROMOTED);
    }

    // The replay must not lose the records in the SAME chunk that were fine. This is the assertion
    // that would fail if the failed chunk were simply written off.
    @Test
    void theRecordsSharingAFailedChunkAreStillWritten() {
        TestHarness h = harness();
        List<RawRecord> records = java.util.stream.IntStream.rangeClosed(1, 1_000)
                .mapToObj(i -> record(i, "e-" + i, "Name " + i))
                .toList();
        h.watchEntityVersionWriter().failOnAggregate(a -> a.version().primaryName().equals("Name 1"));

        LoadWatchlistVersion.Result result = withRecords(h, records).execute(command(h, "sha256:first-bad"));

        assertThat(result.reconciliationReport().added()).isEqualTo(999);
        assertThat(h.watchEntityVersionWriter().written().stream()
                        .map(a -> a.version().primaryName())
                        .toList())
                .doesNotContain("Name 1")
                .contains("Name 2", "Name 1000");
    }

    // A capability deviation means the file is not the shape the adapter expects. The rows are
    // written and consistent -- what is in doubt is a judgement no algorithm should make. It used to
    // mark the load FAILED, and nothing moves a FAILED version back to STAGED, so a deviation threw
    // the whole load away: on the real feed, hours of work over a figure a person must rule on
    // anyway. It now HOLDS, which is still closed by default -- STAGED does not screen.
    @Test
    void aCapabilityDeviationHoldsTheLoadForAnOperatorInsteadOfDiscardingIt() {
        TestHarness h = harness();
        LoadWatchlistVersion interactor = withRecords(h, List.of(
                record(1, "e-1", "Ahmed Yousef"),
                record(2, "e-2", "Sara Ali")), expectingDobOnEveryRecord());

        LoadWatchlistVersion.Result result = interactor.execute(command(h, "sha256:deviating"));

        assertThat(result.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.AWAITING_CONFIRMATION);
        assertThat(result.failureReason()).isPresent();
        assertThat(result.failureReason().orElseThrow()).contains("DATE_OF_BIRTH");
        // The work survives: rows written, version STAGED, nothing serving it yet.
        assertThat(result.reconciliationReport().added()).isEqualTo(2);
        assertThat(h.watchEntityVersionWriter().written()).hasSize(2);
        assertThat(h.listVersionRepository().findById(result.listVersionId()).orElseThrow().status())
                .isEqualTo(LoadStatus.STAGED);
        assertThat(h.listVersionRepository().findActive(SOURCE_ID)).isEmpty();
        assertThat(h.auditEventPort().recorded())
                .anySatisfy(e -> assertThat(e.eventType())
                        .isEqualTo("CAPABILITY_DEVIATION_AWAITING_CONFIRMATION"));
    }

    // The point of holding rather than failing: the load can then be promoted without being re-run.
    @Test
    void anOperatorCanPromoteAHeldLoadWithoutReRunningIt() {
        TestHarness h = harness();
        LoadWatchlistVersion.Result held = withRecords(h, List.of(
                record(1, "e-1", "Ahmed Yousef")), expectingDobOnEveryRecord())
                .execute(command(h, "sha256:deviating"));
        assertThat(held.outcome()).isEqualTo(LoadWatchlistVersion.Outcome.AWAITING_CONFIRMATION);

        RecordAuditEvent recordAuditEvent = new RecordAuditEvent(h.auditEventPort(), h.idGenerator(), CLOCK);
        new ConfirmStagedVersion(
                h.listVersionRepository(),
                new PromoteListVersion(h.listVersionRepository(), new FakeIndexReconciliation(), recordAuditEvent),
                recordAuditEvent)
                .execute(new ConfirmStagedVersion.Command(
                        held.listVersionId(), ConfirmStagedVersion.Decision.CONFIRM,
                        ConfirmStagedVersion.AwaitingReason.CAPABILITY_DEVIATION,
                        "head-of-file slice, coverage figures are whole-file properties", "operator-1"));

        assertThat(h.listVersionRepository().findById(held.listVersionId()).orElseThrow().status())
                .isEqualTo(LoadStatus.ACTIVE);
        assertThat(h.auditEventPort().recorded())
                .anySatisfy(e -> assertThat(e.eventType()).isEqualTo("STAGED_VERSION_CONFIRMED"));
    }

    // Registers whatever the last load wrote as the "currently active" snapshot a real database
    // would reflect after promotion -- the fakes are independent, so tests wire this by hand.
    private static void registerAsActive(TestHarness h) {
        for (WatchEntityVersionAggregate aggregate : h.watchEntityVersionWriter().written()) {
            String sourceEntityId = h.watchEntityRepository().saved().stream()
                    .filter(e -> e.entityId().equals(aggregate.version().entityId()))
                    .findFirst()
                    .map(horus.domain.watchentity.WatchEntity::sourceEntityId)
                    .orElseThrow();
            h.activeEntityVersionLookup().putActive(sourceEntityId, aggregate.version());
        }
    }
}
