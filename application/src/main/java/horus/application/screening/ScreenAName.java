package horus.application.screening;

import horus.application.ingestion.RecordAuditEvent;
import horus.application.port.ActiveListVersion;
import horus.application.port.CandidateViewAssembler;
import horus.application.port.IdGenerator;
import horus.application.port.ScreeningRecord;
import horus.application.port.ScreeningRepository;
import horus.application.port.WhitelistViewProvider;
import horus.blocking.CandidateGenerator;
import horus.blocking.CandidateKey;
import horus.blocking.CandidateSet;
import horus.blocking.Completeness;
import horus.domain.audit.ActorKind;
import horus.domain.screening.ScreeningCandidate;
import horus.domain.screening.ScreeningRequest;
import horus.domain.screening.ScreeningSubject;
import horus.domain.shared.DecisionBand;
import horus.domain.shared.EntityType;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.ListVersionId;
import horus.domain.shared.QueryName;
import horus.domain.shared.ScreeningCandidateId;
import horus.domain.shared.ScreeningId;
import horus.domain.shared.ScreeningSubjectId;
import horus.matching.CandidateView;
import horus.matching.MatchConfig;
import horus.matching.MatchExplanation;
import horus.matching.MatchQuery;
import horus.matching.MatchingEngine;
import horus.matching.PartialDate;
import horus.normalisation.NormalisationPipeline;
import horus.normalisation.NormalisedName;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// The end-to-end use case (spec §16, CLAUDE.md §5): QueryName -> normalise -> block -> assemble
// CandidateViews -> score -> band -> record everything -> audit.
//
// I-1 fail closed: a PARTIAL candidate set, an index failure, an unassemblable candidate or a
//   scoring failure makes that subject ERROR; ERROR dominates across subjects. A TRUNCATED search
//   that found nothing cannot assert a clear, so it is ERROR too. Nothing here catches and
//   returns an empty result.
// I-3 the WHOLE explanation is stored as JSON; reading a screening back never recomputes it.
// I-8 every candidate the engine scored is recorded, however low.
// I-11 nothing logged, audited or thrown from here contains a name -- ids and counts only.
// I-12 the request carries listVersionIds, configVersionId and pipelineVersion.
public final class ScreenAName implements Screener {

    public static final int MAX_SUBJECTS = 100;

    public record SubjectInput(
            Optional<String> reference,
            String name,
            Optional<EntityType> entityType,
            Optional<PartialDate> dob,
            Set<String> countries,
            Set<String> identifiers) {

        public SubjectInput {
            if (reference == null || name == null || entityType == null || dob == null || countries == null
                    || identifiers == null) {
                throw new IllegalArgumentException("no SubjectInput component may be null");
            }
        }
    }

    public record Command(
            String consumerSystem,
            Optional<String> consumerReference,
            String profileId,
            String requestedBy,
            ActorKind actorKind,
            String idempotencyKey,
            List<SubjectInput> subjects) {

        public Command {
            if (consumerSystem == null || consumerSystem.isBlank() || profileId == null || profileId.isBlank()
                    || requestedBy == null || requestedBy.isBlank() || idempotencyKey == null
                    || idempotencyKey.isBlank() || consumerReference == null || actorKind == null) {
                throw new IllegalArgumentException(
                        "consumerSystem, profileId, requestedBy, idempotencyKey and actorKind are required");
            }
            if (subjects == null || subjects.isEmpty() || subjects.size() > MAX_SUBJECTS) {
                throw new IllegalArgumentException("between 1 and " + MAX_SUBJECTS + " subjects are required");
            }
            subjects = List.copyOf(subjects);
        }
    }

    public record ScoredCandidate(ScreeningCandidate candidate, Optional<MatchExplanation> explanation) {
    }

    public record SubjectResult(ScreeningSubject subject, List<ScoredCandidate> candidates, Optional<String> failure) {
    }

    public record Result(ScreeningRequest request, List<SubjectResult> subjects, boolean replayed) {

        public DecisionBand outcome() {
            return request.outcome();
        }
    }

