package tech.zaisys.archivum.scanner.service;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Service for interactive user prompts with smart defaults.
 * Handles console input with validation and default values.
 * Uses System.console() when available (proper terminal), falls back to BufferedReader.
 */
@Slf4j
public class InteractivePrompt {

    private final Console console;
    private final BufferedReader reader;

    public InteractivePrompt() {
        this.console = System.console();
        this.reader = new BufferedReader(new InputStreamReader(System.in));

        if (console != null) {
            log.debug("Using System.console() for interactive input");
        } else {
            log.debug("Using BufferedReader for input (console not available)");
        }
    }

    /**
     * Prompt for source name with smart default.
     * Default: Volume label with underscores replaced by hyphens.
     *
     * @param volumeLabel Detected volume label
     * @return User-provided name or default
     */
    public String promptForSourceName(String volumeLabel) {
        String defaultName = sanitizeVolumeName(volumeLabel);

        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("Detected Volume: " + (volumeLabel != null ? volumeLabel : "(unknown)"));
        System.out.println();
        System.out.println("Enter a logical name for this source (e.g., 'WD-4TB-Blue')");
        System.out.println("Press ENTER to accept default: [" + defaultName + "]");
        System.out.println();
        System.out.print("> ");
        System.out.flush();

        String input = readLine();
        String result = input.isEmpty() ? defaultName : input.trim();

        System.out.println("✓ Source name: " + result);
        System.out.println();

        return result;
    }

    /**
     * Prompt for physical label (sticker on disk).
     * Optional field - user can press Enter to skip.
     *
     * @return Physical label or null
     */
    public String promptForPhysicalLabel() {
        System.out.println("Enter physical label (sticker on disk)");
        System.out.println("Press ENTER to skip (optional)");
        System.out.println();
        System.out.print("> ");
        System.out.flush();

        String input = readLine();
        String result = input.isEmpty() ? null : input.trim();

        if (result != null) {
            System.out.println("✓ Physical label: " + result);
        } else {
            System.out.println("✓ Physical label: (none)");
        }
        System.out.println();

        return result;
    }

    /**
     * Prompt for notes about this source.
     * Optional field - user can press Enter to skip.
     *
     * @return Notes or null
     */
    public String promptForNotes() {
        System.out.println("Enter notes about this source (optional)");
        System.out.println("Press ENTER to skip");
        System.out.println();
        System.out.print("> ");
        System.out.flush();

        String input = readLine();
        String result = input.isEmpty() ? null : input.trim();

        if (result != null) {
            System.out.println("✓ Notes: " + result);
        } else {
            System.out.println("✓ Notes: (none)");
        }
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();

        return result;
    }

    /**
     * Sanitize volume label to create a sensible default name.
     * - Replace underscores with hyphens
     * - Trim whitespace
     * - Fall back to "Unknown" if null/empty
     *
     * @param volumeLabel Raw volume label
     * @return Sanitized name
     */
    private String sanitizeVolumeName(String volumeLabel) {
        if (volumeLabel == null || volumeLabel.trim().isEmpty()) {
            return "Unknown";
        }

        return volumeLabel.trim()
            .replace("_", "-")
            .replace(" ", "-");
    }

    /**
     * Read a line from console, handling IOException gracefully.
     * Uses System.console() when available for proper terminal input,
     * falls back to BufferedReader otherwise.
     *
     * @return Input line or empty string on error
     */
    private String readLine() {
        try {
            // Prefer Console if available (proper terminal connection)
            if (console != null) {
                String line = console.readLine();
                return line != null ? line : "";
            }

            // Fall back to BufferedReader
            String line = reader.readLine();
            return line != null ? line : "";
        } catch (IOException e) {
            log.warn("Error reading from console: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Close the reader (call when done with prompts).
     * Note: System.console() doesn't need to be closed.
     */
    public void close() {
        try {
            if (console == null) {
                reader.close();
            }
        } catch (IOException e) {
            log.debug("Error closing reader: {}", e.getMessage());
        }
    }
}
