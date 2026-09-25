package horus.domain.watchentity;

import horus.domain.shared.EntityVersionId;
import java.util.Optional;
import java.util.UUID;

// Attribute-sourced (location@city/@country/@state), not free text -- see CLAUDE.md §6.
public record WatchAddress(
        UUID addressId,
        EntityVersionId entityVersionId,
        Optional<String> countryCode,
        Optional<String> city,
        Optional<String> state,
        Optional<String> rawAddress) {

    public WatchAddress {
        if (addressId == null) {
            throw new IllegalArgumentException("addressId must not be null");
        }
        if (entityVersionId == null) {
            throw new IllegalArgumentException("entityVersionId must not be null");
        }
        if (countryCode == null || city == null || state == null || rawAddress == null) {
            throw new IllegalArgumentException("optional fields must not be null (use Optional.empty())");
        }
        if (countryCode.isEmpty() && city.isEmpty() && state.isEmpty() && rawAddress.isEmpty()) {
            throw new IllegalArgumentException("at least one address field must be present");
        }
    }
}
