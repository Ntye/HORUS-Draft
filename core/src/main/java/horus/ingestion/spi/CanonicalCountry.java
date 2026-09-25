package horus.ingestion.spi;

public record CanonicalCountry(String rawCountry, String sourcePath) {

    public CanonicalCountry {
        if (rawCountry == null || rawCountry.isBlank()) {
            throw new IllegalArgumentException("rawCountry must not be blank");
        }
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath must not be blank");
        }
    }
}
