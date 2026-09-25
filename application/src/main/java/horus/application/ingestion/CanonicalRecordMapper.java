package horus.application.ingestion;

import horus.application.port.IdGenerator;
import horus.application.port.WatchEntityVersionAggregate;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import horus.domain.shared.ListVersionId;
import horus.domain.watchentity.DobPrecision;
import horus.domain.watchentity.EntityStatus;
import horus.domain.watchentity.IdType;
import horus.domain.watchentity.NameType;
import horus.domain.watchentity.WatchAddress;
import horus.domain.watchentity.WatchCountry;
import horus.domain.watchentity.WatchDesignation;
import horus.domain.watchentity.WatchDob;
import horus.domain.watchentity.WatchEntityVersion;
import horus.domain.watchentity.WatchIdentifier;
import horus.domain.watchentity.WatchName;
import horus.domain.watchentity.WatchOwnership;
import horus.ingestion.spi.CanonicalAddress;
import horus.ingestion.spi.CanonicalCountry;
import horus.ingestion.spi.CanonicalDesignation;
import horus.ingestion.spi.CanonicalDob;
import horus.ingestion.spi.CanonicalIdentifier;
import horus.ingestion.spi.CanonicalName;
import horus.ingestion.spi.CanonicalOwnership;
import horus.ingestion.spi.CanonicalRecord;
import horus.normalisation.NormalisationPipeline;
import horus.normalisation.NormalisedName;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// I-10: the one shared NormalisationPipeline, used identically here (ingestion) and at query
// time (Step 9). Turns a CanonicalRecord into the WatchEntityVersion + children CLAUDE.md §5
// says every child keys on entityVersionId, not entityId.
final class CanonicalRecordMapper {

    private final NormalisationPipeline normalisationPipeline;
    private final IdGenerator idGenerator;

    CanonicalRecordMapper(NormalisationPipeline normalisationPipeline, IdGenerator idGenerator) {
        this.normalisationPipeline = normalisationPipeline;
        this.idGenerator = idGenerator;
    }

    WatchEntityVersionAggregate toAggregate(
            CanonicalRecord record, EntityId entityId, EntityVersionId entityVersionId,
            ListVersionId listVersionId, String contentHash) {
        String primaryName = record.names().stream()
                .filter(n -> n.type() == NameType.PRIMARY)
                .findFirst()
                .or(() -> record.names().stream().findFirst())
                .map(CanonicalName::value)
                .orElseThrow(() -> new IllegalStateException("a CanonicalRecord always carries at least one name"));

        List<String> listSources = record.designations().stream()
                .map(CanonicalDesignation::listSource)
                .distinct()
                .sorted()
                .toList();

        WatchEntityVersion version = new WatchEntityVersion(
                entityVersionId,
                entityId,
                listVersionId,
                contentHash,
                record.entityType(),
                record.gender(),
                primaryName,
                EntityStatus.ACTIVE,
                // KNOWN GAP: WorldCheckAdapter does not currently surface @category on
                // CanonicalRecord (see its own KNOWN GAP comment), so there is nothing to map
                // categories from yet.
                List.of(),
                listSources,
                Optional.empty(),
                Optional.empty());

        return new WatchEntityVersionAggregate(
                version,
                names(record, entityVersionId),
                dobs(record, entityVersionId),
                identifiers(record, entityVersionId),
                countries(record, entityVersionId),
                addresses(record, entityVersionId),
                List.of(),
                List.of(),
                designations(record, entityVersionId),
                ownerships(record, entityVersionId));
    }

    private List<WatchName> names(CanonicalRecord record, EntityVersionId entityVersionId) {
        return record.names().stream()
                .map(name -> {
                    NormalisedName normalised = normalisationPipeline.normalise(name.value());
                    return new WatchName(
                            idGenerator.newId(),
                            entityVersionId,
                            name.type(),
                            name.value(),
                            normalised.normalised(),
                            normalised.tokens(),
                            normalised.phoneticCodes(),
                            normalised.trigrams(),
                            normalised.script(),
                            null,
                            Optional.empty());
                })
                .toList();
    }

