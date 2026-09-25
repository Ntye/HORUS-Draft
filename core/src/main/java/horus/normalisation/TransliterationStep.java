package horus.normalisation;

import com.ibm.icu.text.Transliterator;

// Step 3 of 12 (§15.2): transliterates non-Latin scripts to Latin/ASCII.
//
// CJK and other logographic scripts are a documented limitation, not a handled case (§15.2
// WARNINGS): ICU4J's Any-Latin rules produce *something* for Han/Hangul/Kana input, but that
// output's usefulness for name matching has not been verified or tuned. Homoglyph substitution
// (e.g. digit "0" for letter "o") is a separate, undocumented-by-design gap -- no step in this
// pipeline performs confusable-character detection.
public final class TransliterationStep implements NormalisationStep {

    private static final Transliterator TRANSLITERATOR = Transliterator.getInstance("Any-Latin; Latin-ASCII");

    @Override
    public String stepId() {
        return "transliteration";
    }

    @Override
    public String apply(String input) {
        return TRANSLITERATOR.transliterate(input);
    }
}
