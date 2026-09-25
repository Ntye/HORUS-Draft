package horus.matching;

import horus.normalisation.NormalisedName;
import horus.normalisation.PhoneticEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Comparison-time views of one name, because particles are written inconsistently ("Al-Sayed",
// "Al Sayed", "Alsayed"; "Ahmed Bin Yousef" vs "Ahmed Yousef"). The normalisation pipeline leaves
// particles alone on purpose (§15.2: stripping them is over-normalisation), so the matcher tries
// three views of BOTH names and a measure takes the best of them:
//   RAW      the tokens as normalised
//   MERGED   each particle joined to the token after it ("al sayed" -> "alsayed")
//   DROPPED  particles removed (never leaving the name empty)
// and, only when either name was transliterated from a non-Latin script, a SKELETON of each view:
// vowels removed and doubled letters collapsed. Unvocalised scripts (Arabic, Hebrew) transliterate
// without vowels ("mhmd alsyd"), so vowel-sensitive measures cannot align them with "Mohammed Al
// Sayed"; consonant skeletons can. It is not applied to Latin-vs-Latin names, where it would
// collapse "Ali" and "Alia" (§15.2 over-normalisation guard).
// A measure scores every pairing of a view of one name with a view of the other and keeps the
// best. The max over a cross product is symmetric, and it resolves ambiguity toward keeping the
// candidate (I-2). Deterministic (I-4).
final class NameVariants {

    static final Set<String> PARTICLES = Set.of(
            "al", "el", "bin", "ibn", "bint", "abu", "ben", "de", "del", "della", "di", "da", "dos", "van", "von",
            "der", "den", "le", "la", "du");

    private static final PhoneticEncoder PHONETIC = new PhoneticEncoder();

    record Variant(List<String> tokens, List<String> sortedTokens, List<String> phoneticCodes) {
    }

    private NameVariants() {
    }

    static double best(
            NormalisedName query,
            NormalisedName candidate,
            java.util.function.ToDoubleBiFunction<Variant, Variant> measure) {
        boolean skeletons = isTransliterated(query) || isTransliterated(candidate);
        double best = 0.0;
        for (Variant a : of(query, skeletons)) {
            for (Variant b : of(candidate, skeletons)) {
                best = Math.max(best, measure.applyAsDouble(a, b));
            }
        }
        return best;
    }

    static boolean isTransliterated(NormalisedName name) {
        return !name.script().equals("LATIN") && !name.script().equals("COMMON");
    }

    static List<Variant> of(NormalisedName name, boolean withSkeletons) {
        List<Variant> variants = new ArrayList<>(6);
        variants.add(new Variant(name.tokens(), name.sortedTokens(), name.phoneticCodes()));
        List<String> merged = merge(name.tokens());
        if (!merged.equals(name.tokens())) {
            variants.add(build(merged));
        }
        List<String> dropped = drop(name.tokens());
        if (!dropped.equals(name.tokens())) {
            variants.add(build(dropped));
        }
        if (withSkeletons) {
            List<Variant> skeletons = new ArrayList<>();
            for (Variant v : variants) {
                List<String> skeleton = v.tokens().stream().map(NameVariants::skeleton).toList();
                // The skeleton is its own phonetic key: it already is the consonant form.
                skeletons.add(new Variant(skeleton, skeleton.stream().sorted().toList(), skeleton));
            }
            variants.addAll(skeletons);
        }
        return variants;
    }

    private static String skeleton(String token) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if ("aeiouy".indexOf(c) >= 0) {
                continue;
            }
            if (out.length() == 0 || out.charAt(out.length() - 1) != c) {
                out.append(c);
            }
        }
        // A token made only of vowels keeps its own form rather than vanishing.
        return out.length() == 0 ? token : out.toString();
    }

    private static Variant build(List<String> tokens) {
        return new Variant(tokens, tokens.stream().sorted().toList(), PHONETIC.encode(tokens));
    }

    private static List<String> merge(List<String> tokens) {
        List<String> merged = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (PARTICLES.contains(token) && i + 1 < tokens.size()) {
                merged.add(token + tokens.get(i + 1));
                i++;
            } else {
                merged.add(token);
            }
        }
        return merged;
    }

    private static List<String> drop(List<String> tokens) {
        List<String> kept = tokens.stream().filter(t -> !PARTICLES.contains(t)).toList();
        return kept.isEmpty() ? tokens : kept;
    }
}
