package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * Build a folder tree for a given source.
     *
     * @param sourceId Source ID
     * @return Root folder node containing the entire tree
     */
    @Transactional(readOnly = true)
    public FolderNodeDto buildTree(UUID sourceId) {
        log.debug("Building folder tree for source {}", sourceId);

        // Fetch all files for this source
        List<ScannedFile> files = fileRepository.findBySourceId(sourceId);

        // Build the tree
        return buildTreeFromFiles(files);
    }

    /**
     * Build a folder tree from a list of files.
     *
     * @param files List of files
     * @return Root folder node
     */
    private FolderNodeDto buildTreeFromFiles(List<ScannedFile> files) {
        // Create root node
        Map<String, FolderNode> folderMap = new HashMap<>();
        FolderNode root = new FolderNode("");

        // Process each file
        for (ScannedFile file : files) {
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

        // Convert to DTO
        return convertToDto(root);
    }

    /**
     * Convert internal tree node to DTO.
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

            // Convert children
            List<FolderNodeDto> childDtos = folderNode.children.stream()
                .sorted(Comparator.comparing(n -> {
                    // Folders first, then files
                    if (n instanceof FolderNode) return "0_" + ((FolderNode) n).name;
                    else return "1_" + ((FileNode) n).name;
                }))
                .map(this::convertToDto)
                .toList();

            // Calculate stats
            int fileCount = calculateFileCount(folderNode);
            long totalSize = calculateTotalSize(folderNode);

            return FolderNodeDto.builder()
                .name(folderNode.name)
                .path(folderNode.path)
                .type(FolderNodeDto.NodeType.FOLDER)
                .children(childDtos)
                .fileCount(fileCount)
                .totalSize(totalSize)
                .build();
        }
    }

    /**
     * Calculate total number of files in a folder (recursive).
     */
    private int calculateFileCount(FolderNode folder) {
        int count = 0;
        for (TreeNode child : folder.children) {
            if (child instanceof FileNode) {
                count++;
            } else {
                count += calculateFileCount((FolderNode) child);
            }
        }
        return count;
    }

    /**
     * Calculate total size of all files in a folder (recursive).
     */
    private long calculateTotalSize(FolderNode folder) {
        long size = 0;
        for (TreeNode child : folder.children) {
            if (child instanceof FileNode) {
                size += ((FileNode) child).size;
            } else {
                size += calculateTotalSize((FolderNode) child);
            }
        }
        return size;
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
