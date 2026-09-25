package horus.ingestion.spi;

import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.EntityType;
import horus.domain.shared.Gender;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// «partial is NORMAL» (horus-ingestion-spi.drawio). absentSlots and provenance are how a
// mis-mapped or genuinely-absent field is told apart from one nobody looked for.
public record CanonicalRecord(
        String sourceId,
        String sourceRecordId,
        EntityType entityType,
        Optional<Gender> gender,
        List<CanonicalName> names,
        List<CanonicalDob> dobs,
        List<CanonicalCountry> countries,
        List<CanonicalAddress> addresses,
        List<CanonicalIdentifier> identifiers,
        List<CanonicalDesignation> designations,
        List<CanonicalOwnership> ownership,
        Set<CanonicalSlot> absentSlots,
        Map<CanonicalSlot, String> provenance) {

    public CanonicalRecord {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        if (sourceRecordId == null || sourceRecordId.isBlank()) {
            throw new IllegalArgumentException("sourceRecordId must not be blank");
        }
        if (entityType == null) {
            throw new IllegalArgumentException("entityType must not be null");
        }
        if (gender == null) {
            throw new IllegalArgumentException("gender must not be null (use Optional.empty())");
        }
        if (names == null || names.isEmpty()) {
            throw new IllegalArgumentException("names must not be empty");
        }
        if (dobs == null || countries == null || addresses == null || identifiers == null
                || designations == null || ownership == null) {
            throw new IllegalArgumentException("child lists must not be null (use List.of())");
        }
        if (absentSlots == null) {
            throw new IllegalArgumentException("absentSlots must not be null");
        }
        if (provenance == null) {
            throw new IllegalArgumentException("provenance must not be null");
        }
        names = List.copyOf(names);
        dobs = List.copyOf(dobs);
        countries = List.copyOf(countries);
        addresses = List.copyOf(addresses);
        identifiers = List.copyOf(identifiers);
        designations = List.copyOf(designations);
        ownership = List.copyOf(ownership);
        absentSlots = Set.copyOf(absentSlots);
        provenance = Map.copyOf(provenance);
    }

    public boolean has(CanonicalSlot slot) {
        return !absentSlots.contains(slot);
    }
}
