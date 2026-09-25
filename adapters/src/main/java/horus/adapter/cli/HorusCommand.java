package horus.adapter.cli;

import picocli.CommandLine.Command;

@Command(name = "horus", mixinStandardHelpOptions = true, description = "HORUS ingestion CLI")
public final class HorusCommand implements Runnable {

    @Override
    public void run() {
        // No-op: picocli prints usage when invoked with no subcommand.
    }
}
