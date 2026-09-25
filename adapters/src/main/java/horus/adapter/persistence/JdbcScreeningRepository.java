package horus.adapter.persistence;

import horus.application.port.ScreeningRecord;
import horus.application.port.ScreeningRepository;
import horus.domain.screening.ScreeningCandidate;
import horus.domain.screening.ScreeningRequest;
import horus.domain.screening.ScreeningSubject;
import horus.domain.shared.DecisionBand;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.ListVersionId;
import horus.domain.shared.QueryName;
import horus.domain.shared.ScreeningCandidateId;
import horus.domain.shared.ScreeningId;
import horus.domain.shared.ScreeningSubjectId;
import java.sql.Array;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// Append-only evidence on the horus_screen credential (V7 grants INSERT+SELECT only).
//
// The transaction is bound to THIS repository's own DataSource explicitly. A plain @Transactional
// would use Spring Boot's auto-configured manager, which is bound to the primary (admin) DataSource
// and would leave these statements running outside any transaction on the screen connection --
// so a half-written screening could survive a failure.
@Component
public final class JdbcScreeningRepository implements ScreeningRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public JdbcScreeningRepository(@Qualifier("screenJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
    }

    @Override
    public void save(ScreeningRecord record) {
        transactionTemplate.executeWithoutResult(status -> {
            insertRequest(record.request());
            int ordinal = 1;
            for (ScreeningRecord.SubjectRecord subject : record.subjects()) {
                insertSubject(subject.subject(), ordinal++);
                for (ScreeningCandidate candidate : subject.candidates()) {
                    insertCandidate(candidate);
                }
            }
        });
    }

    @Override
    public Optional<ScreeningRecord> findById(ScreeningId screeningId) {
        Optional<ScreeningRequest> request = jdbcTemplate.query(
                        "SELECT * FROM screening_request WHERE screening_id = ?",
                        (rs, rowNum) -> {
                            Object[] ids = (Object[]) rs.getArray("list_version_ids").getArray();
                            Set<ListVersionId> listVersionIds = new java.util.HashSet<>();
                            for (Object id : ids) {
                                listVersionIds.add(new ListVersionId((UUID) id));
                            }
                            return new ScreeningRequest(
                                    new ScreeningId((UUID) rs.getObject("screening_id")),
                                    rs.getString("consumer_system"),
                                    Optional.ofNullable(rs.getString("consumer_reference")),
                                    rs.getString("profile_id"), rs.getString("requested_by"),
                                    rs.getTimestamp("requested_at").toInstant(), listVersionIds,
                                    (UUID) rs.getObject("config_version_id"), rs.getString("pipeline_version"),
                                    DecisionBand.valueOf(rs.getString("outcome")),
                                    Optional.ofNullable(rs.getString("failure_reason")),
                                    rs.getString("idempotency_key"));
                        }, screeningId.value())
                .stream().findFirst();
        if (request.isEmpty()) {
            return Optional.empty();
        }

        List<ScreeningRecord.SubjectRecord> subjects = new ArrayList<>();
        List<ScreeningSubject> subjectRows = jdbcTemplate.query(
                "SELECT * FROM screening_subject WHERE screening_id = ? ORDER BY ordinal",
                (rs, rowNum) -> new ScreeningSubject(
                        new ScreeningSubjectId((UUID) rs.getObject("subject_id")), screeningId,
                        Optional.ofNullable(rs.getString("subject_reference")),
                        new QueryName(rs.getString("query_name")), rs.getInt("candidate_count"),
                        rs.getInt("top_score"), DecisionBand.valueOf(rs.getString("outcome"))),
                screeningId.value());
        for (ScreeningSubject subject : subjectRows) {
            List<ScreeningCandidate> candidates = jdbcTemplate.query(
                    "SELECT candidate_id, source_id, entity_id, entity_version_id, composite_score, "
                            + "explanation_json::text AS explanation_json, explanation_schema_version, "
                            + "decision_band, rank FROM screening_candidate WHERE subject_id = ? ORDER BY rank",
                    (rs, rowNum) -> new ScreeningCandidate(
                            new ScreeningCandidateId((UUID) rs.getObject("candidate_id")), subject.subjectId(),
                            rs.getString("source_id"), new EntityId((UUID) rs.getObject("entity_id")),
                            new EntityVersionId((UUID) rs.getObject("entity_version_id")),
                            rs.getInt("composite_score"), rs.getString("explanation_json"),
                            rs.getString("explanation_schema_version"),
                            DecisionBand.valueOf(rs.getString("decision_band")), rs.getInt("rank")),
                    subject.subjectId().value());
            subjects.add(new ScreeningRecord.SubjectRecord(subject, candidates));
        }
        return Optional.of(new ScreeningRecord(request.get(), subjects));
    }

    @Override
    public Optional<ScreeningId> findIdByIdempotencyKey(String idempotencyKey) {
        return jdbcTemplate.query("SELECT screening_id FROM screening_request WHERE idempotency_key = ?",
                        (rs, rowNum) -> new ScreeningId((UUID) rs.getObject("screening_id")), idempotencyKey)
                .stream().findFirst();
    }

    private void insertRequest(ScreeningRequest request) {
        UUID[] listVersionIds = request.listVersionIds().stream().map(ListVersionId::value).sorted()
                .collect(Collectors.toList()).toArray(new UUID[0]);
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement(
                    "INSERT INTO screening_request (screening_id, consumer_system, consumer_reference, profile_id, "
                            + "requested_by, requested_at, list_version_ids, config_version_id, pipeline_version, "
                            + "outcome, failure_reason, idempotency_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setObject(1, request.screeningId().value());
            ps.setString(2, request.consumerSystem());
            ps.setString(3, request.consumerReference().orElse(null));
            ps.setString(4, request.profileId());
            ps.setString(5, request.requestedBy());
            ps.setTimestamp(6, Timestamp.from(request.requestedAt()));
            Array array = con.createArrayOf("uuid", listVersionIds);
            ps.setArray(7, array);
            ps.setObject(8, request.configVersionId());
            ps.setString(9, request.pipelineVersion());
            ps.setString(10, request.outcome().name());
            ps.setString(11, request.failureReason().orElse(null));
            ps.setString(12, request.idempotencyKey());
            return ps;
        });
    }

    private void insertSubject(ScreeningSubject subject, int ordinal) {
        jdbcTemplate.update(
                "INSERT INTO screening_subject (subject_id, screening_id, subject_reference, query_name, "
                        + "candidate_count, top_score, outcome, ordinal) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                subject.subjectId().value(), subject.screeningId().value(), subject.subjectReference().orElse(null),
                subject.queryName().value(), subject.candidateCount(), subject.topScore(), subject.outcome().name(),
                ordinal);
    }

    private void insertCandidate(ScreeningCandidate candidate) {
        jdbcTemplate.update(
                "INSERT INTO screening_candidate (candidate_id, subject_id, source_id, entity_id, entity_version_id, "
                        + "composite_score, explanation_json, explanation_schema_version, decision_band, rank) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?::json, ?, ?, ?)",
                candidate.candidateId().value(), candidate.subjectId().value(), candidate.sourceId(),
                candidate.entityId().value(), candidate.entityVersionId().value(), candidate.compositeScore(),
                candidate.explanationJson(), candidate.explanationSchemaVersion(), candidate.decisionBand().name(),
                candidate.rank());
    }
}
