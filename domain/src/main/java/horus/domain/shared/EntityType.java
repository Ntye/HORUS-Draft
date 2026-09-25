package horus.domain.shared;

// I-13: canonical type is a function of (@e-i, @category), not @category alone -- see CLAUDE.md §6.
public enum EntityType {
    INDIVIDUAL,
    ORGANISATION,
    VESSEL,
    AIRCRAFT,
    WEBSITE,
    PORT,
    COUNTRY,
    ADDRESS
}
