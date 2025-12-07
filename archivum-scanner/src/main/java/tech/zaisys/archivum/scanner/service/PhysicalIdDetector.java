package tech.zaisys.archivum.scanner.service;

import lombok.extern.slf4j.Slf4j;
import tech.zaisys.archivum.api.dto.PhysicalId;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for detecting physical device identifiers.
 * Uses OS-specific commands: blkid (Linux), diskutil (macOS), wmic (Windows).
 */
@Slf4j
public class PhysicalIdDetector {

    private static final int COMMAND_TIMEOUT_SECONDS = 5;

    /**
     * Detect physical device identifiers for a given path.
     * Returns best-effort results - some fields may be null if detection fails.
     *
     * @param path Path to detect (mount point or directory)
     * @return PhysicalId with detected values
     */
    public PhysicalId detect(Path path) {
        String os = System.getProperty("os.name").toLowerCase();

        PhysicalId.PhysicalIdBuilder builder = PhysicalId.builder();

        try {
            // Get mount point and filesystem info via Java NIO
            FileStore store = Files.getFileStore(path);
            builder.mountPoint(path.toAbsolutePath().toString());
            builder.filesystemType(store.type());
            builder.capacity(store.getTotalSpace());
            builder.usedSpace(store.getTotalSpace() - store.getUnallocatedSpace());
            builder.volumeLabel(store.name());

            // OS-specific detection
            if (os.contains("linux")) {
                detectLinux(path, builder);
            } else if (os.contains("mac")) {
                detectMacOS(path, builder);
            } else if (os.contains("win")) {
                detectWindows(path, builder);
            } else {
                log.warn("Unsupported OS for physical ID detection: {}", os);
            }

        } catch (IOException e) {
            log.warn("Cannot detect physical ID for path: {} - {}", path, e.getMessage());
        }

        return builder.build();
    }

    /**
     * Detect physical IDs on Linux using blkid, lsblk, and udevadm.
     */
    private void detectLinux(Path path, PhysicalId.PhysicalIdBuilder builder) {
        try {
            // Find device for mount point using df
            String device = findLinuxDevice(path);
            if (device == null) {
                log.debug("Cannot find device for path: {}", path);
                return;
            }

            // Get UUID and filesystem info from blkid
            String blkidOutput = executeCommand("blkid", device);
            if (blkidOutput != null) {
                extractFromBlkid(blkidOutput, builder);
            }

            // Get serial number from udevadm
            String serialOutput = executeCommand("udevadm", "info", "--query=property", "--name=" + device);
            if (serialOutput != null) {
                extractSerialFromUdevadm(serialOutput, builder);
            }

        } catch (Exception e) {
            log.warn("Error detecting Linux physical ID: {}", e.getMessage());
        }
    }

    /**
     * Find Linux device for a path using df command.
     */
    private String findLinuxDevice(Path path) {
        String dfOutput = executeCommand("df", path.toString());
        if (dfOutput == null) return null;

        // Parse df output: /dev/sdb1 ... mounted on /mnt/disk
        String[] lines = dfOutput.split("\n");
        if (lines.length < 2) return null;

        String[] parts = lines[1].trim().split("\\s+");
        return parts.length > 0 ? parts[0] : null;
    }

    /**
     * Extract UUID and partition info from blkid output.
     * Example: /dev/sdb1: UUID="abc-123" TYPE="ext4" PARTUUID="def-456"
     */
    private void extractFromBlkid(String output, PhysicalId.PhysicalIdBuilder builder) {
        Pattern uuidPattern = Pattern.compile("UUID=\"([^\"]+)\"");
        Pattern partUuidPattern = Pattern.compile("PARTUUID=\"([^\"]+)\"");

        Matcher uuidMatcher = uuidPattern.matcher(output);
        if (uuidMatcher.find()) {
            builder.diskUuid(uuidMatcher.group(1));
        }

        Matcher partUuidMatcher = partUuidPattern.matcher(output);
        if (partUuidMatcher.find()) {
            builder.partitionUuid(partUuidMatcher.group(1));
        }
    }

