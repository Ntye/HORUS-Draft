package horus.adapter.persistence;

import horus.domain.shared.EntityId;
import horus.domain.shared.EntityType;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.Gender;
import horus.domain.shared.ListVersionId;
import horus.domain.watchentity.EntityStatus;
import horus.domain.watchentity.WatchEntityVersion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Array;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Shared between the repositories that read watch_entity_version rows back (currently only
// ActiveEntityVersionLookup -- STAGE only ever writes).
final class WatchEntityVersionRowMapper {

    private WatchEntityVersionRowMapper() {
    }

    static WatchEntityVersion map(ResultSet rs) throws SQLException {
        String gender = rs.getString("gender");
        LocalDate designatedAt = rs.getDate("designated_at") == null ? null : rs.getDate("designated_at").toLocalDate();
        LocalDate delistedAt = rs.getDate("delisted_at") == null ? null : rs.getDate("delisted_at").toLocalDate();
        return new WatchEntityVersion(
                new EntityVersionId((UUID) rs.getObject("entity_version_id")),
                new EntityId((UUID) rs.getObject("entity_id")),
                new ListVersionId((UUID) rs.getObject("list_version_id")),
                rs.getString("content_hash"),
                EntityType.valueOf(rs.getString("entity_type")),
                Optional.ofNullable(gender).map(Gender::valueOf),
                rs.getString("primary_name"),
                EntityStatus.valueOf(rs.getString("status")),
                toList(rs.getArray("categories")),
                toList(rs.getArray("list_sources")),
                Optional.ofNullable(designatedAt),
                Optional.ofNullable(delistedAt));
    }

    private static List<String> toList(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        return List.of((String[]) sqlArray.getArray());
    }
}
