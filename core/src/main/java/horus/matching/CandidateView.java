package horus.matching;

import horus.domain.shared.EntityId;
import horus.domain.shared.EntityType;
import horus.domain.shared.EntityVersionId;
import horus.normalisation.NormalisedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

// Assembled by the application layer; comparators and measures read this, never the entities
// (CLAUDE.md §5). Built from primitives and the shared kernel so horus.matching stays pure (I-9).
// `delisted` = the entity's current version is a tombstone; the names are those of its last live
// version, so a de-listed entity is still matchable (I-8).
public record CandidateView(
        EntityId entityId,
        EntityVersionId entityVersionId,
        String sourceId,
        EntityType entityType,
        boolean delisted,
        List<CandidateName> names,
        List<CandidateDob> dobs,
        List<String> countryCodes,
        List<CandidateIdentifier> identifiers) {

    public CandidateView {
        if (entityId == null || entityVersionId == null || entityType == null) {
            throw new IllegalArgumentException("entityId, entityVersionId and entityType must not be null");
        }
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("a candidate must have at least one name");
        }
        if (dobs == null || countryCodes == null || identifiers == null) {
            throw new IllegalArgumentException("dobs, countryCodes and identifiers must not be null");
        }
        names = List.copyOf(names);
        dobs = List.copyOf(dobs);
        countryCodes = List.copyOf(new TreeSet<>(countryCodes));
        identifiers = List.copyOf(identifiers);
    }

    public static Builder builder(EntityId entityId, EntityVersionId entityVersionId, String sourceId,
            EntityType entityType) {
        return new Builder(entityId, entityVersionId, sourceId, entityType);
    }

    public static final class Builder {
        private final EntityId entityId;
        private final EntityVersionId entityVersionId;
        private final String sourceId;
        private final EntityType entityType;
        private boolean delisted;
        private final List<CandidateName> names = new ArrayList<>();
        private final List<CandidateDob> dobs = new ArrayList<>();
        private final List<String> countries = new ArrayList<>();
        private final List<CandidateIdentifier> identifiers = new ArrayList<>();

        private Builder(EntityId entityId, EntityVersionId entityVersionId, String sourceId, EntityType entityType) {
            this.entityId = entityId;
            this.entityVersionId = entityVersionId;
            this.sourceId = sourceId;
            this.entityType = entityType;
        }

        public Builder primaryName(NormalisedName name) {
            names.add(0, new CandidateName(name, false));
            return this;
        }

        public Builder alias(NormalisedName name) {
            names.add(new CandidateName(name, true));
            return this;
        }

        public Builder dob(CandidateDob dob) {
            dobs.add(dob);
            return this;
        }

        public Builder country(String code) {
            countries.add(code);
            return this;
        }

        public Builder identifier(CandidateIdentifier identifier) {
            identifiers.add(identifier);
            return this;
        }

        public Builder delisted(boolean delisted) {
            this.delisted = delisted;
            return this;
        }

        public CandidateView build() {
            return new CandidateView(entityId, entityVersionId, sourceId, entityType, delisted,
                    Collections.unmodifiableList(names), dobs, countries, identifiers);
        }
    }
}
