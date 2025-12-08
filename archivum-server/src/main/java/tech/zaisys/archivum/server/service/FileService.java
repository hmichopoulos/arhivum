package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.api.dto.FileBatchDto;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.server.domain.ScannedFile;
import tech.zaisys.archivum.server.domain.Source;
import tech.zaisys.archivum.server.mapper.FileMapper;
import tech.zaisys.archivum.server.repository.ScannedFileRepository;
import tech.zaisys.archivum.server.repository.SourceRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing scanned files.
 * Handles file batch ingestion and retrieval.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileService {

    private final ScannedFileRepository fileRepository;
    private final SourceRepository sourceRepository;
    private final FileMapper fileMapper;

    /**
     * Ingest a batch of files from the scanner.
     * Uses per-file error handling - continues on individual file failures.
     * Updates source statistics in real-time.
     *
     * @param batch Batch of files to ingest
     * @return Result summary with success/failure counts
     */
    @Transactional
    public FileBatchResult ingestBatch(FileBatchDto batch) {
        log.info("Ingesting batch {} for source {} ({} files)",
            batch.getBatchNumber(), batch.getSourceId(), batch.getFiles().size());

        // Verify source exists
        Source source = sourceRepository.findById(batch.getSourceId())
            .orElseThrow(() -> new IllegalArgumentException(
                "Source not found: " + batch.getSourceId()));

        FileBatchResult result = new FileBatchResult();
        result.setBatchNumber(batch.getBatchNumber());
        result.setTotalFiles(batch.getFiles().size());

        long totalSize = 0;
        List<UUID> successfulIds = new ArrayList<>();
        List<FileBatchResult.FileError> errors = new ArrayList<>();

        // Process each file individually (per-file error handling)
        for (FileDto fileDto : batch.getFiles()) {
            try {
                ScannedFile entity = fileMapper.toEntity(fileDto, source);
                ScannedFile saved = fileRepository.save(entity);
                successfulIds.add(saved.getId());
                totalSize += fileDto.getSize();
                result.incrementSuccessCount();
            } catch (Exception e) {
                log.warn("Failed to save file {} from batch {}: {}",
                    fileDto.getPath(), batch.getBatchNumber(), e.getMessage());
                errors.add(new FileBatchResult.FileError(
                    fileDto.getPath(),
                    e.getMessage()
                ));
                result.incrementFailureCount();
            }
        }

        // Update source statistics
        updateSourceStatistics(source, result.getSuccessCount(), totalSize);

        result.setSuccessfulFileIds(successfulIds);
        result.setErrors(errors);

        log.info("Batch {} complete: {} succeeded, {} failed",
            batch.getBatchNumber(), result.getSuccessCount(), result.getFailureCount());

        return result;
    }

    /**
     * Update source statistics incrementally.
     *
     * @param source Source to update
     * @param filesAdded Number of files added
     * @param sizeAdded Size in bytes added
     */
    private void updateSourceStatistics(Source source, long filesAdded, long sizeAdded) {
        source.setProcessedFiles(source.getProcessedFiles() + filesAdded);
        source.setProcessedSize(source.getProcessedSize() + sizeAdded);
        sourceRepository.save(source);
    }

    /**
     * Find file by ID.
     *
     * @param id File ID
     * @return Optional file DTO
     */
    @Transactional(readOnly = true)
    public Optional<FileDto> findById(UUID id) {
        return fileRepository.findById(id).map(fileMapper::toDto);
    }
}
