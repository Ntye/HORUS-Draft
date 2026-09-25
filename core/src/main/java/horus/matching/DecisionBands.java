package horus.matching;

import horus.domain.shared.DecisionBand;

// §16.3. ERROR is never produced here: it means the screening could not be completed, which is
// decided upstream (I-1). A score can only ever be NO_MATCH, POSSIBLE_MATCH or STRONG_MATCH.
public final class DecisionBands {

    private DecisionBands() {
    }

    public static DecisionBand classify(int compositeScore, MatchConfig config) {
        if (compositeScore >= config.strongMatchThreshold()) {
            return DecisionBand.STRONG_MATCH;
        }
        if (compositeScore >= config.alertThreshold()) {
            return DecisionBand.POSSIBLE_MATCH;
        }
        return DecisionBand.NO_MATCH;
    }
}
