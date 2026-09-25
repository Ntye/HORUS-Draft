package horus.domain.watchentity;

import horus.domain.shared.EntityId;
import horus.domain.shared.EntityType;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.Gender;
import horus.domain.shared.ListVersionId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record WatchEntityVersion(
        EntityVersionId entityVersionId,
        EntityId entityId,
        ListVersionId listVersionId,
        String contentHash,
        EntityType entityType,
        Optional<Gender> gender,
        String primaryName,
        EntityStatus status,
        List<String> categories,
        List<String> listSources,
        Optional<LocalDate> designatedAt,
        Optional<LocalDate> delistedAt) {

    public WatchEntityVersion {
        if (entityVersionId == null) {
            throw new IllegalArgumentException("entityVersionId must not be null");
        }
        if (entityId == null) {
            throw new IllegalArgumentException("entityId must not be null");
        }
        if (listVersionId == null) {
            throw new IllegalArgumentException("listVersionId must not be null");
        }
        if (contentHash == null || contentHash.isBlank()) {
            throw new IllegalArgumentException("contentHash must not be blank");
        }
        if (entityType == null) {
            throw new IllegalArgumentException("entityType must not be null");
        }
        if (gender == null) {
            throw new IllegalArgumentException("gender must not be null (use Optional.empty())");
        }
        if (primaryName == null || primaryName.isBlank()) {
            throw new IllegalArgumentException("primaryName must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (categories == null) {
            throw new IllegalArgumentException("categories must not be null");
        }
        if (listSources == null) {
            throw new IllegalArgumentException("listSources must not be null");
        }
        if (designatedAt == null) {
            throw new IllegalArgumentException("designatedAt must not be null (use Optional.empty())");
        }
        if (delistedAt == null) {
            throw new IllegalArgumentException("delistedAt must not be null (use Optional.empty())");
        }
        // I-6: de-listing is a tombstone version, so DELISTED without a date is a contradiction.
        if (status == EntityStatus.DELISTED && delistedAt.isEmpty()) {
            throw new IllegalArgumentException("delistedAt is required when status is DELISTED");
        }
        // I-4: sort before this leaves the constructor, not at every emission site.
        categories = categories.stream().sorted().toList();
        listSources = listSources.stream().sorted().toList();
    }

    // I-6: never hard-delete watchlist data -- de-listing creates a new, immutable version.
    public static WatchEntityVersion tombstone(
            WatchEntityVersion previous,
            EntityVersionId newEntityVersionId,
            ListVersionId newListVersionId,
            String contentHash,
            LocalDate delistedAt) {
        if (previous == null) {
            throw new IllegalArgumentException("previous must not be null");
        }
        if (delistedAt == null) {
            throw new IllegalArgumentException("delistedAt must not be null");
        }
        return new WatchEntityVersion(
                newEntityVersionId,
                previous.entityId(),
                newListVersionId,
                contentHash,
                previous.entityType(),
                previous.gender(),
                previous.primaryName(),
                EntityStatus.DELISTED,
                previous.categories(),
                previous.listSources(),
                previous.designatedAt(),
                Optional.of(delistedAt));
    }
}
