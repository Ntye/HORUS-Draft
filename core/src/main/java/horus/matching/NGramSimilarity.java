package horus.matching;

import horus.normalisation.NormalisedName;
import java.util.HashSet;
import java.util.Set;

// Dice coefficient over the pipeline's own trigrams (step 12), so scoring and indexing use the
// same n-grams. Good on compound or concatenated names.
public final class NGramSimilarity implements SimilarityMeasure {

    @Override
    public String id() {
        return "ngram-dice";
    }

    @Override
    public double score(NormalisedName query, NormalisedName candidate, TokenWeighting weighting) {
        Set<String> a = new HashSet<>(query.trigrams());
        Set<String> b = new HashSet<>(candidate.trigrams());
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> shared = new HashSet<>(a);
        shared.retainAll(b);
        return 2.0 * shared.size() / (a.size() + b.size());
    }
}
