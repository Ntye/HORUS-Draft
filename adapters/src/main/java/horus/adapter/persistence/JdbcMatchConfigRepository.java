package horus.adapter.persistence;

import horus.application.port.MatchConfigRepository;
import horus.application.port.VersionedMatchConfig;
import horus.matching.MatchConfig;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// Segregation of duties (§11): reads go through the horus_screen credential, the role that screens
// and can only read config; the insert goes through horus_ingest, which V7 grants INSERT+SELECT on
// config_version and nothing more. Neither role can update or delete a config row (I-7).
@Component
public final class JdbcMatchConfigRepository implements MatchConfigRepository {

    private final JdbcTemplate readTemplate;
    private final JdbcTemplate writeTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMatchConfigRepository(
            @Qualifier("screenJdbcTemplate") JdbcTemplate readTemplate,
            @Qualifier("ingestJdbcTemplate") JdbcTemplate writeTemplate,
            ObjectMapper objectMapper) {
        this.readTemplate = readTemplate;
        this.writeTemplate = writeTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<VersionedMatchConfig> findCurrent(String profileId, Instant asOf) {
        // Only APPROVED, already-effective versions are ever returned; the newest wins, with the
        // id as a tie-break so the choice is deterministic (I-4).
        return readTemplate.query(
                        "SELECT config_version_id, profile_id, payload::text AS payload FROM config_version "
                                + "WHERE profile_id = ? AND approved_by IS NOT NULL AND effective_from IS NOT NULL "
                                + "AND effective_from <= ? ORDER BY effective_from DESC, config_version_id DESC LIMIT 1",
                        (rs, rowNum) -> new VersionedMatchConfig(
                                (UUID) rs.getObject("config_version_id"), rs.getString("profile_id"),
                                parse(rs.getString("payload"))),
                        profileId, Timestamp.from(asOf))
                .stream().findFirst();
    }

    @Override
    public void save(VersionedMatchConfig config, String createdBy, Instant createdAt, String approvedBy,
            Instant effectiveFrom) {
        writeTemplate.update(
                "INSERT INTO config_version (config_version_id, profile_id, payload, created_by, created_at, "
                        + "approved_by, effective_from) VALUES (?, ?, ?::jsonb, ?, ?, ?, ?)",
                config.configVersionId(), config.profileId(), write(config.config()), createdBy,
                Timestamp.from(createdAt), approvedBy, Timestamp.from(effectiveFrom));
    }

    private MatchConfig parse(String json) {
        try {
            return objectMapper.readValue(json, MatchConfig.class);
        } catch (JacksonException e) {
            // Never echo the payload; it is configuration, but the habit keeps error text clean (I-11).
            throw new IllegalStateException("stored config_version.payload is not a valid MatchConfig");
        }
    }

    private String write(MatchConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JacksonException e) {
            throw new IllegalStateException("MatchConfig could not be serialised");
        }
    }
}
