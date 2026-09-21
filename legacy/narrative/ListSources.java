package horus.narrative;

import java.util.Locale;

/**
 * Canonicalises a section header into a list-source identifier.
 *
 * watch_entity.list_sources[] has no structured source field in the feed; the
 * attribution exists only as these headers. Roughly 90 of the 117 observed
 * headers are list sources, and they follow a consistent shape:
 * "JURISDICTION SANCTIONS - PROGRAMME".
 */
public final class ListSources {

    private ListSources() {}

    /** e.g. "USA SANCTIONS - OFAC" -> "USA:OFAC"; "EU SANCTIONS" -> "EU". */
    public static String canonical(String header) {
        if (header == null || header.isBlank()) return null;
        String h = header.trim().toUpperCase(Locale.ROOT);

        int dash = h.indexOf(" - ");
        String jurisdiction = dash >= 0 ? h.substring(0, dash).trim() : h;
        String programme = dash >= 0 ? h.substring(dash + 3).trim() : null;

        jurisdiction = jurisdiction
                .replace(" SANCTIONS", "")
                .replace(" REGULATIONS", "")
                .replace(" EMBARGO", "")
                .trim();

        if (jurisdiction.isEmpty()) return programme;
        return programme == null || programme.isEmpty()
                ? jurisdiction : jurisdiction + ":" + programme;
    }

    /** True when the header names a sanctions authority rather than a topic. */
    public static boolean isListSource(String header) {
        if (header == null) return false;
        return header.contains("SANCTIONS") || header.contains("EMBARGO")
                || header.contains("REGULATIONS");
    }

    /** "SANCTIONS HISTORY" aggregates cross-source events in one block. */
    public static boolean isHistoryBlock(String header) {
        return "SANCTIONS HISTORY".equals(header)
                || "REGULATIONS HISTORY".equals(header);
    }
}
