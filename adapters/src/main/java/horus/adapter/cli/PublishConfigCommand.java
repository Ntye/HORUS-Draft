package horus.adapter.cli;

import horus.application.screening.PublishConfigVersion;
import horus.application.screening.ScreeningUnavailableException;
import horus.matching.InvalidMatchConfigException;
import horus.matching.MatchConfig;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// Publishes a new, immutable configuration version (I-7). It is validated against the active list
// versions' capabilities first, so an invalid configuration is refused here and can never become
// what a screening runs on. --defaults publishes the spec §16.2 starting point, which is NOT an
// approved operating point until Compliance approves it (spec §16.4).
@Component
@Command(name = "publish-config", description = "Publish a matching configuration version.")
public final class PublishConfigCommand implements Callable<Integer> {

    @Option(names = "--profile", defaultValue = "default")
    private String profile;

    @Option(names = "--defaults", description = "Publish the built-in defaults (uncalibrated starting point).")
    private boolean defaults;

    @Option(names = "--file", description = "A JSON file holding a MatchConfig.")
    private Path file;

    @Option(names = "--approved-by", required = true, description = "Who approved this configuration.")
    private String approvedBy;

    private final PublishConfigVersion publishConfigVersion;
    private final ObjectMapper objectMapper;

    public PublishConfigCommand(PublishConfigVersion publishConfigVersion, ObjectMapper objectMapper) {
        this.publishConfigVersion = publishConfigVersion;
        this.objectMapper = objectMapper;
    }

    @Override
    public Integer call() {
        if (defaults == (file != null)) {
            System.err.println("give exactly one of --defaults or --file");
            return 64;
        }
        MatchConfig config;
        try {
            config = defaults ? MatchConfig.defaults() : objectMapper.readValue(file.toFile(), MatchConfig.class);
        } catch (JacksonException e) {
            System.err.println("the file is not a valid MatchConfig");
            return 64;
        }
        try {
            UUID id = publishConfigVersion.execute(
                    new PublishConfigVersion.Command(profile, config, operator(), approvedBy));
            System.out.println("configVersionId: " + id);
            System.out.println("alertThreshold: " + config.alertThreshold());
            System.out.println("strongMatchThreshold: " + config.strongMatchThreshold());
            return 0;
        } catch (InvalidMatchConfigException | ScreeningUnavailableException e) {
            System.err.println("refused: " + e.getMessage());
            return 3;
        }
    }

    private static String operator() {
        String user = System.getenv("HORUS_OPERATOR");
        if (user == null || user.isBlank()) {
            throw new IllegalStateException("HORUS_OPERATOR must identify the operator publishing the configuration");
        }
        return user;
    }
}
