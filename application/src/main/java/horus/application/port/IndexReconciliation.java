package horus.application.port;

import horus.domain.shared.ListVersionId;

// I-1, CLAUDE.md §5 ("IndexReconciliation gates promote()"). No separate search index exists
// yet in this iteration (Step 7 builds it) -- the only structural index today is the Postgres
// GIN trigram index, which updates transactionally as rows insert, so there is no separate
// "build" phase to gate on. This gate instead checks that the staged data actually landed: at
// least one watch_entity_version row exists for the list version, unless it staged zero source
// records (record_count = 0), which is vacuously complete. It exists to catch a load that was
// interrupted before any writes committed, so a later PromoteListVersion or
// ConfirmStagedVersion call -- possibly a separate process from the one that staged -- never
// promotes an empty or partially-written version.
public interface IndexReconciliation {

    boolean isComplete(ListVersionId listVersionId);
}
