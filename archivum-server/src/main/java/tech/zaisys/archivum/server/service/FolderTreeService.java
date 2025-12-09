package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.api.dto.FolderNodeDto;
import tech.zaisys.archivum.server.domain.ScannedFile;
import tech.zaisys.archivum.server.exception.TreeSizeLimitExceededException;
import tech.zaisys.archivum.server.repository.ScannedFileRepository;

import java.util.*;

/**
 * Service for building folder tree structures from file paths.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FolderTreeService {

    private final ScannedFileRepository fileRepository;
    private final FolderZoneService folderZoneService;

    private static final int PAGE_SIZE = 1000;
    private static final int MAX_TREE_NODES = 100_000; // Limit total nodes (files + folders)
    private static final int MAX_FILES_WARNING = 50_000; // Warn at 50k files
    private static final long MAX_TREE_SIZE_BYTES = 500_000_000L; // 500MB memory estimate

    /**
     * Build a folder tree for a given source.
     * Uses pagination to avoid loading all files into memory at once.
     * Results are cached to avoid rebuilding the same tree multiple times.
     *
     * @param sourceId Source ID
     * @return Root folder node containing the entire tree
     */
    @Cacheable(value = "folderTrees", key = "#sourceId")
    @Transactional(readOnly = true)
    public FolderNodeDto buildTree(UUID sourceId) {
        log.debug("Building folder tree for source {} (cache miss)", sourceId);

        // Build the tree using pagination
        return buildTreeWithPagination(sourceId);
    }

    /**
     * Invalidate cached tree for a source.
     * Call this when files are added/removed from a source.
     *
     * @param sourceId Source ID
     */
    @CacheEvict(value = "folderTrees", key = "#sourceId")
    public void invalidateTree(UUID sourceId) {
        log.debug("Invalidated folder tree cache for source {}", sourceId);
    }

    /**
     * Build a folder tree using pagination to process files in batches.
     * This avoids loading all files into memory at once.
     * Includes memory safety limits to prevent OOM errors.
     *
     * @param sourceId Source ID
     * @return Root folder node
     * @throws TreeSizeLimitExceededException if tree exceeds memory limits
     */
    private FolderNodeDto buildTreeWithPagination(UUID sourceId) {
        // Load all folder zones for this source
        Map<String, tech.zaisys.archivum.api.enums.Zone> folderZoneMap = folderZoneService.loadFolderZones(sourceId);

        Map<String, FolderNode> folderMap = new HashMap<>();
        FolderNode root = new FolderNode("");

        int pageNumber = 0;
        int totalFiles = 0;
        long estimatedMemoryBytes = 0;
        Page<ScannedFile> page;

        do {
            Pageable pageable = PageRequest.of(pageNumber, PAGE_SIZE);
            page = fileRepository.findBySourceId(sourceId, pageable);

            // Process this batch of files
            for (ScannedFile file : page.getContent()) {
                // Check limits before adding
                int totalNodes = totalFiles + folderMap.size();
                if (totalNodes >= MAX_TREE_NODES) {
                    log.error("Tree size limit exceeded: {} nodes (max: {})", totalNodes, MAX_TREE_NODES);
                    throw new TreeSizeLimitExceededException(totalNodes, MAX_TREE_NODES);
                }

                addFileToTree(file, root, folderMap);
                totalFiles++;

                // Estimate memory usage (rough estimate: ~500 bytes per node)
                estimatedMemoryBytes = (long) (totalFiles + folderMap.size()) * 500;
            }

            pageNumber++;

            // Log warning at threshold
            if (totalFiles == MAX_FILES_WARNING) {
                log.warn("Tree building approaching limits: {} files, {} folders",
                    totalFiles, folderMap.size());
            }

            log.debug("Processed page {}/{} ({} files, {} total nodes, ~{}MB memory)",
                pageNumber, page.getTotalPages(), page.getNumberOfElements(),
                totalFiles + folderMap.size(), estimatedMemoryBytes / 1_000_000);

        } while (page.hasNext());

        log.info("Built tree: {} files, {} folders, estimated ~{}MB memory",
            totalFiles, folderMap.size(), estimatedMemoryBytes / 1_000_000);

        return convertToDto(root, sourceId, folderZoneMap);
    }

    /**
     * Add a single file to the folder tree structure.
     * Extracted method to support both batch and paginated processing.
     *
     * @param file File to add
     * @param root Root folder node
     * @param folderMap Map of folder paths to folder nodes
     */
    private void addFileToTree(ScannedFile file, FolderNode root,
                               Map<String, FolderNode> folderMap) {
        String path = file.getPath();
        String[] parts = path.split("/");

        // Navigate/create folder structure
        FolderNode currentFolder = root;
        StringBuilder currentPath = new StringBuilder();

        // Process all path segments except the last one (which is the filename)
        for (int i = 0; i < parts.length - 1; i++) {
            String folderName = parts[i];
            if (folderName.isEmpty()) continue;

            currentPath.append("/").append(folderName);
            String fullPath = currentPath.toString();

            // Get or create folder node
            FolderNode folder = folderMap.get(fullPath);
            if (folder == null) {
                folder = new FolderNode(fullPath);
                folder.name = folderName;
                currentFolder.children.add(folder);
                folderMap.put(fullPath, folder);
            }
            currentFolder = folder;
        }

        // Add file to its parent folder
        String fileName = parts[parts.length - 1];
        FileNode fileNode = new FileNode(
            file.getPath(),
            fileName,
            file.getId(),
            file.getSize(),
            file.getExtension(),
            file.getIsDuplicate(),
            file.getZone()
        );
        currentFolder.children.add(fileNode);
    }

    /**
     * Convert internal tree node to DTO.
     * Calculates folder statistics in a single pass (bottom-up).
     *
     * @param node Tree node to convert
     * @param sourceId Source ID for zone lookup
     * @param folderZoneMap Map of folder paths to explicit zones
     * @return FolderNodeDto with pre-calculated statistics and zone information
     */
    private FolderNodeDto convertToDto(TreeNode node, UUID sourceId, Map<String, tech.zaisys.archivum.api.enums.Zone> folderZoneMap) {
        return convertToDto(node, sourceId, folderZoneMap, null);
    }

    /**
     * Convert internal tree node to DTO with parent zone for inheritance.
     *
     * @param node Tree node to convert
     * @param sourceId Source ID for zone lookup
     * @param folderZoneMap Map of folder paths to explicit zones
     * @param parentZoneResult Parent folder's zone result (for file inheritance)
     * @return FolderNodeDto with pre-calculated statistics and zone information
     */
    private FolderNodeDto convertToDto(TreeNode node, UUID sourceId,
                                       Map<String, tech.zaisys.archivum.api.enums.Zone> folderZoneMap,
                                       FolderZoneService.ZoneResult parentZoneResult) {
        if (node instanceof FileNode) {
            FileNode fileNode = (FileNode) node;

            // Determine file's zone (explicit or inherited from parent folder)
            tech.zaisys.archivum.api.enums.Zone fileZone = fileNode.zone;
            boolean isInherited = false;

            if (fileZone == null && parentZoneResult != null) {
                // File has no explicit zone, inherit from parent folder
                fileZone = parentZoneResult.zone();
                isInherited = true;
            }

            return FolderNodeDto.builder()
                .name(fileNode.name)
                .path(fileNode.path)
                .type(FolderNodeDto.NodeType.FILE)
                .fileId(fileNode.fileId)
                .size(fileNode.size)
                .extension(fileNode.extension)
                .isDuplicate(fileNode.isDuplicate)
                .zone(fileZone)
                .isInherited(isInherited)
                .children(List.of())
                .build();
        } else {
            FolderNode folderNode = (FolderNode) node;

            // Get zone for this folder (with inheritance)
            FolderZoneService.ZoneResult zoneResult = folderZoneService.getZoneForFolder(
                sourceId, folderNode.path, folderZoneMap);

            // Convert children and accumulate stats in a single pass
            int totalFileCount = 0;
            long totalSize = 0;
            List<FolderNodeDto> childDtos = new ArrayList<>(folderNode.children.size());

            // Sort children: folders first, then files
            List<TreeNode> sortedChildren = folderNode.children.stream()
                .sorted(Comparator.comparing(n -> {
                    if (n instanceof FolderNode) return "0_" + ((FolderNode) n).name;
                    else return "1_" + ((FileNode) n).name;
                }))
                .toList();

            // Process children and accumulate stats
            for (TreeNode child : sortedChildren) {
                // Pass this folder's zone to children so files can inherit
                FolderNodeDto childDto = convertToDto(child, sourceId, folderZoneMap, zoneResult);
                childDtos.add(childDto);

                // Accumulate stats from child
                if (child instanceof FileNode) {
                    totalFileCount++;
                    totalSize += ((FileNode) child).size;
                } else {
                    // Add child folder's aggregated stats
                    totalFileCount += (childDto.getFileCount() != null ? childDto.getFileCount() : 0);
                    totalSize += (childDto.getTotalSize() != null ? childDto.getTotalSize() : 0);
                }
            }

            return FolderNodeDto.builder()
                .name(folderNode.name)
                .path(folderNode.path)
                .type(FolderNodeDto.NodeType.FOLDER)
                .zone(zoneResult != null ? zoneResult.zone() : null)
                .isInherited(zoneResult != null ? zoneResult.isInherited() : null)
                .children(childDtos)
                .fileCount(totalFileCount)
                .totalSize(totalSize)
                .build();
        }
    }

    // Internal tree node classes
    private abstract static class TreeNode {
        String path;
        String name;

        TreeNode(String path) {
            this.path = path;
        }
    }

    private static class FolderNode extends TreeNode {
        List<TreeNode> children = new ArrayList<>();

        FolderNode(String path) {
            super(path);
        }
    }

    private static class FileNode extends TreeNode {
        UUID fileId;
        long size;
        String extension;
        boolean isDuplicate;
        tech.zaisys.archivum.api.enums.Zone zone;

        FileNode(String path, String name, UUID fileId, long size, String extension, boolean isDuplicate, tech.zaisys.archivum.api.enums.Zone zone) {
            super(path);
            this.name = name;
            this.fileId = fileId;
            this.size = size;
            this.extension = extension;
            this.isDuplicate = isDuplicate;
            this.zone = zone;
        }
    }
}
