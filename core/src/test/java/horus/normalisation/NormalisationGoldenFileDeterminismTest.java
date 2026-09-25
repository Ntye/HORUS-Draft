package horus.normalisation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

// I-4: identical input + identical pipeline version must always produce an identical result.
class NormalisationGoldenFileDeterminismTest {

    private final NormalisationPipeline pipeline = NormalisationPipeline.standard();

    @Test
    void normalisingTheSameFixtureTwiceProducesByteForByteIdenticalResults() {
        List<String> rawNames = readGoldenNames();

        List<NormalisedName> firstRun = rawNames.stream().map(pipeline::normalise).toList();
        List<NormalisedName> secondRun = rawNames.stream().map(pipeline::normalise).toList();

        assertThat(firstRun).isEqualTo(secondRun);
    }

    private static List<String> readGoldenNames() {
        try (InputStream stream = NormalisationGoldenFileDeterminismTest.class
                .getResourceAsStream("/normalisation/golden-names.txt")) {
            if (stream == null) {
                throw new IllegalStateException("golden-names.txt fixture not found on classpath");
            }
            return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines()
                    .filter(line -> !line.isBlank())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
