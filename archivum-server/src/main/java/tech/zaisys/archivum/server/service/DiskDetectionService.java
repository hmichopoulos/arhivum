package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.server.domain.Source;
import tech.zaisys.archivum.server.repository.SourceRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for detecting connected disks and updating their online/offline states.
 * Scans the system for currently connected disks and matches them with database entries
 * by serial number.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DiskDetectionService {

    private final SourceRepository sourceRepository;

    /**
     * Scan system for connected disks and update database states.
     * Marks disks as ONLINE if detected, keeps others as OFFLINE.
     *
     * @return Map of serial numbers to mount points for detected disks
     */
    @Transactional
    public Map<String, DiskInfo> detectConnectedDisks() {
        log.info("Scanning system for connected disks...");

        Map<String, DiskInfo> connectedDisks = scanSystem();
        log.info("Found {} connected disks", connectedDisks.size());

        updateDiskStates(connectedDisks);

        return connectedDisks;
    }

    /**
     * Update a specific source's disk state when it's detected.
     *
     * @param sourceId Source ID
     * @param mountPoint Current mount point
     */
    @Transactional
    public void markDiskOnline(UUID sourceId, String mountPoint) {
        Source source = sourceRepository.findById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));

        source.setDiskState("ONLINE");
        source.setMountPoint(mountPoint);
        source.setLastSeen(Instant.now());

        sourceRepository.save(source);
        log.info("Marked disk {} as ONLINE at {}", source.getName(), mountPoint);
    }

    /**
     * Mark a source's disk as offline.
     *
     * @param sourceId Source ID
     */
    @Transactional
    public void markDiskOffline(UUID sourceId) {
        Source source = sourceRepository.findById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));

        source.setDiskState("OFFLINE");
        source.setMountPoint(null);
        // Keep lastSeen as-is (shows when it was last connected)

        sourceRepository.save(source);
        log.info("Marked disk {} as OFFLINE", source.getName());
    }

    /**
     * Scan system for connected disks and extract their serial numbers and mount points.
     *
     * @return Map of serial number to DiskInfo
     */
    private Map<String, DiskInfo> scanSystem() {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("linux")) {
                return scanLinuxDisks();
            } else if (os.contains("mac")) {
                return scanMacDisks();
            } else {
                log.warn("Unsupported OS for disk detection: {}", os);
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            log.error("Failed to scan system for disks", e);
            return Collections.emptyMap();
        }
    }

    /**
     * Scan for Linux disks using lsblk.
     *
     * @return Map of serial number to DiskInfo
     */
    private Map<String, DiskInfo> scanLinuxDisks() throws Exception {
        Map<String, DiskInfo> disks = new HashMap<>();

        // List all block devices with serial and mountpoint
        ProcessBuilder pb = new ProcessBuilder("lsblk", "-no", "NAME,SERIAL,MOUNTPOINT", "-p");
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 2) {
                    String device = parts[0];
                    String serial = parts[1];
                    String mountPoint = parts.length >= 3 ? parts[2] : null;

                    // Skip devices without serial numbers or without mountpoints
                    if (serial != null && !serial.isEmpty() && mountPoint != null && !mountPoint.isEmpty()) {
                        disks.put(serial, new DiskInfo(device, mountPoint, serial));
                        log.debug("Found Linux disk: device={}, serial={}, mountPoint={}", device, serial, mountPoint);
                    }
                }
            }
        }

        return disks;
    }

    /**
     * Scan for macOS disks using diskutil.
     *
     * @return Map of UUID to DiskInfo
     */
    private Map<String, DiskInfo> scanMacDisks() throws Exception {
        Map<String, DiskInfo> disks = new HashMap<>();

        // List all disks
        ProcessBuilder pb = new ProcessBuilder("diskutil", "list", "-plist");
        Process process = pb.start();

        // For macOS, we'll use a simpler approach: list mounted volumes
        ProcessBuilder mountPb = new ProcessBuilder("mount");
        Process mountProcess = mountPb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(mountProcess.getInputStream()))) {
            Pattern pattern = Pattern.compile("(/dev/\\S+) on (\\S+)");
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    String device = matcher.group(1);
                    String mountPoint = matcher.group(2);

                    // Get UUID for this device
                    String uuid = getMacDiskUUID(device);
                    if (uuid != null && !uuid.isEmpty()) {
                        disks.put(uuid, new DiskInfo(device, mountPoint, uuid));
                        log.debug("Found Mac disk: device={}, uuid={}, mountPoint={}", device, uuid, mountPoint);
                    }
                }
            }
        }

        return disks;
    }

    /**
     * Get macOS disk UUID using diskutil.
     *
     * @param device Device path
     * @return UUID or null
     */
    private String getMacDiskUUID(String device) {
        try {
            ProcessBuilder pb = new ProcessBuilder("diskutil", "info", device);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                Pattern uuidPattern = Pattern.compile(".*Volume UUID:\\s+(.+)");
                Pattern diskUuidPattern = Pattern.compile(".*Disk / Partition UUID:\\s+(.+)");

                String volumeUuid = null;
                String diskUuid = null;

                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher volumeMatcher = uuidPattern.matcher(line);
                    if (volumeMatcher.matches()) {
                        volumeUuid = volumeMatcher.group(1).trim();
                    }

                    Matcher diskMatcher = diskUuidPattern.matcher(line);
                    if (diskMatcher.matches()) {
                        diskUuid = diskMatcher.group(1).trim();
                    }
                }

                // Prefer Disk UUID over Volume UUID
                return diskUuid != null ? diskUuid : volumeUuid;
            }
        } catch (Exception e) {
            log.warn("Failed to get UUID for device {}: {}", device, e.getMessage());
            return null;
        }
    }

    /**
     * Update database states for all tracked disks.
     * Marks detected disks as ONLINE, others remain as-is.
     *
     * @param connectedDisks Map of serial numbers to disk info
     */
    private void updateDiskStates(Map<String, DiskInfo> connectedDisks) {
        // Find all sources with serial numbers
        List<Source> sourcesWithSerials = sourceRepository.findAll().stream()
            .filter(s -> s.getSerialNumber() != null && !s.getSerialNumber().isEmpty())
            .toList();

        for (Source source : sourcesWithSerials) {
            String serial = source.getSerialNumber();
            DiskInfo diskInfo = connectedDisks.get(serial);

            if (diskInfo != null) {
                // Disk is connected - mark as ONLINE
                if (!"ONLINE".equals(source.getDiskState()) ||
                    !diskInfo.mountPoint.equals(source.getMountPoint())) {

                    source.setDiskState("ONLINE");
                    source.setMountPoint(diskInfo.mountPoint);
                    source.setLastSeen(Instant.now());
                    sourceRepository.save(source);

                    log.info("Disk detected: {} (serial: {}) at {}", source.getName(), serial, diskInfo.mountPoint);
                }
            } else {
                // Disk not detected - mark as OFFLINE if currently ONLINE
                if ("ONLINE".equals(source.getDiskState())) {
                    source.setDiskState("OFFLINE");
                    source.setMountPoint(null);
                    sourceRepository.save(source);

                    log.info("Disk disconnected: {} (serial: {})", source.getName(), serial);
                }
            }
        }
    }

    /**
     * Information about a detected disk.
     */
    public static class DiskInfo {
        public final String device;
        public final String mountPoint;
        public final String serial;

        public DiskInfo(String device, String mountPoint, String serial) {
            this.device = device;
            this.mountPoint = mountPoint;
            this.serial = serial;
        }
    }
}
