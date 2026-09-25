package horus.normalisation;

// Step 8 of 12 (§15.2): connector-word handling. Spells "&" out as "and"; particles (al, bin,
// ibn, de, van, ...) are already consistently lowercase from step 1 and are left untouched here
// -- stripping them is exactly the over-normalisation §15.2's WARNINGS section rules out.
public final class ConnectorWordNormalisationStep implements NormalisationStep {

    @Override
    public String stepId() {
        return "connector-word-normalisation";
    }

    @Override
    public String apply(String input) {
        return input.replace("&", "and");
    }
}
