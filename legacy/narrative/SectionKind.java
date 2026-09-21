package horus.narrative;

/**
 * What kind of content a bracketed section holds.
 *
 * The split matters because the formal register is templated and parseable
 * while the prose register is not: measurement over the whole feed found 858
 * distinct verb forms in formal sections against 14,308 in prose. Parsing prose
 * for facts would be guesswork, and guesswork has no place in a value that
 * reaches a screening explanation (I-3).
 */
public enum SectionKind {
    /** Sanctions designations, with a (source, action, date) grammar. */
    SANCTIONS,
    /** Regulator warnings: FCA, BaFin, CONSOB. Never a designation. */
    REGULATORY_WARNING,
    /** Ownership and control. Carries the 50 Percent Rule data. */
    OWNERSHIP,
    /** Law enforcement notices, debarment, wanted lists. */
    LAW_ENFORCEMENT,
    /** Section whose whole content is an identifier: IMO REGISTRATION, SWIFT BIC. */
    IDENTIFIER,
    /** Vessel and aircraft operating status: OPERATIONAL, DETAINED VESSEL. */
    ASSET_STATUS,
    /** Free prose. Never parsed for facts. */
    PROSE,
    /** A header, but unclassified. Quarantined, never dropped. */
    UNKNOWN
}
