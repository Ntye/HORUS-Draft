package horus.narrative;

/**
 * Where an extracted value came from, and which rule produced it.
 *
 * Mandatory on every derived value. I-3 requires that an analyst can read an
 * explanation and reconstruct it by hand; for a value lifted out of free text
 * that means being able to jump to the exact substring and see it. Without this
 * a narrative-derived score contribution is unexplainable.
 *
 * @param ruleId stable identifier of the rule that fired, e.g. "OFSI.GROUP_ID"
 * @param start  character offset into further_information, inclusive
 * @param end    character offset into further_information, exclusive
 */
public record Provenance(String ruleId, int start, int end) {

    public Provenance {
        if (ruleId == null || ruleId.isBlank())
            throw new IllegalArgumentException("ruleId is mandatory");
        if (start < 0 || end < start)
            throw new IllegalArgumentException("bad offsets: " + start + ".." + end);
    }

    /** Shifts offsets from section-local to document-absolute. */
    public Provenance shift(int by) {
        return new Provenance(ruleId, start + by, end + by);
    }

    @Override
    public String toString() { return ruleId + "@" + start + ":" + end; }
}
