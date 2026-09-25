package horus.matching;

import horus.normalisation.NormalisedName;
import java.util.List;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;

// Order-invariant prefix-weighted similarity (§15.3). For organisations the generic descriptors
// are dropped first, so the measure judges the distinctive core -- unless that would leave
// nothing, in which case the whole name is compared rather than an empty string. The best of the
// particle views of the two names is used (see NameVariants).
public final class TokenSortJaroWinkler implements SimilarityMeasure {

    private final JaroWinklerSimilarity jaroWinkler = new JaroWinklerSimilarity();

    @Override
    public String id() {
        return "token-sort-jaro-winkler";
    }

    @Override
    public double score(NormalisedName query, NormalisedName candidate, TokenWeighting weighting) {
        return NameVariants.best(query, candidate, (a, b) -> {
            String x = core(a.sortedTokens(), weighting);
            String y = core(b.sortedTokens(), weighting);
            return x.isEmpty() || y.isEmpty() ? 0.0 : jaroWinkler.apply(x, y);
        });
    }

    private static String core(List<String> sortedTokens, TokenWeighting weighting) {
        List<String> distinctive = sortedTokens.stream().filter(t -> !weighting.isGeneric(t)).toList();
        return String.join(" ", distinctive.isEmpty() ? sortedTokens : distinctive);
    }
}
