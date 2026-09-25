package horus.adapter.persistence;

import horus.application.port.IndexReconciliation;
import horus.domain.shared.ListVersionId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// See IndexReconciliation's port doc for what "complete" means in this iteration and why: no
// separate search index exists yet (Step 7), so this checks that the staged data actually
// landed rather than gating on an index build phase that doesn't exist.
//
// This is a weak check by construction: at promote time there is no independent expectation to
// compare a row count against -- record_count counts records READ, which on a reload exceeds the
// versions written by however many were UNCHANGED. The strong check lives in LoadWatchlistVersion,
// which knows that added + amended + delisted version rows must exist and reads the count back
// before it returns anything that could be promoted (I-1). Do not weaken that one to lean on this.
@Component
public final class JdbcIndexReconciliation implements IndexReconciliation {

    private final JdbcTemplate jdbcTemplate;

    public JdbcIndexReconciliation(@Qualifier("ingestJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isComplete(ListVersionId listVersionId) {
        Long recordCount = jdbcTemplate.queryForObject(
                "SELECT record_count FROM list_version WHERE list_version_id = ?", Long.class,
                listVersionId.value());
        if (recordCount == null || recordCount == 0) {
            return true;
        }
        Long versionRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM watch_entity_version WHERE list_version_id = ?", Long.class,
                listVersionId.value());
        return versionRows != null && versionRows > 0;
    }
}
