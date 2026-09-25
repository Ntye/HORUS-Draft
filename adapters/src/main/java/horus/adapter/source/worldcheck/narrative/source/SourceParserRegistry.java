package horus.adapter.source.worldcheck.narrative.source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a section header to the parser that owns it.
 *
 * Same pattern as the matching engine's Registry: implementations are
 * registered once at startup and resolved by identifier, so adding an authority
 * is a registration rather than an edit to the orchestrator.
 *
 * Order matters -- the first parser whose handles() returns true wins -- so
 * specific authorities are registered before the generic fallback.
 */
public final class SourceParserRegistry {

    private final List<SourceParser> parsers = new ArrayList<>();
    private final SourceParser fallback;
    private final Map<String, Long> resolutionCounts = new LinkedHashMap<>();

    private SourceParserRegistry(List<SourceParser> specific, SourceParser fallback) {
        this.parsers.addAll(specific);
        this.fallback = fallback;
        for (SourceParser p : specific) resolutionCounts.put(p.parserId(), 0L);
        resolutionCounts.put(fallback.parserId(), 0L);
    }

    /** The registry as shipped. Extend here, not in NarrativeParser. */
    public static SourceParserRegistry standard() {
        return new SourceParserRegistry(
                List.of(new OfsiSourceParser(),
                        new OfacSourceParser(),
                        new EuSourceParser()),
                new GenericSanctionsParser());
    }

    public static SourceParserRegistry of(List<SourceParser> specific, SourceParser fallback) {
        return new SourceParserRegistry(specific, fallback);
    }

    public SourceParser resolve(String sectionHeader) {
        for (SourceParser p : parsers) {
            if (p.handles(sectionHeader)) {
                resolutionCounts.merge(p.parserId(), 1L, Long::sum);
                return p;
            }
        }
        resolutionCounts.merge(fallback.parserId(), 1L, Long::sum);
        return fallback;
    }

    /**
     * How often each parser fired. A fallback share that stays high is the
     * signal that another authority-specific parser is worth writing.
     */
    public Map<String, Long> resolutionCounts() {
        return new LinkedHashMap<>(resolutionCounts);
    }

    public List<String> parserIds() {
        List<String> ids = new ArrayList<>();
        for (SourceParser p : parsers) ids.add(p.parserId());
        ids.add(fallback.parserId());
        return ids;
    }
}
