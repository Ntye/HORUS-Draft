package horus.ingestion.spi;

import horus.domain.watchentity.NameType;

public record CanonicalName(String value, NameType type, String sourcePath) {

    public CanonicalName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (sourcePath == null || sourcePath.isBlank()) {
            throw new IllegalArgumentException("sourcePath must not be blank");
        }
    }
}
