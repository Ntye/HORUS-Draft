package horus.normalisation;

import java.util.List;

// The one normalisation pipeline (I-10): imported identically by ingestion and by screening.
// No entity-type branching -- every step either applies uniformly or is a harmless no-op when
// its pattern doesn't match, which keeps the same raw input always producing the same result
// regardless of a type flag that might be wrong or absent at query time (I-4).
public final class NormalisationPipeline {

    private static final String STANDARD_PIPELINE_VERSION = "1.0.0";

    private final String pipelineVersion;
    private final List<NormalisationStep> steps;
    private final Tokeniser tokeniser = new Tokeniser();
    private final TokenSorter tokenSorter = new TokenSorter();
    private final PhoneticEncoder phoneticEncoder = new PhoneticEncoder();
    private final TrigramGenerator trigramGenerator = new TrigramGenerator();

    public NormalisationPipeline(String pipelineVersion, List<NormalisationStep> steps) {
        if (pipelineVersion == null || pipelineVersion.isBlank()) {
            throw new IllegalArgumentException("pipelineVersion must not be blank");
        }
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("steps must not be empty");
        }
        this.pipelineVersion = pipelineVersion;
        this.steps = List.copyOf(steps);
    }

    public static NormalisationPipeline standard() {
        return new NormalisationPipeline(STANDARD_PIPELINE_VERSION, List.of(
                new UnicodeNormalisationStep(),
                new DiacriticRemovalStep(),
                new TransliterationStep(),
                new PunctuationHandlingStep(),
                new WhitespaceCollapsingStep(),
                new HonorificRemovalStep(),
                new LegalFormNormalisationStep(),
                new ConnectorWordNormalisationStep()));
    }

    public String pipelineVersion() {
        return pipelineVersion;
    }

    public NormalisedName normalise(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("raw must not be null");
        }

        String script = detectScript(raw);

        String normalised = raw;
        for (NormalisationStep step : steps) {
            normalised = step.apply(normalised);
        }
        // Steps 5 and beyond can reintroduce whitespace runs (e.g. honorific removal); a final
        // collapse keeps the emitted normalised string and tokens consistent with each other.
        normalised = new WhitespaceCollapsingStep().apply(normalised);

        List<String> tokens = tokeniser.tokenise(normalised);
        List<String> sortedTokens = tokenSorter.sort(tokens);
        List<String> phoneticCodes = phoneticEncoder.encode(tokens);
        List<String> trigrams = trigramGenerator.generate(normalised);

        return new NormalisedName(raw, normalised, tokens, sortedTokens, phoneticCodes, trigrams, script);
    }

    private static String detectScript(String raw) {
        return raw.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .mapToObj(Character.UnicodeScript::of)
                .findFirst()
                .map(Enum::name)
                .orElse("COMMON");
    }
}
