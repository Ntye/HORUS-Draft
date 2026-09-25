package horus.bootstrap;

import horus.adapter.cli.BenchmarkCommand;
import horus.adapter.cli.ConfirmCommand;
import horus.adapter.cli.HorusCommand;
import horus.adapter.cli.IngestCommand;
import horus.adapter.cli.MeasureBlockingCommand;
import horus.adapter.cli.PublishConfigCommand;
import horus.adapter.cli.RollbackCommand;
import horus.adapter.cli.ScreenCommand;
import horus.adapter.cli.ShowScreeningCommand;
import horus.adapter.persistence.MissingCredentialException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import picocli.CommandLine;

// CLAUDE.md §8: `.\gradlew.bat :bootstrap:bootRun --args="ingest --source=worldcheck"`.
@Configuration
public class CliRunnerConfig {

    /** sysexits.h EX_CONFIG: the command is well formed but the environment cannot support it. */
    static final int EXIT_CONFIGURATION_ERROR = 78;

    @Bean
    public CommandLineRunner horusCli(
            IngestCommand ingestCommand,
            ConfirmCommand confirmCommand,
            RollbackCommand rollbackCommand,
            MeasureBlockingCommand measureBlockingCommand,
            PublishConfigCommand publishConfigCommand,
            ScreenCommand screenCommand,
            ShowScreeningCommand showScreeningCommand,
            BenchmarkCommand benchmarkCommand) {
        return args -> {
            CommandLine cli = new CommandLine(new HorusCommand())
                    .addSubcommand("ingest", ingestCommand)
                    .addSubcommand("confirm", confirmCommand)
                    .addSubcommand("rollback", rollbackCommand)
                    .addSubcommand("measure-blocking", measureBlockingCommand)
                    .addSubcommand("publish-config", publishConfigCommand)
                    .addSubcommand("screen", screenCommand)
                    .addSubcommand("show-screening", showScreeningCommand)
                    .addSubcommand("benchmark", benchmarkCommand)
                    .setExecutionExceptionHandler(CliRunnerConfig::reportExecutionFailure);
            int exitCode = cli.execute(args);
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        };
    }

    // picocli's default handler prints a full stack trace. A missing credential is an operator
    // problem with a one-line remedy, so it gets one line and a distinct exit code; anything else is
    // rethrown so a genuine bug keeps its trace. The message names a variable, never a value (I-11).
    private static int reportExecutionFailure(
            Exception exception, CommandLine commandLine, CommandLine.ParseResult parseResult) throws Exception {
        MissingCredentialException missing = MissingCredentialException.findIn(exception);
        if (missing == null) {
            throw exception;
        }
        commandLine.getErr().println("Configuration error: " + missing.getMessage());
        return EXIT_CONFIGURATION_ERROR;
    }
}
