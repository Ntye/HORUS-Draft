package horus.matching;

// How much one token counts. Individuals: every token carries signal. Organisations: generic
// descriptors (trading, group, ltd ...) are down-weighted so distinctive tokens carry the score
// (spec §15.4).
public interface TokenWeighting {

    double weight(String token);

    boolean isGeneric(String token);

    static TokenWeighting uniform() {
        return new TokenWeighting() {
            @Override
            public double weight(String token) {
                return 1.0;
            }

            @Override
            public boolean isGeneric(String token) {
                return false;
            }
        };
    }
}
