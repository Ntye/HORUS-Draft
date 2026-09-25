package horus.domain.watchentity;

// Kept identical to legacy/narrative/ActionVocabulary.ActionType so Step 4's migration of that
// parser into horus.adapter.source.worldcheck.narrative can reference this domain type directly.
public enum ActionType {
    ADDITION,
    REMOVAL,
    AMENDMENT,
    WARNING,
    AUTHORISATION,
    UNCLASSIFIED
}
