package horus.normalisation;

import java.util.List;

// Step 10 of 12 (§15.2): token sorting into a canonical form -- retained ALONGSIDE, not instead
// of, the ordered form from step 9.
public final class TokenSorter {

    public List<String> sort(List<String> tokens) {
        return tokens.stream().sorted().toList();
    }
}
