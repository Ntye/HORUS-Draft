package horus.ingestion.spi;

/**
 * Thrown by a {@link WatchlistSourceAdapter} when a record cannot be mapped, carrying a controlled
 * reason code rather than a free-text message.
 *
 * <p>The first real load of the World-Check feed rejected 5,979,934 of 5,990,394 records and
 * reported the reason as {@code IllegalStateException}, because {@code LoadWatchlistVersion} records
 * {@code e.getClass().getSimpleName()}. Three hours of work said <em>that</em> it failed and not
 * <em>why</em>. A reason code fixes that, and a code -- rather than an exception message -- is what
 * makes it safe to print: messages interpolate the offending value, and in this feed the offending
 * value is a name or a date of birth (I-11).
 *
 * <p>So a code is a short, fixed token from the adapter's own vocabulary, optionally qualified by a
 * value the adapter knows to be a classification code rather than data about a person -- an
 * {@code @e-i} letter, say. Nothing else may be appended.
 */
public final class RecordMappingException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final int MAX_CODE_LENGTH = 80;

    private final String reasonCode;

    public RecordMappingException(String reasonCode) {
        super(reasonCode);
        if (reasonCode == null || reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        if (reasonCode.length() > MAX_CODE_LENGTH) {
            // A long code means someone has interpolated data into it, which is exactly what this
            // type exists to prevent -- fail loudly at the mistake rather than leak it into a log.
            throw new IllegalArgumentException("reasonCode must be at most " + MAX_CODE_LENGTH + " characters");
        }
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }

    /**
     * The reason code if this is a mapping failure with one, otherwise the exception's simple class
     * name -- so an unexpected fault is still distinguishable from a known one in a report.
     */
    public static String reasonCodeOf(RuntimeException e) {
        return e instanceof RecordMappingException mapping
                ? mapping.reasonCode()
                : e.getClass().getSimpleName();
    }
}
