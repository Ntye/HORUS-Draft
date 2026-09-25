package horus.domain.shared;

public record Provenance(String ruleId, int start, int end) {

    public Provenance {
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId must not be blank");
        }
        if (start < 0) {
            throw new IllegalArgumentException("start must not be negative");
        }
        if (end < start) {
            throw new IllegalArgumentException("end must not be before start");
        }
    }

    public Provenance shift(int by) {
        return new Provenance(ruleId, start + by, end + by);
    }
}
