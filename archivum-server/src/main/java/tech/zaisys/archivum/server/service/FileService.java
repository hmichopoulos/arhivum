package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

        long totalSize = 0;
        int successCount = 0;
        int failureCount = 0;
        List<UUID> successfulIds = new ArrayList<>();
        List<FileBatchResult.FileError> errors = new ArrayList<>();

        // Process each file individually (per-file error handling)
        for (FileDto fileDto : batch.getFiles()) {
            try {
                ScannedFile entity = fileMapper.toEntity(fileDto, source);
                ScannedFile saved = fileRepository.save(entity);
                successfulIds.add(saved.getId());
                totalSize += fileDto.getSize();
                successCount++;
            } catch (Exception e) {
                log.warn("Failed to save file {} from batch {}: {}",
                    fileDto.getPath(), batch.getBatchNumber(), e.getMessage());
                errors.add(new FileBatchResult.FileError(
                    fileDto.getPath(),
                    e.getMessage()
                ));
                failureCount++;
            }
        }

        // Update source statistics
        updateSourceStatistics(source, successCount, totalSize);

        // Build immutable result
        FileBatchResult result = FileBatchResult.builder()
            .batchNumber(batch.getBatchNumber())
            .totalFiles(batch.getFiles().size())
            .successCount(successCount)
            .failureCount(failureCount)
            .successfulFileIds(successfulIds)
            .errors(errors)
            .build();

        log.info("Batch {} complete: {} succeeded, {} failed",
            batch.getBatchNumber(), successCount, failureCount);

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

    /**
     * Query files with optional filters and pagination.
     *
     * @param sourceId Source ID (required)
     * @param extension File extension filter (optional)
     * @param isDuplicate Duplicate filter (optional)
     * @param page Page number (0-indexed)
     * @param size Page size
     * @return List of file DTOs
     */
    @Transactional(readOnly = true)
    public List<FileDto> queryFiles(UUID sourceId, String extension, Boolean isDuplicate,
                                     int page, int size) {
        // Create pageable with sorting by path
        Pageable pageable = PageRequest.of(page, size, Sort.by("path").ascending());

        // Query with filters
        Page<ScannedFile> resultPage = fileRepository.queryFiles(
            sourceId, extension, isDuplicate, pageable);

        // Map to DTOs
        return resultPage.map(fileMapper::toDto).getContent();
    }

    /**
     * Find all duplicate files (files with the same SHA-256 hash).
     *
     * @param fileId File ID to find duplicates for
     * @return List of all files with the same hash (including the original)
     */
    @Transactional(readOnly = true)
    public List<FileDto> findDuplicates(UUID fileId) {
        // Get the original file
        ScannedFile file = fileRepository.findById(fileId)
            .orElseThrow(() -> new IllegalArgumentException("File not found: " + fileId));

        // Find all files with the same hash
        List<ScannedFile> duplicates = fileRepository.findBySha256(file.getSha256());

        // Map to DTOs
        return duplicates.stream()
            .map(fileMapper::toDto)
            .toList();
    }
}