    private List<WatchDob> dobs(CanonicalRecord record, EntityVersionId entityVersionId) {
        return record.dobs().stream()
                .map(dob -> new WatchDob(
                        idGenerator.newId(),
                        entityVersionId,
                        dob.year(),
                        dob.month(),
                        dob.day(),
                        dob.age(),
                        dob.asOfDate(),
                        dob.deceased(),
                        precisionOf(dob)))
                .toList();
    }

    private static DobPrecision precisionOf(CanonicalDob dob) {
        if (dob.year().isPresent() && dob.month().isPresent() && dob.day().isPresent()) {
            return DobPrecision.FULL_DATE;
        }
        if (dob.year().isPresent() && dob.month().isPresent()) {
            return DobPrecision.YEAR_MONTH;
        }
        if (dob.year().isPresent()) {
            return DobPrecision.YEAR_ONLY;
        }
        return DobPrecision.AGE_ONLY;
    }

    private List<WatchIdentifier> identifiers(CanonicalRecord record, EntityVersionId entityVersionId) {
        return record.identifiers().stream()
                .map(identifier -> new WatchIdentifier(
                        idGenerator.newId(),
                        entityVersionId,
                        idTypeOf(identifier),
                        identifier.idValue(),
                        identifier.idValue().strip().toUpperCase(Locale.ROOT),
                        Optional.empty(),
                        Optional.empty()))
                .toList();
    }

    private static IdType idTypeOf(CanonicalIdentifier identifier) {
        try {
            return IdType.valueOf(identifier.idType().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return IdType.OTHER;
        }
    }

    private List<WatchCountry> countries(CanonicalRecord record, EntityVersionId entityVersionId) {
        return record.countries().stream()
                .map(country -> new WatchCountry(
                        idGenerator.newId(), entityVersionId, country.rawCountry(), Optional.empty()))
                .toList();
    }

    // "Dubai, Dubai, UNITED ARAB EMIRATES" -- the parts the source supplied, in the order it gave
    // them, with nothing invented and nothing dropped.
    private static Optional<String> rawAddressOf(CanonicalAddress address) {
        String composed = Stream.of(address.city(), address.state(), address.rawCountry())
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .collect(Collectors.joining(", "));
        return composed.isBlank() ? Optional.empty() : Optional.of(composed);
    }

    private List<WatchAddress> addresses(CanonicalRecord record, EntityVersionId entityVersionId) {
        return record.addresses().stream()
                // countryCode stays empty: the source gives a country NAME, and watch_address's
                // country_code is a code column. The name is not discarded -- it goes into
                // raw_address, which is exactly what it is: the address as the source wrote it.
                // Normalising names to ISO codes is a separate decision with screening consequences
                // (see D12), not something to smuggle in here.
                .map(address -> new WatchAddress(
                        idGenerator.newId(),
                        entityVersionId,
                        Optional.empty(),
                        Optional.ofNullable(address.city()),
                        Optional.ofNullable(address.state()),
                        rawAddressOf(address)))
                .toList();
    }

    private List<WatchDesignation> designations(CanonicalRecord record, EntityVersionId entityVersionId) {
        return record.designations().stream()
                .map(designation -> new WatchDesignation(
                        idGenerator.newId(),
                        entityVersionId,
                        designation.listSource(),
                        designation.action(),
                        Optional.of(designation.year()),
                        designation.rawVerb(),
                        Optional.of(designation.provenance())))
                .toList();
    }

    private List<WatchOwnership> ownerships(CanonicalRecord record, EntityVersionId entityVersionId) {
        return record.ownership().stream()
                .map(ownership -> new WatchOwnership(
                        idGenerator.newId(),
                        entityVersionId,
                        ownership.owner(),
                        Optional.ofNullable(ownership.ownerType()),
                        Optional.ofNullable(ownership.percent()),
                        ownership.percentStated(),
                        Optional.of(ownership.provenance())))
                .toList();
    }
}
