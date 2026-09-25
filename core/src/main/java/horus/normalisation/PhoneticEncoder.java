package horus.normalisation;

import java.util.List;
import org.apache.commons.codec.language.DoubleMetaphone;

// Step 11 of 12 (§15.2): phonetic encoding per token.
public final class PhoneticEncoder {

    private final DoubleMetaphone doubleMetaphone = new DoubleMetaphone();

    public List<String> encode(List<String> tokens) {
        return tokens.stream().map(doubleMetaphone::encode).toList();
    }
}
