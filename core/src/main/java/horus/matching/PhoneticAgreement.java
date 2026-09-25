package horus.matching;

import horus.normalisation.NormalisedName;
import java.util.ArrayList;
import java.util.List;

// Double Metaphone agreement per token (Mohammed / Muhammad), weighted like the token overlap,
// expanding an initial's code to a longer code that starts with it ("J" ~ "JN"), and taking the
// best of the particle views of the two names (see NameVariants). Tokens with no phonetic code
// (digits) contribute nothing; two names with no codes score 0.
public final class PhoneticAgreement implements SimilarityMeasure {

    @Override
    public String id() {
        return "phonetic-agreement";
    }

    @Override
    public double score(NormalisedName query, NormalisedName candidate, TokenWeighting weighting) {
        return NameVariants.best(query, candidate,
                (a, b) -> WeightedOverlap.jaccard(items(a, weighting), items(b, weighting), true));
    }

    private static List<WeightedOverlap.Item> items(NameVariants.Variant variant, TokenWeighting weighting) {
        List<WeightedOverlap.Item> items = new ArrayList<>();
        for (int i = 0; i < variant.tokens().size() && i < variant.phoneticCodes().size(); i++) {
            String code = variant.phoneticCodes().get(i);
            if (code != null && !code.isEmpty()) {
                items.add(new WeightedOverlap.Item(code, weighting.weight(variant.tokens().get(i))));
            }
        }
        return items;
    }
}
