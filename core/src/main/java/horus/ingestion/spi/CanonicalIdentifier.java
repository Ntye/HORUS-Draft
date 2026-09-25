package horus.ingestion.spi;

// idType is a plain string (not horus.domain.watchentity.IdType) because narrative-derived
// identifiers (TAX_ID, LEI, SWIFT_BIC, GROUP_ID, SDN_REF, ...) use a wider vocabulary than the
// structured record's IdType enum -- see legacy/narrative/IdentifierParser.
public record CanonicalIdentifier(String idType, String idValue, String sourcePath) {

    public CanonicalIdentifier {
        if (idType == null || idType.isBlank()) {
            throw new IllegalArgumentException("idType must not be blank");
        }
        if (idValue == null || idValue.isBlank()) {
            throw new IllegalArgumentException("idValue must not be blank");
        }
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath must not be blank");
        }
    }
}
