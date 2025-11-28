package tech.zaisys.archivum.scanner.service;

import lombok.extern.slf4j.Slf4j;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.api.enums.FileStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.UUID;

/**
 * Service for extracting file metadata.
 * Extracts basic metadata like name, size, dates, and MIME type.
 * EXIF extraction is handled separately in Milestone 3.
 */
@Slf4j
public class MetadataService {

    /**
     * Extract metadata from a file and create a FileDto.
     *
     * @param file Path to the file
     * @param sourceId Source ID this file belongs to
     * @param hash SHA-256 hash of the file
     * @return FileDto with metadata
     * @throws IOException if metadata cannot be read
     */
    public FileDto extractMetadata(Path file, UUID sourceId, String hash) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

        String fileName = file.getFileName().toString();
        String extension = getFileExtension(fileName);
        String mimeType = detectMimeType(file);

        return FileDto.builder()
            .id(UUID.randomUUID())
            .sourceId(sourceId)
            .path(file.toString())
            .name(fileName)
            .extension(extension)
            .size(attrs.size())
            .sha256(hash)
            .modifiedAt(fileTimeToInstant(attrs.lastModifiedTime()))
            .createdAt(fileTimeToInstant(attrs.creationTime()))
            .accessedAt(fileTimeToInstant(attrs.lastAccessTime()))
            .mimeType(mimeType)
            .exif(null) // Will be populated in Milestone 3
            .status(FileStatus.HASHED)
            .isDuplicate(false)
            .scannedAt(Instant.now())
            .build();
    }

    /**
     * Get file extension from filename.
     * Returns empty string if no extension.
     *
     * @param fileName File name
     * @return File extension without dot, or empty string
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }

    /**
     * Detect MIME type of a file.
     * Uses Files.probeContentType which checks file extension and magic bytes.
     *
     * @param file Path to the file
     * @return MIME type or "application/octet-stream" if unknown
     */
    private String detectMimeType(Path file) {
        try {
            String mimeType = Files.probeContentType(file);
            return mimeType != null ? mimeType : "application/octet-stream";
        } catch (IOException e) {
            log.warn("Failed to detect MIME type for: {}", file, e);
            return "application/octet-stream";
        }
    }

    /**
     * Convert FileTime to Instant.
     *
     * @param fileTime FileTime
     * @return Instant or null if fileTime is null
     */
    private Instant fileTimeToInstant(FileTime fileTime) {
        return fileTime != null ? fileTime.toInstant() : null;
    }

    /**
     * Check if a file is an image based on its extension.
     * Used to determine if EXIF extraction should be attempted.
     *
     * @param file Path to the file
     * @return true if image file, false otherwise
     */
    public boolean isImageFile(Path file) {
        String extension = getFileExtension(file.getFileName().toString());
        return switch (extension) {
            case "jpg", "jpeg", "png", "tiff", "tif", "heif", "heic", "webp" -> true;
            default -> false;
        };
    }

    /**
     * Check if a file is a video based on its extension.
     *
     * @param file Path to the file
     * @return true if video file, false otherwise
     */
    public boolean isVideoFile(Path file) {
        String extension = getFileExtension(file.getFileName().toString());
        return switch (extension) {
            case "mp4", "mov", "avi", "mkv", "webm", "flv", "wmv", "m4v" -> true;
            default -> false;
        };
    }

    /**
     * Check if a file is an archive based on its extension.
     *
     * @param file Path to the file
     * @return true if archive file, false otherwise
     */
    public boolean isArchiveFile(Path file) {
        String extension = getFileExtension(file.getFileName().toString());
        return switch (extension) {
            case "zip", "tar", "gz", "bz2", "xz", "7z", "rar",
                 "tgz", "tbz", "txz", "iso", "img", "tib", "bak" -> true;
            default -> false;
        };
    }
}
