package horus.application.port;

import horus.domain.watchentity.WatchAddress;
import horus.domain.watchentity.WatchCountry;
import horus.domain.watchentity.WatchDesignation;
import horus.domain.watchentity.WatchDob;
import horus.domain.watchentity.WatchEntityVersion;
import horus.domain.watchentity.WatchExternalSource;
import horus.domain.watchentity.WatchIdentifier;
import horus.domain.watchentity.WatchLink;
import horus.domain.watchentity.WatchName;
import horus.domain.watchentity.WatchOwnership;
import java.util.List;

// One entity version plus every child row that keys on its entityVersionId (CLAUDE.md §5), so
// STAGE writes an entity's whole slice atomically instead of row-by-row.
public record WatchEntityVersionAggregate(
        WatchEntityVersion version,
        List<WatchName> names,
        List<WatchDob> dobs,
        List<WatchIdentifier> identifiers,
        List<WatchCountry> countries,
        List<WatchAddress> addresses,
        List<WatchLink> links,
        List<WatchExternalSource> externalSources,
        List<WatchDesignation> designations,
        List<WatchOwnership> ownerships) {

    public WatchEntityVersionAggregate {
        if (version == null) {
            throw new IllegalArgumentException("version must not be null");
        }
        if (names == null || dobs == null || identifiers == null || countries == null
                || addresses == null || links == null || externalSources == null
                || designations == null || ownerships == null) {
            throw new IllegalArgumentException("child lists must not be null (use List.of())");
        }
        names = List.copyOf(names);
        dobs = List.copyOf(dobs);
        identifiers = List.copyOf(identifiers);
        countries = List.copyOf(countries);
        addresses = List.copyOf(addresses);
        links = List.copyOf(links);
        externalSources = List.copyOf(externalSources);
        designations = List.copyOf(designations);
        ownerships = List.copyOf(ownerships);
    }
}
