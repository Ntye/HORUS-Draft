package horus.matching;

import horus.domain.shared.DecisionBand;
import horus.domain.shared.EntityId;
import horus.domain.shared.EntityVersionId;
import java.util.List;
import java.util.Optional;

// I-3: every score is traceable to stated rules and weights and reconstructible by hand. This
// record holds every feature score with its weight, every attribute effect with its reason, and
// any cap or floor applied -- enough for recompute() to re-derive the stored score with no access
// to the engine. It is stored whole (schema-versioned) and never recomputed at reconstruction
// time (CLAUDE.md §5). It deliberately holds no query name (I-11).
public record MatchExplanation(
        String schemaVersion,
        String strategy,
        EntityId candidateEntityId,
        EntityVersionId candidateEntityVersionId,
        String candidateSourceId,
        String matchedName,
        boolean matchedNameIsAlias,
        List<FeatureScore> features,
        double nameScore,
        List<AttributeEffect> effects,
        double attributeDelta,
        Optional<Double> cap,
        Optional<Double> floor,
        int compositeScore,
        int alertThreshold,
        int strongMatchThreshold,
        DecisionBand band,
        List<String> sourcesConsulted,
        List<String> capabilityGaps,
        List<String> notes) {

    public static final String SCHEMA_VERSION = "1";

    public record FeatureScore(String measureId, double weight, double score) {
    }

    public MatchExplanation {
        features = List.copyOf(features);
        effects = List.copyOf(effects);
        sourcesConsulted = List.copyOf(sourcesConsulted);
        capabilityGaps = List.copyOf(capabilityGaps);
        notes = List.copyOf(notes);
    }

    // The single definition of the name score: sum(w_i * s_i) / sum(w_i), summed in list order.
    public static double nameScore(List<FeatureScore> features) {
        double weighted = 0.0;
        double total = 0.0;
        for (FeatureScore f : features) {
            weighted += f.weight() * f.score();
            total += f.weight();
        }
        return total > 0.0 ? weighted / total : 0.0;
    }

    // The single definition of the composite: clamp(0, 100, name*100 + delta), then the cap, then
    // the floor (an exact identifier wins over a distinctiveness cap).
    public static int composite(
            double nameScore, List<AttributeEffect> effects, Optional<Double> cap, Optional<Double> floor) {
        double delta = 0.0;
        for (AttributeEffect e : effects) {
            delta += e.delta();
        }
        double raw = Math.max(0.0, Math.min(100.0, nameScore * 100.0 + delta));
        if (cap.isPresent()) {
            raw = Math.min(raw, cap.get());
        }
        if (floor.isPresent()) {
            raw = Math.max(raw, floor.get());
        }
        return (int) Math.round(Math.max(0.0, Math.min(100.0, raw)));
    }

    public static double delta(List<AttributeEffect> effects) {
        double delta = 0.0;
        for (AttributeEffect e : effects) {
            delta += e.delta();
        }
        return delta;
    }

    // I-3: re-derives the composite from the stored parts only.
    public int recompute() {
        return composite(nameScore(features), effects, cap, floor);
    }

    public DecisionBand recomputeBand() {
        int score = recompute();
        if (score >= strongMatchThreshold) {
            return DecisionBand.STRONG_MATCH;
        }
        return score >= alertThreshold ? DecisionBand.POSSIBLE_MATCH : DecisionBand.NO_MATCH;
    }

    // Hand-rolled so the output is byte-stable (I-4) and the core needs no JSON library: fixed key
    // order, lists in stored order, doubles via Double.toString.
    public String toCanonicalJson() {
        StringBuilder json = new StringBuilder(512);
        json.append('{');
        field(json, "schemaVersion", schemaVersion).append(',');
        field(json, "strategy", strategy).append(',');
        field(json, "candidateEntityId", candidateEntityId.value().toString()).append(',');
        field(json, "candidateEntityVersionId", candidateEntityVersionId.value().toString()).append(',');
        field(json, "candidateSourceId", candidateSourceId).append(',');
        field(json, "matchedName", matchedName).append(',');
        json.append("\"matchedNameIsAlias\":").append(matchedNameIsAlias).append(',');
        json.append("\"features\":[");
        for (int i = 0; i < features.size(); i++) {
            FeatureScore f = features.get(i);
            json.append(i > 0 ? "," : "").append("{\"measureId\":");
            quote(json, f.measureId()).append(",\"weight\":").append(f.weight())
                    .append(",\"score\":").append(f.score()).append('}');
        }
        json.append("],\"nameScore\":").append(nameScore).append(",\"effects\":[");
        for (int i = 0; i < effects.size(); i++) {
            AttributeEffect e = effects.get(i);
            json.append(i > 0 ? "," : "").append("{\"attribute\":");
            quote(json, e.attribute()).append(",\"delta\":").append(e.delta()).append(",\"reason\":");
            quote(json, e.reason()).append(",\"forcesStrongMatch\":").append(e.forcesStrongMatch());
            e.provenance().ifPresent(p -> {
                json.append(",\"provenance\":{\"ruleId\":");
                quote(json, p.ruleId()).append(",\"start\":").append(p.start()).append(",\"end\":")
                        .append(p.end()).append('}');
            });
            json.append('}');
        }
        json.append("],\"attributeDelta\":").append(attributeDelta);
        cap.ifPresent(c -> json.append(",\"cap\":").append(c));
        floor.ifPresent(f -> json.append(",\"floor\":").append(f));
        json.append(",\"compositeScore\":").append(compositeScore)
                .append(",\"alertThreshold\":").append(alertThreshold)
                .append(",\"strongMatchThreshold\":").append(strongMatchThreshold)
                .append(",\"band\":\"").append(band.name()).append("\",");
        list(json, "sourcesConsulted", sourcesConsulted).append(',');
        list(json, "capabilityGaps", capabilityGaps).append(',');
        list(json, "notes", notes);
        return json.append('}').toString();
    }

    private static StringBuilder field(StringBuilder json, String key, String value) {
        quote(json, key).append(':');
        return quote(json, value);
    }

    private static StringBuilder list(StringBuilder json, String key, List<String> values) {
        quote(json, key).append(":[");
        for (int i = 0; i < values.size(); i++) {
            json.append(i > 0 ? "," : "");
            quote(json, values.get(i));
        }
        return json.append(']');
    }

    private static StringBuilder quote(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        return json.append('"');
    }
}
