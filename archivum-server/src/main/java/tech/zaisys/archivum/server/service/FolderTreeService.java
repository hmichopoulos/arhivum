package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.api.dto.FolderNodeDto;
import tech.zaisys.archivum.server.domain.ScannedFile;
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

    private static final int PAGE_SIZE = 1000;

    /**
     * Build a folder tree for a given source.
     * Uses pagination to avoid loading all files into memory at once.
     *
     * @param sourceId Source ID
     * @return Root folder node containing the entire tree
     */
    @Transactional(readOnly = true)
    public FolderNodeDto buildTree(UUID sourceId) {
        log.debug("Building folder tree for source {}", sourceId);

        // Build the tree using pagination
        return buildTreeWithPagination(sourceId);
    }

    /**
     * Build a folder tree using pagination to process files in batches.
     * This avoids loading all files into memory at once.
     *
     * @param sourceId Source ID
     * @return Root folder node
     */
    private FolderNodeDto buildTreeWithPagination(UUID sourceId) {
        Map<String, FolderNode> folderMap = new HashMap<>();
        FolderNode root = new FolderNode("");

        int pageNumber = 0;
        Page<ScannedFile> page;

        do {
            Pageable pageable = PageRequest.of(pageNumber, PAGE_SIZE);
            page = fileRepository.findBySourceId(sourceId, pageable);

            // Process this batch of files
            for (ScannedFile file : page.getContent()) {
                addFileToTree(file, root, folderMap);
            }

            pageNumber++;
            log.debug("Processed page {}/{} ({} files)",
                pageNumber, page.getTotalPages(), page.getNumberOfElements());

        } while (page.hasNext());

        log.debug("Built tree from {} total files", pageNumber * PAGE_SIZE);

        return convertToDto(root);
    }

    /**
     * Build a folder tree from a list of files.
     * Used by tests and for small file sets.
     *
     * @param files List of files
     * @return Root folder node
     */
    private FolderNodeDto buildTreeFromFiles(List<ScannedFile> files) {
        Map<String, FolderNode> folderMap = new HashMap<>();
        FolderNode root = new FolderNode("");

        // Process each file
        for (ScannedFile file : files) {
            addFileToTree(file, root, folderMap);
        }

        return convertToDto(root);
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
            file.getIsDuplicate()
        );
        currentFolder.children.add(fileNode);
    }

    /**
     * Convert internal tree node to DTO.
     * Calculates folder statistics in a single pass (bottom-up).
     *
     * @return FolderNodeDto with pre-calculated statistics
     */
    private FolderNodeDto convertToDto(TreeNode node) {
        if (node instanceof FileNode) {
            FileNode fileNode = (FileNode) node;
            return FolderNodeDto.builder()
                .name(fileNode.name)
                .path(fileNode.path)
                .type(FolderNodeDto.NodeType.FILE)
                .fileId(fileNode.fileId)
                .size(fileNode.size)
                .extension(fileNode.extension)
                .isDuplicate(fileNode.isDuplicate)
                .children(List.of())
                .build();
        } else {
            FolderNode folderNode = (FolderNode) node;

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
                FolderNodeDto childDto = convertToDto(child);
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

        FileNode(String path, String name, UUID fileId, long size, String extension, boolean isDuplicate) {
            super(path);
            this.name = name;
            this.fileId = fileId;
            this.size = size;
            this.extension = extension;
            this.isDuplicate = isDuplicate;
        }
    }
}