    private final NormalisationPipeline pipeline;
    private final CandidateGenerator candidateGenerator;
    private final CandidateViewAssembler assembler;
    private final MatchingEngine engine;
    private final LoadScreeningConfig loadScreeningConfig;
    private final WhitelistViewProvider whitelistViewProvider;
    private final ScreeningRepository screeningRepository;
    private final RecordAuditEvent recordAuditEvent;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public ScreenAName(
            NormalisationPipeline pipeline,
            CandidateGenerator candidateGenerator,
            CandidateViewAssembler assembler,
            MatchingEngine engine,
            LoadScreeningConfig loadScreeningConfig,
            WhitelistViewProvider whitelistViewProvider,
            ScreeningRepository screeningRepository,
            RecordAuditEvent recordAuditEvent,
            IdGenerator idGenerator,
            Clock clock) {
        this.pipeline = pipeline;
        this.candidateGenerator = candidateGenerator;
        this.assembler = assembler;
        this.engine = engine;
        this.loadScreeningConfig = loadScreeningConfig;
        this.whitelistViewProvider = whitelistViewProvider;
        this.screeningRepository = screeningRepository;
        this.recordAuditEvent = recordAuditEvent;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    public Result execute(Command command) {
        // Input bounds first (I-11 / §10): an over-length or blank name is rejected before
        // anything is recorded. QueryName's own message never echoes the value.
        List<QueryName> queryNames = command.subjects().stream().map(s -> new QueryName(s.name())).toList();

        Optional<ScreeningId> existing = screeningRepository.findIdByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get());
        }

        LoadScreeningConfig.Loaded loaded;
        try {
            loaded = loadScreeningConfig.execute(command.profileId(), pipeline.pipelineVersion());
        } catch (RuntimeException e) {
            recordUnavailable(command, e);
            throw e;
        }

        MatchConfig config = loaded.config().config();
        ScreeningId screeningId = new ScreeningId(idGenerator.newId());
        List<SubjectResult> results = new ArrayList<>();
        List<ScreeningRecord.SubjectRecord> records = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        DecisionBand overall = DecisionBand.NO_MATCH;

        for (int i = 0; i < command.subjects().size(); i++) {
            SubjectResult result = screenSubject(screeningId, command.subjects().get(i), queryNames.get(i),
                    loaded, config);
            results.add(result);
            records.add(new ScreeningRecord.SubjectRecord(result.subject(),
                    result.candidates().stream().map(ScoredCandidate::candidate).toList()));
            final int position = i + 1;
            result.failure().ifPresent(reason -> failures.add("subject " + position + ": " + reason));
            overall = dominant(overall, result.subject().outcome());
        }

        Set<ListVersionId> listVersionIds = loaded.activeListVersions().stream()
                .map(ActiveListVersion::listVersionId).collect(Collectors.toSet());
        ScreeningRequest request = new ScreeningRequest(
                screeningId, command.consumerSystem(), command.consumerReference(), command.profileId(),
                command.requestedBy(), clock.instant(), listVersionIds, loaded.config().configVersionId(),
                pipeline.pipelineVersion(), overall,
                failures.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", failures)),
                command.idempotencyKey());

