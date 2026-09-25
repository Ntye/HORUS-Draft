package horus.application.port;

import horus.domain.shared.EntityId;
import horus.domain.watchentity.WatchEntity;
import java.util.List;
import java.util.Map;

public interface WatchEntityRepository {

    /**
     * Ensures a stable identity exists for each candidate and returns, per {@code sourceEntityId},
     * the id to use: the candidate's own when the row is new, the existing row's when it is not.
     * Every candidate must carry the same {@code sourceId}, which is the key's other half.
     *
     * <p>Why this resolves identity rather than asserting it: {@code watch_entity} is stable
     * identity -- never versioned, never deleted (CLAUDE.md §5) -- so a row outlives the load that
     * created it, including a load that failed. {@code current_watch_entity_version} deliberately
     * excludes FAILED and STAGED loads, so after a failed load the identity row exists while the
     * entity is invisible to {@link ActiveEntityVersionLookup#loadActiveSnapshot}. A caller that
     * reads "absent from the snapshot" as "safe to insert identity" is right only if no load has
     * ever failed. It broke twice on the real feed (D12 H-9): a retry of a file whose previous
     * attempt had aborted rejected every record it had already reached with
     * {@code SQL_23505:watch_entity_source_id_source_entity_id_key}.
     *
     * <p>Adopting the existing row is what the model already says is correct. The identity is the
     * stable thing; the failed load's versions stay queryable as history. Nothing is rewritten and
     * nothing is deleted, so I-6 holds -- and the proof is that this needs no new privilege:
     * {@code horus_ingest} still holds only SELECT and INSERT on the table.
     *
     * <p>A LIST for the same reason the version writer takes one: one round-trip per thousand
     * entities rather than one per entity.
     */
    Map<String, EntityId> ensureAll(List<WatchEntity> candidates);

    default EntityId ensure(WatchEntity candidate) {
        return ensureAll(List.of(candidate)).get(candidate.sourceEntityId());
    }
}
