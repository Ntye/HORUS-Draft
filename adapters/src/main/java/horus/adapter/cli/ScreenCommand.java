package horus.adapter.cli;

import horus.adapter.persistence.MissingCredentialException;
import horus.application.port.IdGenerator;
import horus.application.screening.ScreenAName;
import horus.application.screening.ScreeningUnavailableException;
import horus.domain.audit.ActorKind;
import horus.domain.shared.DecisionBand;
import horus.domain.shared.EntityType;
import horus.matching.InvalidMatchConfigException;
import horus.matching.PartialDate;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

// Step 9's driver. Exit codes are part of the contract, because a script must never mistake a
// failure for a clear (I-1):
//   0 NO_MATCH  1 POSSIBLE_MATCH  2 STRONG_MATCH  3 ERROR / unavailable  64 bad input
//   78 configuration error (a required credential is not set)
// Output carries ids, scores and the LISTED entity's name -- never the name that was screened (I-11).
@Component
@Command(name = "screen", description = "Screen one or more names against the active watchlist.")
public final class ScreenCommand implements Callable<Integer> {

    private static final int SHOWN_CANDIDATES = 5;

    @Option(names = "--name", description = "The name to screen.")
    private String name;

    @Option(names = "--names-file", description = "A UTF-8 file with one name per line.")
    private Path namesFile;

    @Option(names = "--type", description = "Entity type of the subject (e.g. INDIVIDUAL, ORGANISATION).")
    private EntityType entityType;

    @Option(names = "--dob", description = "Date of birth: yyyy, yyyy-MM or yyyy-MM-dd.")
    private String dob;

    @Option(names = "--country", description = "Country code; repeatable.")
    private List<String> countries = new ArrayList<>();

    @Option(names = "--identifier", description = "Identifier such as a passport number; repeatable.")
    private List<String> identifiers = new ArrayList<>();

    @Option(names = "--profile", defaultValue = "default")
    private String profile;

    @Option(names = "--consumer", defaultValue = "cli")
    private String consumer;

    @Option(names = "--reference", description = "Consumer's own reference for this request.")
    private String reference;

    @Option(names = "--idempotency-key", description = "Repeating a key returns the stored screening.")
    private String idempotencyKey;

    private final ScreenAName screenAName;
    private final IdGenerator idGenerator;

    public ScreenCommand(ScreenAName screenAName, IdGenerator idGenerator) {
        this.screenAName = screenAName;
        this.idGenerator = idGenerator;
    }

    @Override
    public Integer call() {
        ScreenAName.Command command;
        try {
            command = buildCommand();
        } catch (IllegalArgumentException e) {
            System.err.println("invalid input: " + e.getMessage());
            return 64;
        }

        ScreenAName.Result result;
        try {
            result = screenAName.execute(command);
        } catch (ScreeningUnavailableException | InvalidMatchConfigException e) {
            // The message names configuration and list versions only -- no query data (I-11).
            System.err.println("ERROR: screening unavailable: " + e.getMessage());
            return 3;
        } catch (IllegalArgumentException e) {
            System.err.println("invalid input: " + e.getMessage());
            return 64;
        } catch (RuntimeException e) {
            // A missing credential is an operator configuration fault, not a screening that hit a data
            // problem, and it has a one-line remedy. Let it out so the runner can say so precisely --
            // Spring wraps it (CannotGetJdbcConnectionException), hence the cause-chain search.
            if (MissingCredentialException.findIn(e) != null) {
                throw e;
            }
            System.err.println("ERROR: screening could not be completed (" + e.getClass().getSimpleName() + ")");
            return 3;
        }
        print(result);
        return exitCodeFor(result.outcome());
    }

    private ScreenAName.Command buildCommand() {
        List<String> names = new ArrayList<>();
        if (name != null) {
            names.add(name);
        }
        if (namesFile != null) {
            try {
                Files.readAllLines(namesFile, StandardCharsets.UTF_8).stream()
                        .filter(line -> !line.isBlank()).forEach(names::add);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read the names file", e);
            }
        }
        if (names.isEmpty()) {
            throw new IllegalArgumentException("give --name or --names-file");
        }
        Optional<PartialDate> date = Optional.ofNullable(dob).map(ScreenCommand::parseDob);
        List<ScreenAName.SubjectInput> subjects = new ArrayList<>();
        for (String n : names) {
            subjects.add(new ScreenAName.SubjectInput(Optional.empty(), n, Optional.ofNullable(entityType), date,
                    new HashSet<>(countries), new HashSet<>(identifiers)));
        }
        return new ScreenAName.Command(consumer, Optional.ofNullable(reference), profile, operator(),
                ActorKind.OPERATOR, idempotencyKey != null ? idempotencyKey : idGenerator.newId().toString(),
                subjects);
    }

    static PartialDate parseDob(String text) {
        String[] parts = text.split("-");
        try {
            int year = Integer.parseInt(parts[0]);
            OptionalInt month = parts.length > 1 ? OptionalInt.of(Integer.parseInt(parts[1])) : OptionalInt.empty();
            OptionalInt day = parts.length > 2 ? OptionalInt.of(Integer.parseInt(parts[2])) : OptionalInt.empty();
            if (parts.length > 3) {
                throw new IllegalArgumentException("--dob must be yyyy, yyyy-MM or yyyy-MM-dd");
            }
            return new PartialDate(year, month, day);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--dob must be yyyy, yyyy-MM or yyyy-MM-dd");
        }
    }

    private void print(ScreenAName.Result result) {
        System.out.println("screeningId: " + result.request().screeningId().value());
        System.out.println("outcome: " + result.outcome() + (result.replayed() ? " (replayed)" : ""));
        System.out.println("listVersionIds: " + result.request().listVersionIds().stream()
                .map(id -> id.value().toString()).sorted().toList());
        System.out.println("configVersionId: " + result.request().configVersionId());
        System.out.println("pipelineVersion: " + result.request().pipelineVersion());
        result.request().failureReason().ifPresent(reason -> System.out.println("failure: " + reason));
        int position = 1;
        for (ScreenAName.SubjectResult subject : result.subjects()) {
            System.out.println("subject " + position++ + ": " + subject.subject().outcome() + ", candidates="
                    + subject.subject().candidateCount() + ", topScore=" + subject.subject().topScore());
            subject.candidates().stream().limit(SHOWN_CANDIDATES).forEach(c -> System.out.println("  #"
                    + c.candidate().rank() + " score=" + c.candidate().compositeScore() + " "
                    + c.candidate().decisionBand() + " entity=" + c.candidate().entityId().value()
                    + c.explanation().map(e -> " matched=\"" + e.matchedName() + "\"").orElse("")));
        }
    }

    static int exitCodeFor(DecisionBand band) {
        return switch (band) {
            case NO_MATCH -> 0;
            case POSSIBLE_MATCH -> 1;
            case STRONG_MATCH -> 2;
            case ERROR -> 3;
        };
    }

    private static String operator() {
        String user = System.getenv("HORUS_OPERATOR");
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("HORUS_OPERATOR must identify the operator requesting the screening");
        }
        return user;
    }
}