        // Evidence first: if it cannot be stored, no result is returned at all.
        screeningRepository.save(new ScreeningRecord(request, records));
        recordCompleted(command, request, results);
        return new Result(request, results, false);
    }

    private SubjectResult screenSubject(
            ScreeningId screeningId,
            SubjectInput input,
            QueryName queryName,
            LoadScreeningConfig.Loaded loaded,
            MatchConfig config) {
        ScreeningSubjectId subjectId = new ScreeningSubjectId(idGenerator.newId());
        NormalisedName normalised = pipeline.normalise(queryName.value());
        MatchQuery query = new MatchQuery(normalised, input.entityType(), input.dob(), input.countries(),
                input.identifiers());

        CandidateSet set;
        try {
            set = candidateGenerator.generate(normalised);
        } catch (RuntimeException e) {
            return errored(subjectId, screeningId, input, queryName, List.of(), "candidate generation failed");
        }
        if (set.completeness() == Completeness.PARTIAL) {
            // I-1: an incomplete search must never read as "nothing found".
            return errored(subjectId, screeningId, input, queryName, List.of(),
                    "candidate generation was incomplete (PARTIAL)");
        }

        List<ScoredCandidate> scored;
        try {
            scored = scoreAll(subjectId, query, set, config);
        } catch (MissingCandidateException e) {
            return errored(subjectId, screeningId, input, queryName, List.of(), "a candidate record was unavailable");
        } catch (RuntimeException e) {
            return errored(subjectId, screeningId, input, queryName, List.of(),
                    "scoring failed (" + e.getClass().getSimpleName() + ")");
        }

        DecisionBand outcome = scored.isEmpty() ? DecisionBand.NO_MATCH : scored.get(0).candidate().decisionBand();
        Optional<String> failure = Optional.empty();
        if (outcome == DecisionBand.NO_MATCH && set.completeness() == Completeness.TRUNCATED) {
            // The candidate list was cut by a cap, so "nothing matched" cannot be asserted.
            outcome = DecisionBand.ERROR;
            failure = Optional.of("candidate list was truncated and no match was found; cannot assert a clear");
        }
        int topScore = scored.isEmpty() ? 0 : scored.get(0).candidate().compositeScore();
        ScreeningSubject subject = new ScreeningSubject(subjectId, screeningId, input.reference(), queryName,
                scored.size(), topScore, outcome);
        return new SubjectResult(subject, scored, failure);
    }

    private List<ScoredCandidate> scoreAll(ScreeningSubjectId subjectId, MatchQuery query, CandidateSet set,
            MatchConfig config) {
        List<CandidateKey> keys = set.candidates();
        if (keys.isEmpty()) {
            return List.of();
        }
        Map<EntityVersionId, CandidateView> views = assembler.assemble(keys);
        var whitelist = whitelistViewProvider.forQuery(query.name());

        List<MatchExplanation> explanations = new ArrayList<>();
        Map<EntityVersionId, CandidateView> byVersion = new LinkedHashMap<>();
        for (CandidateKey key : keys) {
            CandidateView view = views.get(key.entityVersionId());
            if (view == null) {
                throw new MissingCandidateException();
            }
            byVersion.put(key.entityVersionId(), view);
            explanations.add(engine.score(query, view, config, whitelist));
        }
        // I-4: score descending, then version id ascending -- never an arbitrary tie order.
        explanations.sort(Comparator
                .comparingInt(MatchExplanation::compositeScore).reversed()
                .thenComparing(e -> e.candidateEntityVersionId().value()));

        List<ScoredCandidate> scored = new ArrayList<>();
        for (int rank = 1; rank <= explanations.size(); rank++) {
            MatchExplanation e = explanations.get(rank - 1);
            ScreeningCandidate candidate = new ScreeningCandidate(
                    new ScreeningCandidateId(idGenerator.newId()), subjectId, e.candidateSourceId(),
                    e.candidateEntityId(), e.candidateEntityVersionId(), e.compositeScore(), e.toCanonicalJson(),
                    e.schemaVersion(), e.band(), rank);
            scored.add(new ScoredCandidate(candidate, Optional.of(e)));
        }
        return scored;
    }

    private SubjectResult errored(ScreeningSubjectId subjectId, ScreeningId screeningId, SubjectInput input,
            QueryName queryName, List<ScoredCandidate> candidates, String reason) {
        ScreeningSubject subject = new ScreeningSubject(subjectId, screeningId, input.reference(), queryName,
                candidates.size(), 0, DecisionBand.ERROR);
        return new SubjectResult(subject, candidates, Optional.of(reason));
    }

    // DecisionBand is declared in severity order: NO_MATCH < POSSIBLE_MATCH < STRONG_MATCH < ERROR.
    private static DecisionBand dominant(DecisionBand a, DecisionBand b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private Result replay(ScreeningId screeningId) {
        ScreeningRecord stored = screeningRepository.findById(screeningId).orElseThrow(
                () -> new IllegalStateException("idempotency key maps to a screening that cannot be loaded"));
        List<SubjectResult> subjects = stored.subjects().stream()
                .map(s -> new SubjectResult(s.subject(),
                        s.candidates().stream().map(c -> new ScoredCandidate(c, Optional.empty())).toList(),
                        Optional.empty()))
                .toList();
        return new Result(stored.request(), subjects, true);
    }

    private void recordCompleted(Command command, ScreeningRequest request, List<SubjectResult> results) {
        int candidates = results.stream().mapToInt(r -> r.candidates().size()).sum();
        recordAuditEvent.execute(new RecordAuditEvent.Command(
                "SCREENING_COMPLETED", command.requestedBy(), command.actorKind(), "Screening",
                request.screeningId().value().toString(),
                Map.of("outcome", request.outcome().name(),
                        "subjects", String.valueOf(results.size()),
                        "candidates", String.valueOf(candidates),
                        "configVersionId", request.configVersionId().toString(),
                        "pipelineVersion", request.pipelineVersion(),
                        "listVersionIds", request.listVersionIds().stream()
                                .map(id -> id.value().toString()).collect(Collectors.joining(","))),
                Optional.of(command.idempotencyKey())));
    }

    private void recordUnavailable(Command command, RuntimeException cause) {
        recordAuditEvent.execute(new RecordAuditEvent.Command(
                "SCREENING_UNAVAILABLE", command.requestedBy(), command.actorKind(), "Screening",
                command.idempotencyKey(),
                Map.of("reason", cause.getClass().getSimpleName(), "subjects", String.valueOf(command.subjects().size())),
                Optional.of(command.idempotencyKey())));
    }

    private static final class MissingCandidateException extends RuntimeException {
        MissingCandidateException() {
            super("candidate view unavailable");
        }
    }
}
