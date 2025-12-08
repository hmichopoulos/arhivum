package tech.zaisys.archivum.server.mapper;

import org.springframework.stereotype.Component;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.server.domain.ScannedFile;
import tech.zaisys.archivum.server.domain.Source;

import java.util.UUID;

/**
 * Mapper for converting between FileDto and ScannedFile entity.
 */
@Component
public class FileMapper {

    /**
     * Convert FileDto to ScannedFile entity.
     *
     * @param dto FileDto from scanner
     * @param source Source entity
     * @return ScannedFile entity
     */
    public ScannedFile toEntity(FileDto dto, Source source) {
        return ScannedFile.builder()
            .id(dto.getId())
            .source(source)
            .path(dto.getPath())
            .name(dto.getName())
            .extension(dto.getExtension())
            .size(dto.getSize())
            .sha256(dto.getSha256())
            .modifiedAt(dto.getModifiedAt())
            .fileCreatedAt(dto.getCreatedAt())
            .accessedAt(dto.getAccessedAt())
            .mimeType(dto.getMimeType())
            .exifMetadata(dto.getExif())
            .status(dto.getStatus())
            .isDuplicate(dto.getIsDuplicate() != null ? dto.getIsDuplicate() : false)
            .scannedAt(dto.getScannedAt())
            .build();
    }

    /**
     * Convert ScannedFile entity to FileDto.
     * Note: Assumes source is already loaded to avoid N+1 query.
     *
     * @param entity ScannedFile entity
     * @return FileDto
     */
    public FileDto toDto(ScannedFile entity) {
        return toDto(entity, entity.getSource().getId());
    }

    /**
     * Convert ScannedFile entity to FileDto with explicit sourceId.
     * Use this variant to avoid lazy loading when source is not fetched.
     *
     * @param entity ScannedFile entity
     * @param sourceId Source ID (avoids lazy load)
     * @return FileDto
     */
    public FileDto toDto(ScannedFile entity, UUID sourceId) {
        return FileDto.builder()
            .id(entity.getId())
            .sourceId(sourceId)
            .path(entity.getPath())
            .name(entity.getName())
            .extension(entity.getExtension())
            .size(entity.getSize())
            .sha256(entity.getSha256())
            .modifiedAt(entity.getModifiedAt())
            .createdAt(entity.getFileCreatedAt())
            .accessedAt(entity.getAccessedAt())
            .mimeType(entity.getMimeType())
            .exif(entity.getExifMetadata())
            .status(entity.getStatus())
            .isDuplicate(entity.getIsDuplicate())
            .originalFileId(entity.getOriginalFile() != null ?
                entity.getOriginalFile().getId() : null)
            .scannedAt(entity.getScannedAt())
            .build();
    }
}
