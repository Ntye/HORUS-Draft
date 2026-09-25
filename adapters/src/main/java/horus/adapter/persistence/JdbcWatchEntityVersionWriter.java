package horus.adapter.persistence;

import horus.application.port.WatchEntityVersionAggregate;
import horus.application.port.WatchEntityVersionWriter;
import horus.domain.shared.ListVersionId;
import horus.domain.shared.Provenance;
import horus.domain.watchentity.WatchAddress;
import horus.domain.watchentity.WatchCountry;
import horus.domain.watchentity.WatchDesignation;
import horus.domain.watchentity.WatchDob;
import horus.domain.watchentity.WatchEntityVersion;
import horus.domain.watchentity.WatchExternalSource;
import horus.domain.watchentity.WatchIdentifier;
import horus.domain.watchentity.WatchLink;
import horus.domain.watchentity.WatchName;
import horus.domain.watchentity.WatchOwnership;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

// A CHUNK of entities is written in one transaction (CLAUDE.md section 5 for the slice an entity owns).
//
// WHAT CHANGED, and why, in two steps on 2026-09-25:
//
// 1. Child rows went from one jdbcTemplate.update per row to one batch per child table. Measured on
//    the real feed that moved 34 records/second to 60 -- only 1.76x, because a batch scoped to ONE
//    entity is usually a single row: an entity has one DOB, one or a handful of names. The prepares
//    went away; the round-trips and the commits did not.
//
// 2. So the unit became a chunk of entities. Measured on this database, with its bulk-load settings
//    and fsync/synchronous_commit both ON: 2,000 rows as 2,000 transactions took 5,609 ms; the same
//    rows in ONE transaction took 35.6 ms; as one multi-row statement, 21.0 ms. Commit frequency
//    alone was worth 158x, and a load doing one transaction per entity was paying it 5,990,394
//    times. Separately, Postgres inserts into this table shape with all six indexes at 17,500
//    rows/second -- the database was never the constraint.
//
// ATOMICITY IS STRONGER, NOT WEAKER. TransactionAtomicityIntegrationTest protects the property that
// a version row never exists without its children; a chunk transaction guarantees it for every
// aggregate in the chunk. What a chunk cannot do is say which record broke a constraint, so the
// caller retries a failed chunk one aggregate at a time. That cost is paid only on failure.
@Component
public final class JdbcWatchEntityVersionWriter implements WatchEntityVersionWriter {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public JdbcWatchEntityVersionWriter(@Qualifier("ingestJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        // Bound to the ingest DataSource explicitly. The former @Transactional used Spring Boot's
        // auto-configured manager, which is bound to the PRIMARY (admin) DataSource, so it never
        // wrapped these statements -- and a final class cannot be proxied at all.
        this.transactionTemplate =
                new TransactionTemplate(new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
    }

    @Override
    public void write(List<WatchEntityVersionAggregate> aggregates) {
        if (aggregates.isEmpty()) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> writeChunk(aggregates));
        } catch (RuntimeException e) {
            // Named here, where the driver's error is still available, so the load report can say
            // WHICH constraint was broken instead of "PSQLException". The transaction has already
            // rolled back whole, so nothing partial survives.
            throw PersistenceFailures.translate(e);
        }
    }

