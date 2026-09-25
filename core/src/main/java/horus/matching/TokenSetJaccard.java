package horus.matching;

import horus.normalisation.NormalisedName;
import java.util.List;

// Set overlap: tolerant of word order and a missing middle name (§15.3), weighted by token
// distinctiveness, expanding an initial to a token starting with it ("J." ~ "John"), and taking
// the best of the particle views of the two names (see NameVariants).
public final class TokenSetJaccard implements SimilarityMeasure {

    @Override
    public String id() {
        return "token-set-jaccard";
    }

    @Override
    public double score(NormalisedName query, NormalisedName candidate, TokenWeighting weighting) {
        return NameVariants.best(query, candidate,
                (a, b) -> WeightedOverlap.jaccard(items(a.tokens(), weighting), items(b.tokens(), weighting), true));
    }

    private static List<WeightedOverlap.Item> items(List<String> tokens, TokenWeighting weighting) {
        return tokens.stream().map(t -> new WeightedOverlap.Item(t, weighting.weight(t))).toList();
    }
}
