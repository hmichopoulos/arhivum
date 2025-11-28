package tech.zaisys.archivum.scanner.service;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Service for interactive user prompts with smart defaults.
 * Handles console input with validation and default values.
 */
@Slf4j
public class InteractivePrompt {

    private final BufferedReader reader;

    public InteractivePrompt() {
        this.reader = new BufferedReader(new InputStreamReader(System.in));
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
        System.out.println("Detected Volume: " + (volumeLabel != null ? volumeLabel : "(unknown)"));
        System.out.print("Enter logical name [" + defaultName + "]: ");

        String input = readLine();
        return input.isEmpty() ? defaultName : input.trim();
    }

    /**
     * Prompt for physical label (sticker on disk).
     * Optional field - user can press Enter to skip.
     *
     * @return Physical label or null
     */
    public String promptForPhysicalLabel() {
        System.out.print("Enter physical label (sticker on disk) [leave empty if none]: ");

        String input = readLine();
        return input.isEmpty() ? null : input.trim();
    }

    /**
     * Prompt for notes about this source.
     * Optional field - user can press Enter to skip.
     *
     * @return Notes or null
     */
    public String promptForNotes() {
        System.out.print("Enter notes (optional): ");

        String input = readLine();
        return input.isEmpty() ? null : input.trim();
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
     *
     * @return Input line or empty string on error
     */
    private String readLine() {
        try {
            String line = reader.readLine();
            return line != null ? line : "";
        } catch (IOException e) {
            log.warn("Error reading from console: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Close the reader (call when done with prompts).
     */
    public void close() {
        try {
            reader.close();
        } catch (IOException e) {
            log.debug("Error closing reader: {}", e.getMessage());
        }
    }
}