    @Override
    public long countVersionsIn(ListVersionId listVersionId) {
        // Read outside any of the write transactions, so it reflects what is committed rather than
        // what a transaction hoped to commit. That is the whole point of the check (I-1).
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM watch_entity_version WHERE list_version_id = ?",
                Long.class, listVersionId.value());
        return count == null ? 0L : count;
    }

    // Rows are gathered across the whole chunk, one statement per table, in the order the
    // records appeared in the file -- so two runs over the same file send identical statements
    // (I-4). Versions go first: every child row references one.
    private void writeChunk(List<WatchEntityVersionAggregate> aggregates) {
        batch("INSERT INTO watch_entity_version (entity_version_id, entity_id, list_version_id, "
                        + "content_hash, entity_type, gender, primary_name, status, categories, "
                        + "list_sources, designated_at, delisted_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                map(aggregates, WatchEntityVersionAggregate::version),
                JdbcWatchEntityVersionWriter::bindVersion);

        batch("INSERT INTO watch_name (name_id, entity_version_id, name_type, raw_name, "
                        + "normalised_name, name_tokens, phonetic_codes, trigrams, script, language, "
                        + "provenance_rule_id, provenance_start, provenance_end) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                flatten(aggregates, WatchEntityVersionAggregate::names),
                JdbcWatchEntityVersionWriter::bindName);

        batch("INSERT INTO watch_dob (dob_id, entity_version_id, year, month, day, age, "
                        + "as_of_date, deceased, precision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                flatten(aggregates, WatchEntityVersionAggregate::dobs),
                JdbcWatchEntityVersionWriter::bindDob);

        batch("INSERT INTO watch_identifier (identifier_id, entity_version_id, id_type, id_value, "
                        + "normalised_id_value, issuing_country, provenance_rule_id, provenance_start, "
                        + "provenance_end) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                flatten(aggregates, WatchEntityVersionAggregate::identifiers),
                JdbcWatchEntityVersionWriter::bindIdentifier);

        batch("INSERT INTO watch_country (country_id, entity_version_id, raw_country, iso_code) "
                        + "VALUES (?, ?, ?, ?)",
                flatten(aggregates, WatchEntityVersionAggregate::countries),
                JdbcWatchEntityVersionWriter::bindCountry);

        batch("INSERT INTO watch_address (address_id, entity_version_id, country_code, city, state, "
                        + "raw_address) VALUES (?, ?, ?, ?, ?, ?)",
                flatten(aggregates, WatchEntityVersionAggregate::addresses),
                JdbcWatchEntityVersionWriter::bindAddress);

        batch("INSERT INTO watch_link (link_id, entity_version_id, target_entity_id, role, percent) "
                        + "VALUES (?, ?, ?, ?, ?)",
                flatten(aggregates, WatchEntityVersionAggregate::links),
                JdbcWatchEntityVersionWriter::bindLink);

        batch("INSERT INTO watch_external_source (external_source_id, entity_version_id, uri) "
                        + "VALUES (?, ?, ?)",
                flatten(aggregates, WatchEntityVersionAggregate::externalSources),
                JdbcWatchEntityVersionWriter::bindExternalSource);

        batch("INSERT INTO watch_designation (designation_id, entity_version_id, list_source, action, "
                        + "year, raw_verb, provenance_rule_id, provenance_start, provenance_end) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                flatten(aggregates, WatchEntityVersionAggregate::designations),
                JdbcWatchEntityVersionWriter::bindDesignation);

        batch("INSERT INTO watch_ownership (stake_id, entity_version_id, owner, owner_type, percent, "
                        + "percent_stated, provenance_rule_id, provenance_start, provenance_end) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                flatten(aggregates, WatchEntityVersionAggregate::ownerships),
                JdbcWatchEntityVersionWriter::bindOwnership);
    }

    private static <T> List<T> map(
            List<WatchEntityVersionAggregate> aggregates,
            Function<WatchEntityVersionAggregate, T> one) {
        return aggregates.stream().map(one).toList();
    }

    private static <T> List<T> flatten(
            List<WatchEntityVersionAggregate> aggregates,
            Function<WatchEntityVersionAggregate, List<T>> many) {
        return aggregates.stream().flatMap(a -> many.apply(a).stream()).toList();
    }

    /**
     * One prepare and one round-trip for a whole child table ACROSS THE CHUNK. With
     * reWriteBatchedInserts=true the driver sends it as a single multi-row INSERT.
     * An empty collection sends nothing at all -- most records have no ownership or links.
     */
    private <T> void batch(String sql, List<T> rows, RowBinder<T> binder) {
        if (rows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int index) throws SQLException {
                binder.bind(ps, rows.get(index));
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    @FunctionalInterface
    private interface RowBinder<T> {
        void bind(PreparedStatement ps, T row) throws SQLException;
    }

    private static void bindVersion(PreparedStatement ps, WatchEntityVersion version)
            throws SQLException {
        ps.setObject(1, version.entityVersionId().value());
        ps.setObject(2, version.entityId().value());
        ps.setObject(3, version.listVersionId().value());
        ps.setString(4, version.contentHash());
        ps.setString(5, version.entityType().name());
        ps.setString(6, version.gender().map(Enum::name).orElse(null));
        ps.setString(7, version.primaryName());
        ps.setString(8, version.status().name());
        // The connection of the batch's own statement, so arrays are built on the right session.
        ps.setArray(9, ps.getConnection().createArrayOf("text", version.categories().toArray()));
        ps.setArray(10, ps.getConnection().createArrayOf("text", version.listSources().toArray()));
        ps.setDate(11, version.designatedAt().map(Date::valueOf).orElse(null));
        ps.setDate(12, version.delistedAt().map(Date::valueOf).orElse(null));
    }

    private static void bindName(PreparedStatement ps, WatchName name) throws SQLException {
        ps.setObject(1, name.nameId());
        ps.setObject(2, name.entityVersionId().value());
        ps.setString(3, name.nameType().name());
        ps.setString(4, name.rawName());
        ps.setString(5, name.normalisedName());
        // The connection of the batch's own statement, so arrays are built on the right session.
        ps.setArray(6, ps.getConnection().createArrayOf("text", name.nameTokens().toArray()));
        ps.setArray(7, ps.getConnection().createArrayOf("text", name.phoneticCodes().toArray()));
        ps.setArray(8, ps.getConnection().createArrayOf("text", name.trigrams().toArray()));
        ps.setString(9, name.script());
        ps.setString(10, name.language());
        setProvenance(ps, 11, name.provenance());
    }

    private static void bindDob(PreparedStatement ps, WatchDob dob) throws SQLException {
        ps.setObject(1, dob.dobId());
        ps.setObject(2, dob.entityVersionId().value());
        setNullableInt(ps, 3, dob.year());
        setNullableInt(ps, 4, dob.month());
        setNullableInt(ps, 5, dob.day());
        setNullableInt(ps, 6, dob.age());
        ps.setDate(7, dob.asOfDate().map(Date::valueOf).orElse(null));
        ps.setDate(8, dob.deceased().map(Date::valueOf).orElse(null));
        ps.setString(9, dob.precision().name());
    }

    private static void bindIdentifier(PreparedStatement ps, WatchIdentifier identifier) throws SQLException {
        ps.setObject(1, identifier.identifierId());
        ps.setObject(2, identifier.entityVersionId().value());
        ps.setString(3, identifier.idType().name());
        ps.setString(4, identifier.idValue());
        ps.setString(5, identifier.normalisedIdValue());
        ps.setString(6, identifier.issuingCountry().orElse(null));
        setProvenance(ps, 7, identifier.provenance());
    }

    private static void bindCountry(PreparedStatement ps, WatchCountry country) throws SQLException {
        ps.setObject(1, country.countryId());
        ps.setObject(2, country.entityVersionId().value());
        ps.setString(3, country.rawCountry());
        ps.setString(4, country.isoCode().orElse(null));
    }

    private static void bindAddress(PreparedStatement ps, WatchAddress address) throws SQLException {
        ps.setObject(1, address.addressId());
        ps.setObject(2, address.entityVersionId().value());
        ps.setString(3, address.countryCode().orElse(null));
        ps.setString(4, address.city().orElse(null));
        ps.setString(5, address.state().orElse(null));
        ps.setString(6, address.rawAddress().orElse(null));
    }

    private static void bindLink(PreparedStatement ps, WatchLink link) throws SQLException {
        ps.setObject(1, link.linkId());
        ps.setObject(2, link.entityVersionId().value());
        ps.setObject(3, link.targetEntityId().value());
        ps.setString(4, link.role().orElse(null));
        if (link.percent().isPresent()) {
            ps.setDouble(5, link.percent().get());
        } else {
            ps.setNull(5, java.sql.Types.DOUBLE);
        }
    }

    private static void bindExternalSource(PreparedStatement ps, WatchExternalSource externalSource)
            throws SQLException {
        ps.setObject(1, externalSource.externalSourceId());
        ps.setObject(2, externalSource.entityVersionId().value());
        ps.setString(3, externalSource.uri());
    }

    private static void bindDesignation(PreparedStatement ps, WatchDesignation designation) throws SQLException {
        ps.setObject(1, designation.designationId());
        ps.setObject(2, designation.entityVersionId().value());
        ps.setString(3, designation.listSource());
        ps.setString(4, designation.action().name());
        setNullableInt(ps, 5, designation.year());
        ps.setString(6, designation.rawVerb());
        setProvenance(ps, 7, designation.provenance());
    }

    private static void bindOwnership(PreparedStatement ps, WatchOwnership ownership) throws SQLException {
        ps.setObject(1, ownership.stakeId());
        ps.setObject(2, ownership.entityVersionId().value());
        ps.setString(3, ownership.owner());
        ps.setString(4, ownership.ownerType().orElse(null));
        if (ownership.percent().isPresent()) {
            ps.setDouble(5, ownership.percent().get());
        } else {
            ps.setNull(5, java.sql.Types.DOUBLE);
        }
        ps.setBoolean(6, ownership.percentStated());
        setProvenance(ps, 7, ownership.provenance());
    }

    private static void setNullableInt(PreparedStatement ps, int index, Optional<Integer> value)
            throws SQLException {
        if (value.isPresent()) {
            ps.setInt(index, value.get());
        } else {
            ps.setNull(index, java.sql.Types.INTEGER);
        }
    }

    private static void setProvenance(PreparedStatement ps, int firstIndex, Optional<Provenance> provenance)
            throws SQLException {
        if (provenance.isPresent()) {
            ps.setString(firstIndex, provenance.get().ruleId());
            ps.setInt(firstIndex + 1, provenance.get().start());
            ps.setInt(firstIndex + 2, provenance.get().end());
        } else {
            ps.setNull(firstIndex, java.sql.Types.VARCHAR);
            ps.setNull(firstIndex + 1, java.sql.Types.INTEGER);
            ps.setNull(firstIndex + 2, java.sql.Types.INTEGER);
        }
    }
}
