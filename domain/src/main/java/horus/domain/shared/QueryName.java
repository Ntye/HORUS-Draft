package horus.domain.shared;

public record QueryName(String value) {

    public static final int MAX_LENGTH = 500;

    public QueryName {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        value = value.strip();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("value must not exceed " + MAX_LENGTH + " characters");
        }
    }
}
