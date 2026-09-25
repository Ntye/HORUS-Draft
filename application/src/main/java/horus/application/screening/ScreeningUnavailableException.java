package horus.application.screening;

// Thrown when a screening cannot even begin -- no approved configuration, or no active list
// version to screen against. The caller must surface this as an error and halt; it is never a
// clear (I-1). Carries no query data (I-11).
public final class ScreeningUnavailableException extends RuntimeException {

    public ScreeningUnavailableException(String message) {
        super(message);
    }
}
