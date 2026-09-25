package horus.ingestion.spi;

// §6: location data lives in location@city/@country/@state attributes, not element text.
//
// The country component is called rawCountry, not countryCode, because that is what the World-Check
// feed actually carries: a country NAME. Calling it a code was not merely imprecise -- the mapper
// wrote it straight into watch_address.country_code VARCHAR(10), and a real load aborted on
// "value too long for type character varying(10)". A name is not a code, and the type system is the
// right place to stop that confusion (§9: a value object that can exist is a valid one).
public record CanonicalAddress(String rawCountry, String city, String state, String sourcePath) {

    public CanonicalAddress {
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath must not be blank");
        }
        boolean countryBlank = rawCountry == null || rawCountry.isBlank();
        boolean cityBlank = city == null || city.isBlank();
        boolean stateBlank = state == null || state.isBlank();
        if (countryBlank && cityBlank && stateBlank) {
            throw new IllegalArgumentException("at least one of rawCountry, city or state must be present");
        }
    }
}
