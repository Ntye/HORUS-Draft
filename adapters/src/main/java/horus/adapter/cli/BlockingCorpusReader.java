package horus.adapter.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Corpus format: one case per line, "query<TAB>sourceEntityId". Blank lines and lines starting
// with '#' are skipped. A malformed line fails the whole read: silently skipping a case would
// shrink the denominator and inflate recall. Errors cite the line number only -- the line holds a
// name (I-11).
public final class BlockingCorpusReader {

    public record Row(int line, String query, String sourceEntityId) {
    }

    public List<Row> read(Path path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the corpus file", e);
        }
        return parse(lines);
    }

    List<Row> parse(List<String> lines) {
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split("\t", -1);
            if (columns.length != 2 || columns[0].isBlank() || columns[1].isBlank()) {
                throw new IllegalArgumentException(
                        "corpus line " + (i + 1) + ": expected two non-empty tab-separated columns");
            }
            rows.add(new Row(i + 1, columns[0].strip(), columns[1].strip()));
        }
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("corpus contains no cases");
        }
        return rows;
    }
}
