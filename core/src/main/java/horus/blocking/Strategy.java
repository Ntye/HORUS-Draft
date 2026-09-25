package horus.blocking;

// Candidate-generation strategies. The union of all configured strategies is the candidate set
// (I-2: recall is the binding constraint, so never an intersection).
public enum Strategy {
    EXACT_HASH,
    TRIGRAM,
    PHONETIC
}
