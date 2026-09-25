package horus.matching;

import horus.normalisation.NormalisedName;

// One explainable feature of the name score. Pure and deterministic (I-3, I-4): the same two
// names and weighting always give the same value in [0, 1], and score(a, b) equals score(b, a).
public interface SimilarityMeasure {

    String id();

    double score(NormalisedName query, NormalisedName candidate, TokenWeighting weighting);
}
