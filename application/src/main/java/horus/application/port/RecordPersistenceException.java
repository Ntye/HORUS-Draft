package horus.application.port;

/**
 * Thrown by a persistence adapter when one record cannot be written, carrying a controlled reason
 * code that names the failure without quoting the value that caused it.
 *
 * <p>Why this exists: the load loop must be able to say which constraint a record broke.
 * {@code PERSIST_FAILED:PSQLException} told us that twelve records of two hundred failed and nothing
 * more, which is the same shortcoming that made the first real load undiagnosable (D12 B-6), one
 * layer down. A JDBC message would say everything -- and would also quote the offending value, which
 * in this feed is a name, an address or a date of birth (I-11).
 *
 * <p>So the adapter reduces the failure to identifiers only: the SQLSTATE, and the constraint or
 * {@code table.column} the server named. Those are schema facts. The constructor rejects anything
 * containing whitespace, because free text is how a value would get in.
 *
 * <p>It lives in {@code horus.application.port} rather than in the adapter because the interactor
 * has to catch it, and {@code horus.application} may not depend on an adapter, on Spring or on
 * {@code java.sql} (ArchUnit rules 1 and 5).
 */
public final class RecordPersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final int MAX_CODE_LENGTH = 80;

    private final String reasonCode;

    public RecordPersistenceException(String reasonCode, Throwable cause) {
        super(reasonCode, cause);
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        if (reasonCode.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("reasonCode must be at most " + MAX_CODE_LENGTH + " characters");
        }
        if (reasonCode.chars().anyMatch(Character::isWhitespace)) {
            // Whitespace means prose, and prose means a value has been interpolated.
            throw new IllegalArgumentException("reasonCode must not contain whitespace");
        }
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
