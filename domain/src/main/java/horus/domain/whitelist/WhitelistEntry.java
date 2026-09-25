package horus.domain.whitelist;

import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.WhitelistId;
import java.time.Instant;
import java.util.Optional;

public record WhitelistEntry(
        WhitelistId whitelistId,
        WhitelistScope scope,
        String queryNameNormalised,
        EntityId entityId,
        EntityVersionId approvedAgainstVersionId,
        String justification,
        String approvedBy,
        Instant approvedAt,
        Optional<Instant> expiresAt) {

    public WhitelistEntry {
        if (whitelistId == null) {
            throw new IllegalArgumentException("whitelistId must not be null");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        if (queryNameNormalised == null || queryNameNormalised.isBlank()) {
            throw new IllegalArgumentException("queryNameNormalised must not be blank");
        }
        if (entityId == null) {
            throw new IllegalArgumentException("entityId must not be null");
        }
        if (approvedAgainstVersionId == null) {
            throw new IllegalArgumentException("approvedAgainstVersionId must not be null");
        }
        if (justification == null || justification.isBlank()) {
            throw new IllegalArgumentException("justification must not be blank");
        }
        if (approvedBy == null || approvedBy.isBlank()) {
            throw new IllegalArgumentException("approvedBy must not be blank");
        }
        if (approvedAt == null) {
            throw new IllegalArgumentException("approvedAt must not be null");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null (use Optional.empty())");
        }
    }

    // I-8: flags and demotes, never suppresses -- this only answers version staleness; the
    // caller still decides how much to demote a candidate whose whitelist entry is inactive.
    public boolean isActiveFor(EntityVersionId currentVersionId) {
        if (currentVersionId == null) {
            throw new IllegalArgumentException("currentVersionId must not be null");
        }
        return approvedAgainstVersionId.equals(currentVersionId);
    }
}
