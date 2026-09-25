package horus.adapter.source.worldcheck.narrative;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives designation status per list source, then for the record as a whole.
 *
 * "Mentions removal" is NOT "is de-listed". A record can be removed from the
 * Australian list while remaining designated by OFAC. Status is per source; the
 * record-level answer is derived from the set.
 *
 * CONSERVATIVE BY CONSTRUCTION. Under I-2 a false negative is a regulatory
 * breach and a false positive is an operational cost, so every ambiguity
 * resolves toward LIVE. A source counts as removed only when a removal is
 * unambiguously later than any addition, or when a removal appears with no
 * addition at all.
 *
 * The output DEMOTES AND EXPLAINS. It must never be used to suppress a
 * candidate (I-8): a parser bug that reads a live designation as removed would
 * otherwise become a silent recall failure.
 */
public final class StatusDeriver {

    private StatusDeriver() {}

    public enum RecordStatus {
        /** No sanctions section at all. 96.3% of records. */
        NOT_SANCTIONED,
        /** At least one source shows a designation still in force. */
        LIVE,
        /** Every source named shows a later removal. */
        REMOVED_EVERYWHERE,
        /** Sanctions content present but not classifiable. Treat as LIVE. */
        INDETERMINATE
    }

    public record SourceStatus(
            String listSource,
            boolean removed,
            Integer lastAdditionYear,
            Integer lastRemovalYear,
            int unclassifiedEvents,
            boolean hasAuthorisation) {}

    public record Result(
            RecordStatus status,
            List<SourceStatus> perSource,
            int unclassifiedEvents,
            String rationale) {}

    public static Result derive(Facts facts) {
        // src -> {lastAdd, lastRem, unclassified, hasAuthorisation}
        Map<String, int[]> agg = new HashMap<>();
        int unclassified = 0;

        for (Facts.SanctionEvent e : facts.events()) {
            String src = e.listSource() == null ? "(unattributed)" : e.listSource();
            int[] a = agg.computeIfAbsent(src, k -> new int[]{-1, -1, 0, 0});
            switch (e.type()) {
                case ADDITION -> { if (e.year() > a[0]) a[0] = e.year(); }
                case REMOVAL  -> { if (e.year() > a[1]) a[1] = e.year(); }
                case AMENDMENT -> { if (e.year() > a[0]) a[0] = e.year(); }
                case WARNING -> { /* not a designation; ignore for status */ }
                case AUTHORISATION -> {
                    // A general licence authorises transactions DESPITE a
                    // designation, so it is evidence the listing is live. It is
                    // never a lifecycle event and never moves a date.
                    a[3] = 1;
                }
                case UNCLASSIFIED -> { a[2]++; unclassified++; }
            }
        }

        if (agg.isEmpty()) {
            return new Result(RecordStatus.NOT_SANCTIONED, List.of(), 0,
                    "no sanctions section present");
        }

        List<SourceStatus> per = new ArrayList<>(agg.size());
        int live = 0, removed = 0, unknown = 0;
        for (Map.Entry<String, int[]> e : agg.entrySet()) {
            int add = e.getValue()[0], rem = e.getValue()[1], unk = e.getValue()[2];
            boolean auth = e.getValue()[3] == 1;
            boolean isRemoved = rem >= 0 && (add < 0 || rem > add);
            per.add(new SourceStatus(e.getKey(), isRemoved,
                    add < 0 ? null : add, rem < 0 ? null : rem, unk, auth));
            if (isRemoved) removed++;
            else if (add >= 0 || auth) live++;   // a licence implies a live listing
            else unknown++;
        }

        RecordStatus status;
        String why;
        if (live > 0) {
            status = RecordStatus.LIVE;
            why = live + " source(s) designated with no later removal";
        } else if (removed > 0 && unknown == 0) {
            status = RecordStatus.REMOVED_EVERYWHERE;
            why = "all " + removed + " source(s) show a removal later than any addition";
        } else {
            status = RecordStatus.INDETERMINATE;
            why = "sanctions content present but unclassifiable; treated as LIVE (I-2)";
        }
        return new Result(status, per, unclassified, why);
    }

    /**
     * The only safe question a consumer may ask. INDETERMINATE answers true:
     * ambiguity never removes a candidate from consideration.
     */
    public static boolean mayStillBeDesignated(Result r) {
        return r.status() != RecordStatus.REMOVED_EVERYWHERE;
    }
}
