package tech.zaisys.archivum.scanner.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for reading disk hardware information (serial numbers, mount points).
 * Supports Linux and macOS.
 */
public class DiskInfoUtil {

    private static final Logger logger = LoggerFactory.getLogger(DiskInfoUtil.class);

    /**
     * Get disk serial number for a given path.
     * Uses platform-specific commands:
     * - Linux: lsblk -no SERIAL
     * - macOS: diskutil info
     *
     * @param path Path on the disk
     * @return Serial number or null if not found
     */
    public static String getDiskSerial(Path path) {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("linux")) {
                return getLinuxDiskSerial(path);
            } else if (os.contains("mac")) {
                return getMacDiskSerial(path);
            } else {
                logger.warn("Unsupported OS for disk serial detection: {}", os);
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to get disk serial for path: {}", path, e);
            return null;
        }
    }

    /**
     * Get mount point for a given path.
     * Returns the root mount point where the disk is mounted.
     *
     * @param path Path on the disk
     * @return Mount point or null if not found
     */
    public static String getMountPoint(Path path) {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("linux")) {
                return getLinuxMountPoint(path);
            } else if (os.contains("mac")) {
                return getMacMountPoint(path);
            } else {
                logger.warn("Unsupported OS for mount point detection: {}", os);
                return null;
            }
        } catch (Exception e) {
            logger.error("Failed to get mount point for path: {}", path, e);
            return null;
        }
    }

    private static String getLinuxDiskSerial(Path path) throws Exception {
        // Find the device for this path using df
        ProcessBuilder pb = new ProcessBuilder("df", path.toString());
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.readLine(); // Skip header
            String line = reader.readLine();
            if (line != null) {
                String device = line.split("\\s+")[0];

                // Extract base device name (e.g., /dev/sda1 -> sda)
                String deviceName = device.replaceAll(".*/", "").replaceAll("[0-9]+$", "");

                // Get serial using lsblk
                ProcessBuilder serialPb = new ProcessBuilder("lsblk", "-no", "SERIAL", "/dev/" + deviceName);
                Process serialProcess = serialPb.start();

                try (BufferedReader serialReader = new BufferedReader(new InputStreamReader(serialProcess.getInputStream()))) {
                    String serial = serialReader.readLine();
                    return (serial != null && !serial.trim().isEmpty()) ? serial.trim() : null;
                }
            }
        }

        return null;
    }

    private static String getMacDiskSerial(Path path) throws Exception {
        // Find the disk for this path using df
        ProcessBuilder pb = new ProcessBuilder("df", path.toString());
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.readLine(); // Skip header
            String line = reader.readLine();
            if (line != null) {
                String device = line.split("\\s+")[0];

                // Get disk info using diskutil
                ProcessBuilder infoPb = new ProcessBuilder("diskutil", "info", device);
                Process infoProcess = infoPb.start();

                try (BufferedReader infoReader = new BufferedReader(new InputStreamReader(infoProcess.getInputStream()))) {
                    Pattern serialPattern = Pattern.compile(".*Disk / Partition UUID:\\s+(.+)");
                    Pattern volumeUuidPattern = Pattern.compile(".*Volume UUID:\\s+(.+)");

                    String volumeUuid = null;
                    String diskUuid = null;

                    String infoLine;
                    while ((infoLine = infoReader.readLine()) != null) {
                        Matcher serialMatcher = serialPattern.matcher(infoLine);
                        if (serialMatcher.matches()) {
                            diskUuid = serialMatcher.group(1).trim();
                        }

                        Matcher volumeMatcher = volumeUuidPattern.matcher(infoLine);
                        if (volumeMatcher.matches()) {
                            volumeUuid = volumeMatcher.group(1).trim();
                        }
                    }

                    // Prefer Disk UUID over Volume UUID
                    return diskUuid != null ? diskUuid : volumeUuid;
                }
            }
        }

        return null;
    }

    private static String getLinuxMountPoint(Path path) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("df", "--output=target", path.toString());
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.readLine(); // Skip header
            String mountPoint = reader.readLine();
            return (mountPoint != null && !mountPoint.trim().isEmpty()) ? mountPoint.trim() : null;
        }
    }

    private static String getMacMountPoint(Path path) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("df", path.toString());
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            reader.readLine(); // Skip header
            String line = reader.readLine();
            if (line != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 6) {
                    // Mount point is the last column
                    return parts[parts.length - 1];
                }
            }
        }

        return null;
    }
}
