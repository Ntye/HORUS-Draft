package horus.ingestion.spi;

import horus.domain.shared.CanonicalSlot;
import horus.domain.shared.EntityType;
import horus.domain.shared.Gender;
import horus.domain.watchentity.NameType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CanonicalRecordBuilder {

    private String sourceId;
    private String sourceRecordId;
    private EntityType entityType;
    private Optional<Gender> gender = Optional.empty();
    private final List<CanonicalName> names = new ArrayList<>();
    private final List<CanonicalDob> dobs = new ArrayList<>();
    private final List<CanonicalCountry> countries = new ArrayList<>();
    private final List<CanonicalAddress> addresses = new ArrayList<>();
    private final List<CanonicalIdentifier> identifiers = new ArrayList<>();
    private final List<CanonicalDesignation> designations = new ArrayList<>();
    private final List<CanonicalOwnership> ownership = new ArrayList<>();
    private final Set<CanonicalSlot> absentSlots = EnumSet.noneOf(CanonicalSlot.class);
    private final Map<CanonicalSlot, String> provenance = new EnumMap<>(CanonicalSlot.class);

    public CanonicalRecordBuilder sourceId(String v) {
        this.sourceId = v;
        return this;
    }

    public CanonicalRecordBuilder sourceRecordId(String v) {
        this.sourceRecordId = v;
        return this;
    }

    public CanonicalRecordBuilder entityType(EntityType t, String sourcePath) {
        this.entityType = t;
        provenance.put(CanonicalSlot.ENTITY_TYPE, sourcePath);
        return this;
    }

    public CanonicalRecordBuilder gender(Gender g, String sourcePath) {
        this.gender = Optional.ofNullable(g);
        provenance.put(CanonicalSlot.GENDER, sourcePath);
        return this;
    }

    public CanonicalRecordBuilder name(String value, NameType type, String sourcePath) {
        names.add(new CanonicalName(value, type, sourcePath));
        provenance.putIfAbsent(
                type == NameType.PRIMARY ? CanonicalSlot.PRIMARY_NAME : CanonicalSlot.ALIAS,
                sourcePath);
        return this;
    }

    public CanonicalRecordBuilder dob(CanonicalDob dob) {
        dobs.add(dob);
        provenance.putIfAbsent(CanonicalSlot.DATE_OF_BIRTH, dob.sourcePath());
        return this;
    }

    public CanonicalRecordBuilder country(CanonicalCountry country) {
        countries.add(country);
        provenance.putIfAbsent(CanonicalSlot.COUNTRY, country.sourcePath());
        return this;
    }

    public CanonicalRecordBuilder address(CanonicalAddress address) {
        addresses.add(address);
        provenance.putIfAbsent(CanonicalSlot.ADDRESS, address.sourcePath());
        return this;
    }

    public CanonicalRecordBuilder identifier(CanonicalIdentifier identifier) {
        identifiers.add(identifier);
        provenance.putIfAbsent(CanonicalSlot.IDENTIFIER, identifier.sourcePath());
        return this;
    }

    public CanonicalRecordBuilder designation(CanonicalDesignation designation) {
        designations.add(designation);
        provenance.putIfAbsent(CanonicalSlot.DESIGNATION_DATE, designation.provenance().ruleId());
        return this;
    }

    public CanonicalRecordBuilder ownership(CanonicalOwnership stake) {
        ownership.add(stake);
        provenance.putIfAbsent(CanonicalSlot.OWNERSHIP, stake.provenance().ruleId());
        return this;
    }

    public CanonicalRecordBuilder absent(CanonicalSlot slot, String reason) {
        absentSlots.add(slot);
        provenance.putIfAbsent(slot, reason);
        return this;
    }

    public CanonicalRecord build() {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalStateException("sourceId is required");
        }
        if (names.isEmpty()) {
            throw new IllegalStateException("at least one name is required");
        }
        return new CanonicalRecord(
                sourceId, sourceRecordId, entityType, gender,
                names, dobs, countries, addresses, identifiers, designations, ownership,
                absentSlots, provenance);
    }
}
