package tech.zaisys.archivum.scanner;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import tech.zaisys.archivum.scanner.command.ScanCommand;

/**
 * Main entry point for the Archivum Scanner CLI application.
 *
 * <p>This lightweight tool scans file systems, computes hashes, and sends
 * metadata to the Archivum server for cataloging.</p>
 */
@Command(
    name = "archivum-scanner",
    version = "0.1.0",
    description = "Scan filesystems and send metadata to Archivum server",
    mixinStandardHelpOptions = true,
    subcommands = {
        ScanCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class ScannerApp implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ScannerApp()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println("Archivum Scanner - Use --help for available commands");
    }
}
