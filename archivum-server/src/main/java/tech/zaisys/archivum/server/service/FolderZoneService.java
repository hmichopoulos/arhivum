package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.api.enums.Zone;
import tech.zaisys.archivum.server.domain.FolderZone;
import tech.zaisys.archivum.server.domain.Source;
import tech.zaisys.archivum.server.repository.FolderZoneRepository;
import tech.zaisys.archivum.server.repository.SourceRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for managing folder zone classifications with inheritance.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FolderZoneService {

    private final FolderZoneRepository folderZoneRepository;
    private final SourceRepository sourceRepository;

    /**
     * Result containing zone and whether it's inherited.
     */
    public record ZoneResult(Zone zone, boolean isInherited) {}

    /**
     * Get zone for a folder, with inheritance from parent folders.
     * Walks up the folder hierarchy until it finds an explicit zone.
     *
     * @param sourceId Source ID
     * @param folderPath Full folder path
     * @param folderZoneMap Preloaded map of folder paths to zones for efficiency
     * @return ZoneResult containing zone and inheritance flag
     */
    public ZoneResult getZoneForFolder(UUID sourceId, String folderPath, Map<String, Zone> folderZoneMap) {
        // Check if this folder has an explicit zone
        Zone explicitZone = folderZoneMap.get(folderPath);
        if (explicitZone != null) {
            return new ZoneResult(explicitZone, false);
        }

        // Walk up the folder hierarchy to find inherited zone
        String currentPath = folderPath;
        while (currentPath != null && !currentPath.isEmpty()) {
            currentPath = getParentPath(currentPath);
            if (currentPath != null) {
                Zone parentZone = folderZoneMap.get(currentPath);
                if (parentZone != null) {
                    return new ZoneResult(parentZone, true);
                }
            }
        }

        // No zone found in hierarchy, return null (will show as no zone)
        return null;
    }

    /**
     * Load all folder zones for a source into a map for efficient lookups.
     *
     * @param sourceId Source ID
     * @return Map of folder paths to zones
     */
    public Map<String, Zone> loadFolderZones(UUID sourceId) {
        List<FolderZone> zones = folderZoneRepository.findBySourceId(sourceId);
        Map<String, Zone> zoneMap = new HashMap<>();
        for (FolderZone fz : zones) {
            zoneMap.put(fz.getFolderPath(), fz.getZone());
        }
        log.debug("Loaded {} folder zones for source {}", zoneMap.size(), sourceId);
        return zoneMap;
    }

    /**
     * Set explicit zone for a folder.
     *
     * @param sourceId Source ID
     * @param folderPath Folder path
     * @param zone Zone to set
     */
    @Transactional
    public void setFolderZone(UUID sourceId, String folderPath, Zone zone) {
        Source source = sourceRepository.findById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Source not found: " + sourceId));

        FolderZone folderZone = folderZoneRepository
            .findBySourceIdAndFolderPath(sourceId, folderPath)
            .orElse(FolderZone.builder()
                .source(source)
                .folderPath(folderPath)
                .build());

        folderZone.setZone(zone);
        folderZoneRepository.save(folderZone);

        log.info("Set zone {} for folder {} in source {}", zone, folderPath, sourceId);
    }

    /**
     * Remove explicit zone for a folder (will fall back to inherited zone).
     *
     * @param sourceId Source ID
     * @param folderPath Folder path
     */
    @Transactional
    public void removeFolderZone(UUID sourceId, String folderPath) {
        folderZoneRepository.deleteBySourceIdAndFolderPath(sourceId, folderPath);
        log.info("Removed explicit zone for folder {} in source {}", folderPath, sourceId);
    }

    /**
     * Get parent folder path.
     * For example: "/Documents/Work" -> "/Documents"
     *
     * @param path Full folder path
     * @return Parent path, or null if at root
     */
    private String getParentPath(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) {
            return null;
        }

        // Remove trailing slash if present
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return null;
        }

        return path.substring(0, lastSlash);
    }
}
