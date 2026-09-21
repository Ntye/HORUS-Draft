package horus.narrative;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything extracted from one record's further_information, with provenance
 * on every value.
 *
 * These are FACTS, not decisions. Nothing here suppresses a candidate. Status
 * derived from this data demotes and explains; it never eliminates (I-8).
 */
public record Facts(
        List<String> listSources,
        List<SanctionEvent> events,
        List<OwnershipStake> ownership,
        List<Identifier> identifiers,
        List<Name> names,
        List<Unparsed> unparsed,
        int sectionsSeen,
        int sectionsParsed) {

    /** One (source, action, date) triple. Status is derived from these. */
    public record SanctionEvent(
            String listSource,
            ActionVocabulary.ActionType type,
            String rawVerb,
            int year,
            Provenance provenance) {}

    /** One shareholder line. percent is null when "unknown percentage". */
    public record OwnershipStake(
            String owner,
            String ownerType,      // SOE, IOS, or null
            Double percent,
            boolean percentStated,
            String relation,       // section header: DIRECT SHAREHOLDER/S etc
            Provenance provenance) {}

    public record Identifier(
            String type,           // TAX_ID, LEI, SWIFT_BIC, IMO, GROUP_ID, REF_NO...
            String value,
            String issuingContext, // list source the identifier was stated under
            Provenance provenance) {}

    public record Name(
            String value,
            String nameType,       // PRIMARY, AKA, LOW_QUALITY_AKA, SPELLING_VARIANT
            String sourceFormat,   // which per-source parser produced it
            Provenance provenance) {}

    /**
     * Recognised structure that no rule handled. Quarantined with a line
     * reference, never silently discarded (spec 13.1 stage 5).
     */
    public record Unparsed(
            String sectionHeader,
            String reason,
            String excerpt,
            Provenance provenance) {}

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Set<String> sources = new LinkedHashSet<>();
        private final List<SanctionEvent> events = new ArrayList<>();
        private final List<OwnershipStake> ownership = new ArrayList<>();
        private final List<Identifier> identifiers = new ArrayList<>();
        private final List<Name> names = new ArrayList<>();
        private final List<Unparsed> unparsed = new ArrayList<>();
        private int seen, parsed;

        public Builder source(String s) {
            if (s != null && !s.isBlank()) sources.add(s);
            return this;
        }
        public Builder event(SanctionEvent e) { events.add(e); return this; }
        public Builder stake(OwnershipStake o) { ownership.add(o); return this; }
        public Builder identifier(Identifier i) { identifiers.add(i); return this; }
        public Builder name(Name n) { names.add(n); return this; }
        public Builder unparsed(Unparsed u) { unparsed.add(u); return this; }
        public Builder sectionSeen() { seen++; return this; }
        public Builder sectionParsed() { parsed++; return this; }

        public Facts build() {
            return new Facts(List.copyOf(sources), List.copyOf(events),
                    List.copyOf(ownership), List.copyOf(identifiers),
                    List.copyOf(names), List.copyOf(unparsed), seen, parsed);
        }
    }
}
