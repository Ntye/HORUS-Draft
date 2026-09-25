package horus.domain.shared;

// I-1: ERROR exists precisely so unavailability is never interpreted as NO_MATCH.
public enum DecisionBand {
    NO_MATCH,
    POSSIBLE_MATCH,
    STRONG_MATCH,
    ERROR
}
