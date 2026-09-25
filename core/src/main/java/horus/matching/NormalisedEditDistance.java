package horus.matching;

import horus.normalisation.NormalisedName;
import org.apache.commons.text.similarity.LevenshteinDistance;

// 1 - Levenshtein / max length over the name with spaces removed, so "Al-Sayed" and "Alsayed"
// collapse (§15.2) while "Ali" and "Alia" stay one whole edit apart. Scored on both the ordered
// and the token-sorted form and keeping the better, so a transposition plus a reordering
// ("Smtih John" / "John Smith") is still close. Best of the particle views (see NameVariants).
public final class NormalisedEditDistance implements SimilarityMeasure {

    @Override
    public String id() {
        return "normalised-edit-distance";
    }

    @Override
    public double score(NormalisedName query, NormalisedName candidate, TokenWeighting weighting) {
        return NameVariants.best(query, candidate, (a, b) -> Math.max(
                similarity(String.join("", a.tokens()), String.join("", b.tokens())),
                similarity(String.join("", a.sortedTokens()), String.join("", b.sortedTokens()))));
    }

    private static double similarity(String a, String b) {
        int longest = Math.max(a.length(), b.length());
        if (longest == 0) {
            return 0.0;
        }
        return 1.0 - (LevenshteinDistance.getDefaultInstance().apply(a, b) / (double) longest);
    }
}
