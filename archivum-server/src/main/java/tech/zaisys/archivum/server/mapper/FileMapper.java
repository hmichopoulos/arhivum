package tech.zaisys.archivum.server.mapper;

import org.springframework.stereotype.Component;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.server.domain.ScannedFile;
import tech.zaisys.archivum.server.domain.Source;

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
            .createdAt(dto.getCreatedAt())
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
     *
     * @param entity ScannedFile entity
     * @return FileDto
     */
    public FileDto toDto(ScannedFile entity) {
        return FileDto.builder()
            .id(entity.getId())
            .sourceId(entity.getSource().getId())
            .path(entity.getPath())
            .name(entity.getName())
            .extension(entity.getExtension())
            .size(entity.getSize())
            .sha256(entity.getSha256())
            .modifiedAt(entity.getModifiedAt())
            .createdAt(entity.getCreatedAt())
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