    /**
     * Extract serial number from udevadm output.
     * Example: ID_SERIAL=WD-WXY1234567890
     */
    private void extractSerialFromUdevadm(String output, PhysicalId.PhysicalIdBuilder builder) {
        Pattern serialPattern = Pattern.compile("ID_SERIAL=(.+)");
        Matcher matcher = serialPattern.matcher(output);
        if (matcher.find()) {
            builder.serialNumber(matcher.group(1).trim());
        }
    }

    /**
     * Detect physical IDs on macOS using diskutil.
     */
    private void detectMacOS(Path path, PhysicalId.PhysicalIdBuilder builder) {
        try {
            String diskutilOutput = executeCommand("diskutil", "info", path.toString());
            if (diskutilOutput == null) return;

            // Extract UUID
            Pattern uuidPattern = Pattern.compile("Volume UUID:\\s+(.+)");
            Matcher uuidMatcher = uuidPattern.matcher(diskutilOutput);
            if (uuidMatcher.find()) {
                builder.diskUuid(uuidMatcher.group(1).trim());
            }

            // Extract device identifier
            Pattern devicePattern = Pattern.compile("Device Identifier:\\s+(.+)");
            Matcher deviceMatcher = devicePattern.matcher(diskutilOutput);
            if (deviceMatcher.find()) {
                String device = deviceMatcher.group(1).trim();

                // Get serial number using system_profiler
                String profilerOutput = executeCommand("system_profiler", "SPSerialATADataType");
                if (profilerOutput != null) {
                    extractMacOSSerial(profilerOutput, builder);
                }
            }

        } catch (Exception e) {
            log.warn("Error detecting macOS physical ID: {}", e.getMessage());
        }
    }

    /**
     * Extract serial number from macOS system_profiler output.
     */
    private void extractMacOSSerial(String output, PhysicalId.PhysicalIdBuilder builder) {
        Pattern serialPattern = Pattern.compile("Serial Number:\\s+(.+)");
        Matcher matcher = serialPattern.matcher(output);
        if (matcher.find()) {
            builder.serialNumber(matcher.group(1).trim());
        }
    }

    /**
     * Detect physical IDs on Windows using wmic.
     */
    private void detectWindows(Path path, PhysicalId.PhysicalIdBuilder builder) {
        try {
            // Get volume serial number
            String volumeOutput = executeCommand("wmic", "volume", "where",
                "DriveLetter='" + path.toString().substring(0, 2) + "'", "get", "DeviceID,VolumeSerialNumber");

            if (volumeOutput != null) {
                extractWindowsVolumeSerial(volumeOutput, builder);
            }

            // Get disk serial number
            String diskOutput = executeCommand("wmic", "diskdrive", "get", "SerialNumber");
            if (diskOutput != null) {
                extractWindowsDiskSerial(diskOutput, builder);
            }

        } catch (Exception e) {
            log.warn("Error detecting Windows physical ID: {}", e.getMessage());
        }
    }

    private void extractWindowsVolumeSerial(String output, PhysicalId.PhysicalIdBuilder builder) {
        String[] lines = output.split("\n");
        if (lines.length > 1) {
            String[] parts = lines[1].trim().split("\\s+");
            if (parts.length > 1) {
                builder.partitionUuid(parts[1]);
            }
        }
    }

    private void extractWindowsDiskSerial(String output, PhysicalId.PhysicalIdBuilder builder) {
        String[] lines = output.split("\n");
        if (lines.length > 1) {
            String serial = lines[1].trim();
            if (!serial.isEmpty()) {
                builder.serialNumber(serial);
            }
        }
    }

    /**
     * Execute a system command and return output.
     * Returns null if command fails or times out.
     */
    private String executeCommand(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // Wait for completion with timeout
            boolean completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                log.warn("Command timed out: {}", String.join(" ", command));
                return null;
            }

            if (process.exitValue() != 0) {
                log.debug("Command failed with exit code {}: {}",
                    process.exitValue(), String.join(" ", command));
                return null;
            }

            return output.toString();

        } catch (IOException | InterruptedException e) {
            log.debug("Cannot execute command {}: {}",
                String.join(" ", command), e.getMessage());
            return null;
        }
    }
}
