package horus.matching;

// Thrown at boot by Registry.validateAgainst (I-7). The message lists every problem found and
// carries configuration facts only -- never a query name (I-11).
public final class InvalidMatchConfigException extends IllegalStateException {

    public InvalidMatchConfigException(String message) {
        super(message);
    }
}
