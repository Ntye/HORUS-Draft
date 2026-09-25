package horus.ingestion.format;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// «absent ≠ empty» (horus-ingestion-spi.drawio). isAbsent() answers a narrower question than
// "is the value missing": it is true only when the source asserted absence explicitly (e.g.
// xsi:nil="true"), recorded by the FormatReader under the "<path>@nil" attribute key.
public record RawRecord(
        long ordinal,
        Map<String, List<String>> paths,
        Map<String, String> attributes,
        String sourceRef) {

    public RawRecord {
        if (paths == null) {
            throw new IllegalArgumentException("paths must not be null");
        }
        if (attributes == null) {
            throw new IllegalArgumentException("attributes must not be null");
        }
        if (sourceRef == null || sourceRef.isBlank()) {
            throw new IllegalArgumentException("sourceRef must not be blank");
        }
        paths = paths.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
        attributes = Map.copyOf(attributes);
    }

    public List<String> get(String path) {
        return paths.getOrDefault(path, List.of());
    }

    public boolean isAbsent(String path) {
        return "true".equals(attributes.get(path + "@nil"));
    }
}
