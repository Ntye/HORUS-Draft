package horus.ingestion.spi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

// I-4/I-6: a WatchEntityVersion is only ever created when this hash changes (Step 5's
// ReconcileVersion). Each list is rendered independently sorted so incidental reordering by the
// source file (not a real content change) never produces a spurious new version.
public final class ContentHasher {

    private ContentHasher() {
    }

    public static String hash(CanonicalRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        StringBuilder canonical = new StringBuilder();
        canonical.append("entityType=").append(record.entityType()).append('\n');
        canonical.append("gender=").append(record.gender().map(Enum::name).orElse("")).append('\n');
        appendSorted(canonical, "name", record.names().stream()
                .map(n -> n.type() + ":" + n.value())
                .toList());
        appendSorted(canonical, "dob", record.dobs().stream().map(ContentHasher::dobKey).toList());
        appendSorted(canonical, "country", record.countries().stream()
                .map(CanonicalCountry::rawCountry)
                .toList());
        appendSorted(canonical, "address", record.addresses().stream()
                .map(ContentHasher::addressKey)
                .toList());
        appendSorted(canonical, "identifier", record.identifiers().stream()
                .map(i -> i.idType() + ":" + i.idValue())
                .toList());
        appendSorted(canonical, "designation", record.designations().stream()
                .map(d -> d.listSource() + ":" + d.action() + ":" + d.year() + ":" + d.rawVerb())
                .toList());
        appendSorted(canonical, "ownership", record.ownership().stream()
                .map(o -> o.owner() + ":" + o.ownerType() + ":" + o.percent())
                .toList());

        return "sha256:" + HexFormat.of().formatHex(sha256(canonical.toString()));
    }

    private static void appendSorted(StringBuilder canonical, String label, List<String> values) {
        canonical.append(label).append('=');
        values.stream().sorted().forEach(v -> canonical.append(v).append(';'));
        canonical.append('\n');
    }

    private static String dobKey(CanonicalDob dob) {
        return dob.year().map(String::valueOf).orElse("") + ":"
                + dob.month().map(String::valueOf).orElse("") + ":"
                + dob.day().map(String::valueOf).orElse("") + ":"
                + dob.age().map(String::valueOf).orElse("") + ":"
                + dob.asOfDate().map(Object::toString).orElse("") + ":"
                + dob.deceased().map(Object::toString).orElse("");
    }

    private static String addressKey(CanonicalAddress address) {
        return (address.rawCountry() == null ? "" : address.rawCountry()) + ":"
                + (address.city() == null ? "" : address.city()) + ":"
                + (address.state() == null ? "" : address.state());
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required to be available on every JVM", e);
        }
    }
}
