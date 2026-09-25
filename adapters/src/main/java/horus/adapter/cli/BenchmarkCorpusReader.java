package horus.adapter.cli;

import horus.domain.shared.EntityType;
import horus.matching.PartialDate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// Labelled corpus format (plan Step 10): a header line, then one case per line, TAB-separated:
//   query  entityType  dob  country  identifier  expected  caseClass
// entityType / dob / country / identifier may be blank; country and identifier take comma-separated
// values; expected is the source's own entity id or NONE; caseClass is free text such as
// TRUE_MATCH, VARIANT, NEAR_MISS, CLEAR. Blank lines and '#' comments are skipped.
//
// A malformed line fails the WHOLE read: skipping a case would shrink the denominator and inflate
// recall. Errors cite the line number only -- the line holds a name (I-11).
public final class BenchmarkCorpusReader {

    static final String HEADER = "query\tentityType\tdob\tcountry\tidentifier\texpected\tcaseClass";

    public record Row(
            int line,
            String query,
            Optional<EntityType> entityType,
            Optional<PartialDate> dob,
            Set<String> countries,
            Set<String> identifiers,
            Optional<String> expectedSourceEntityId,
            String caseClass) {
    }

    public List<Row> read(Path path) {
        try {
            return parse(Files.readAllLines(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the corpus file", e);
        }
    }

    List<Row> parse(List<String> lines) {
        List<Row> rows = new ArrayList<>();
        boolean sawHeader = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int number = i + 1;
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (!sawHeader) {
                if (!line.strip().equals(HEADER)) {
                    throw new IllegalArgumentException("corpus line " + number + ": the first line must be the header "
                            + HEADER.replace("\t", "<TAB>"));
                }
                sawHeader = true;
                continue;
            }
            String[] columns = line.split("\t", -1);
            if (columns.length != 7 || columns[0].isBlank() || columns[5].isBlank() || columns[6].isBlank()) {
                throw new IllegalArgumentException("corpus line " + number
                        + ": expected 7 tab-separated columns with query, expected and caseClass filled");
            }
            try {
                rows.add(new Row(number, columns[0].strip(), optionalType(columns[1]),
                        columns[2].isBlank() ? Optional.empty() : Optional.of(ScreenCommand.parseDob(columns[2].strip())),
                        split(columns[3]), split(columns[4]),
                        columns[5].strip().equals("NONE") ? Optional.empty() : Optional.of(columns[5].strip()),
                        columns[6].strip()));
            } catch (IllegalArgumentException e) {
                // Deliberately drops e.getMessage(): it could quote the offending value.
                throw new IllegalArgumentException("corpus line " + number + ": a value could not be parsed");
            }
        }
        if (!sawHeader || rows.isEmpty()) {
            throw new IllegalArgumentException("the corpus contains no cases");
        }
        return rows;
    }

    private static Optional<EntityType> optionalType(String text) {
        return text.isBlank() ? Optional.empty() : Optional.of(EntityType.valueOf(text.strip()));
    }

    private static Set<String> split(String text) {
        Set<String> values = new LinkedHashSet<>();
        Arrays.stream(text.split(",")).map(String::strip).filter(v -> !v.isEmpty()).forEach(values::add);
        return values;
    }
}
