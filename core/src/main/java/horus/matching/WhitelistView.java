package horus.matching;

import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import java.util.Map;

// What the engine needs to know about whitelisting, resolved by the application layer for this
// query name. An entry references the stable entityId and records the version it was approved
// against; when the candidate's version differs the entry is inactive (§21, mirrors
// WhitelistEntry.isActiveFor). Whitelisting flags and demotes -- it never suppresses (I-8).
public record WhitelistView(Map<EntityId, EntityVersionId> approvedAgainst) {

    public enum Status {
        NONE,
        ACTIVE,
        STALE
    }

    public WhitelistView {
        if (approvedAgainst == null) {
            throw new IllegalArgumentException("approvedAgainst must not be null");
        }
        approvedAgainst = Map.copyOf(approvedAgainst);
    }

    public static WhitelistView empty() {
        return new WhitelistView(Map.of());
    }

    public Status statusFor(EntityId entityId, EntityVersionId currentVersionId) {
        EntityVersionId approved = approvedAgainst.get(entityId);
        if (approved == null) {
            return Status.NONE;
        }
        return approved.equals(currentVersionId) ? Status.ACTIVE : Status.STALE;
    }
}
