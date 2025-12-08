package tech.zaisys.archivum.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.zaisys.archivum.api.dto.FileBatchDto;
import tech.zaisys.archivum.api.dto.FileDto;
import tech.zaisys.archivum.api.enums.FileStatus;
import tech.zaisys.archivum.api.enums.ScanStatus;
import tech.zaisys.archivum.api.enums.SourceType;
import tech.zaisys.archivum.server.domain.ScannedFile;
import tech.zaisys.archivum.server.domain.Source;
import tech.zaisys.archivum.server.mapper.FileMapper;
import tech.zaisys.archivum.server.repository.ScannedFileRepository;
import tech.zaisys.archivum.server.repository.SourceRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FileService.
 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private ScannedFileRepository fileRepository;

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private FileMapper fileMapper;

    @InjectMocks
    private FileService fileService;

    private UUID sourceId;
    private Source testSource;
    private FileDto testFileDto;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID();

        testSource = Source.builder()
            .id(sourceId)
            .name("Test Disk")
            .type(SourceType.DISK)
            .rootPath("/mnt/test")
            .status(ScanStatus.SCANNING)
            .postponed(false)
            .totalFiles(0L)
            .totalSize(0L)
            .processedFiles(0L)
            .processedSize(0L)
            .build();

        testFileDto = FileDto.builder()
            .id(UUID.randomUUID())
            .sourceId(sourceId)
            .path("photos/vacation.jpg")
            .name("vacation.jpg")
            .extension("jpg")
            .size(1024000L)
            .sha256("a1b2c3d4e5f6" + "0".repeat(52))
            .modifiedAt(Instant.now())
            .createdAt(Instant.now())
            .mimeType("image/jpeg")
            .status(FileStatus.HASHED)
            .isDuplicate(false)
            .scannedAt(Instant.now())
            .build();
    }

    @Test
    void testIngestBatch_Success() {
        // Given
        List<FileDto> files = List.of(testFileDto);

        FileBatchDto batch = FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(1)
            .files(files)
            .build();

        ScannedFile mockEntity = ScannedFile.builder()
            .id(testFileDto.getId())
            .source(testSource)
            .path(testFileDto.getPath())
            .size(testFileDto.getSize())
            .isDuplicate(false)
            .build();

        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(testSource));
        when(fileMapper.toEntity(any(FileDto.class), any(Source.class))).thenReturn(mockEntity);
        when(fileRepository.save(any(ScannedFile.class))).thenAnswer(invocation -> {
            ScannedFile file = invocation.getArgument(0);
            return file;
        });

        // When
        FileBatchResult result = fileService.ingestBatch(batch);

        // Then
        assertNotNull(result);
        assertEquals(1, result.getBatchNumber());
        assertEquals(1, result.getTotalFiles());
        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        assertEquals(1, result.getSuccessfulFileIds().size());
        assertTrue(result.getErrors().isEmpty());

        verify(sourceRepository).findById(sourceId);
        verify(fileRepository).save(any(ScannedFile.class));
        verify(sourceRepository).save(testSource);

        // Verify source statistics updated
        assertEquals(1L, testSource.getProcessedFiles());
        assertEquals(1024000L, testSource.getProcessedSize());
    }

    @Test
    void testIngestBatch_PartialFailure() {
        // Given
        FileDto validFile = testFileDto;
        FileDto invalidFile = FileDto.builder()
            .id(UUID.randomUUID())
            .sourceId(sourceId)
            .path("invalid/file.jpg")
            .name("file.jpg")
            .extension("jpg")
            .size(512000L)
            .sha256("invalid_hash")
            .scannedAt(Instant.now())
            .build();

        List<FileDto> files = List.of(validFile, invalidFile);

        FileBatchDto batch = FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(1)
            .files(files)
            .build();

        ScannedFile validEntity = ScannedFile.builder()
            .id(validFile.getId())
            .source(testSource)
            .size(validFile.getSize())
            .build();

        ScannedFile invalidEntity = ScannedFile.builder()
            .id(invalidFile.getId())
            .source(testSource)
            .size(invalidFile.getSize())
            .build();

        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(testSource));
        when(fileMapper.toEntity(any(FileDto.class), any(Source.class)))
            .thenReturn(validEntity)
            .thenReturn(invalidEntity);
        when(fileRepository.save(any(ScannedFile.class)))
            .thenAnswer(invocation -> invocation.getArgument(0)) // First file succeeds
            .thenThrow(new RuntimeException("Database error")); // Second file fails

        // When
        FileBatchResult result = fileService.ingestBatch(batch);

        // Then
        assertEquals(2, result.getTotalFiles());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(1, result.getSuccessfulFileIds().size());
        assertEquals(1, result.getErrors().size());
        assertEquals("invalid/file.jpg", result.getErrors().get(0).getPath());
        assertTrue(result.getErrors().get(0).getError().contains("Database error"));

        // Verify statistics updated only for successful file
        assertEquals(1L, testSource.getProcessedFiles());
        assertEquals(1024000L, testSource.getProcessedSize());
    }

    @Test
    void testIngestBatch_SourceNotFound() {
        // Given
        FileBatchDto batch = FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(1)
            .files(List.of(testFileDto))
            .build();

        when(sourceRepository.findById(sourceId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            fileService.ingestBatch(batch);
        });

        verify(sourceRepository).findById(sourceId);
        verify(fileRepository, never()).save(any());
    }

    @Test
    void testIngestBatch_PreserveDuplicateFlag() {
        // Given
        testFileDto.setIsDuplicate(true); // Scanner marked as duplicate

        FileBatchDto batch = FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(1)
            .files(List.of(testFileDto))
            .build();

        ScannedFile duplicateEntity = ScannedFile.builder()
            .id(testFileDto.getId())
            .source(testSource)
            .isDuplicate(true)
            .size(testFileDto.getSize())
            .build();

        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(testSource));
        when(fileMapper.toEntity(any(FileDto.class), any(Source.class))).thenReturn(duplicateEntity);
        when(fileRepository.save(any(ScannedFile.class))).thenAnswer(invocation -> {
            ScannedFile file = invocation.getArgument(0);
            // Verify isDuplicate is true from scanner
            assertTrue(file.getIsDuplicate());
            return file;
        });

        // When
        FileBatchResult result = fileService.ingestBatch(batch);

        // Then
        assertEquals(1, result.getSuccessCount());
        verify(fileRepository).save(any(ScannedFile.class));
    }

    @Test
    void testIngestBatch_NullDuplicateFlagDefaultsFalse() {
        // Given
        testFileDto.setIsDuplicate(null); // Null duplicate flag

        FileBatchDto batch = FileBatchDto.builder()
            .sourceId(sourceId)
            .batchNumber(1)
            .files(List.of(testFileDto))
            .build();

        ScannedFile entityWithDefaultFalse = ScannedFile.builder()
            .id(testFileDto.getId())
            .source(testSource)
            .isDuplicate(false)
            .size(testFileDto.getSize())
            .build();

        when(sourceRepository.findById(sourceId)).thenReturn(Optional.of(testSource));
        when(fileMapper.toEntity(any(FileDto.class), any(Source.class))).thenReturn(entityWithDefaultFalse);
        when(fileRepository.save(any(ScannedFile.class))).thenAnswer(invocation -> {
            ScannedFile file = invocation.getArgument(0);
            // Verify isDuplicate defaults to false
            assertFalse(file.getIsDuplicate());
            return file;
        });

        // When
        FileBatchResult result = fileService.ingestBatch(batch);

        // Then
        assertEquals(1, result.getSuccessCount());
        verify(fileRepository).save(any(ScannedFile.class));
    }

    @Test
    void testFindById_Found() {
        // Given
        UUID fileId = UUID.randomUUID();
        ScannedFile entity = ScannedFile.builder()
            .id(fileId)
            .source(testSource)
            .path("photos/test.jpg")
            .name("test.jpg")
            .extension("jpg")
            .size(1024000L)
            .sha256("hash123" + "0".repeat(57))
            .status(FileStatus.HASHED)
            .isDuplicate(false)
            .scannedAt(Instant.now())
            .build();

        FileDto expectedDto = FileDto.builder()
            .id(fileId)
            .sourceId(sourceId)
            .path("photos/test.jpg")
            .name("test.jpg")
            .extension("jpg")
            .size(1024000L)
            .build();

        when(fileRepository.findById(fileId)).thenReturn(Optional.of(entity));
        when(fileMapper.toDto(entity)).thenReturn(expectedDto);

        // When
        Optional<FileDto> result = fileService.findById(fileId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(fileId, result.get().getId());
        assertEquals("photos/test.jpg", result.get().getPath());
        assertEquals("test.jpg", result.get().getName());
        assertEquals(sourceId, result.get().getSourceId());

        verify(fileRepository).findById(fileId);
        verify(fileMapper).toDto(entity);
    }

    @Test
    void testFindById_NotFound() {
        // Given
        UUID fileId = UUID.randomUUID();
        when(fileRepository.findById(fileId)).thenReturn(Optional.empty());

        // When
        Optional<FileDto> result = fileService.findById(fileId);

        // Then
        assertFalse(result.isPresent());
        verify(fileRepository).findById(fileId);
    }

    @Test
    void testToDto_WithOriginalFile() {
        // Given
        ScannedFile original = ScannedFile.builder()
            .id(UUID.randomUUID())
            .source(testSource)
            .path("photos/original.jpg")
            .name("original.jpg")
            .extension("jpg")
            .size(1024000L)
            .scannedAt(Instant.now())
            .build();

        ScannedFile duplicate = ScannedFile.builder()
            .id(UUID.randomUUID())
            .source(testSource)
            .path("photos/duplicate.jpg")
            .name("duplicate.jpg")
            .extension("jpg")
            .size(1024000L)
            .isDuplicate(true)
            .originalFile(original)
            .scannedAt(Instant.now())
            .build();

        FileDto duplicateDto = FileDto.builder()
            .id(duplicate.getId())
            .sourceId(sourceId)
            .path("photos/duplicate.jpg")
            .name("duplicate.jpg")
            .isDuplicate(true)
            .originalFileId(original.getId())
            .build();

        when(fileRepository.findById(duplicate.getId())).thenReturn(Optional.of(duplicate));
        when(fileMapper.toDto(duplicate)).thenReturn(duplicateDto);

        // When
        Optional<FileDto> result = fileService.findById(duplicate.getId());

        // Then
        assertTrue(result.isPresent());
        assertTrue(result.get().getIsDuplicate());
        assertEquals(original.getId(), result.get().getOriginalFileId());
        verify(fileMapper).toDto(duplicate);
    }

    @Test
    void testFindDuplicates_WithMultipleDuplicates() {
        // Given: a file with multiple duplicates (same SHA-256 hash)
        UUID fileId = UUID.randomUUID();
        String sha256Hash = "a1b2c3d4e5f6...";

        ScannedFile originalFile = ScannedFile.builder()
            .id(fileId)
            .source(testSource)
            .path("/photos/original.jpg")
            .name("original.jpg")
            .sha256(sha256Hash)
            .size(1024000L)
            .build();

        ScannedFile duplicate1 = ScannedFile.builder()
            .id(UUID.randomUUID())
            .source(testSource)
            .path("/backup/photo.jpg")
            .name("photo.jpg")
            .sha256(sha256Hash)
            .size(1024000L)
            .isDuplicate(true)
            .build();

        ScannedFile duplicate2 = ScannedFile.builder()
            .id(UUID.randomUUID())
            .source(testSource)
            .path("/archive/image.jpg")
            .name("image.jpg")
            .sha256(sha256Hash)
            .size(1024000L)
            .isDuplicate(true)
            .build();

        List<ScannedFile> allDuplicates = List.of(originalFile, duplicate1, duplicate2);

        FileDto originalDto = FileDto.builder()
            .id(fileId)
            .path("/photos/original.jpg")
            .sha256(sha256Hash)
            .build();

        FileDto duplicate1Dto = FileDto.builder()
            .id(duplicate1.getId())
            .path("/backup/photo.jpg")
            .sha256(sha256Hash)
            .isDuplicate(true)
            .build();

        FileDto duplicate2Dto = FileDto.builder()
            .id(duplicate2.getId())
            .path("/archive/image.jpg")
            .sha256(sha256Hash)
            .isDuplicate(true)
            .build();

        when(fileRepository.findById(fileId)).thenReturn(Optional.of(originalFile));
        when(fileRepository.findBySha256(sha256Hash)).thenReturn(allDuplicates);
        when(fileMapper.toDto(originalFile)).thenReturn(originalDto);
        when(fileMapper.toDto(duplicate1)).thenReturn(duplicate1Dto);
        when(fileMapper.toDto(duplicate2)).thenReturn(duplicate2Dto);

        // When
        List<FileDto> result = fileService.findDuplicates(fileId);

        // Then
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(dto -> dto.getId().equals(fileId)));
        assertTrue(result.stream().anyMatch(dto -> dto.getId().equals(duplicate1.getId())));
        assertTrue(result.stream().anyMatch(dto -> dto.getId().equals(duplicate2.getId())));

        verify(fileRepository).findById(fileId);
        verify(fileRepository).findBySha256(sha256Hash);
        verify(fileMapper, times(3)).toDto(any(ScannedFile.class));
    }

    @Test
    void testFindDuplicates_NoDuplicates() {
        // Given: a file with no duplicates (unique hash)
        UUID fileId = UUID.randomUUID();
        String uniqueHash = "unique123...";

        ScannedFile uniqueFile = ScannedFile.builder()
            .id(fileId)
            .source(testSource)
            .path("/photos/unique.jpg")
            .name("unique.jpg")
            .sha256(uniqueHash)
            .size(1024000L)
            .build();

        FileDto uniqueDto = FileDto.builder()
            .id(fileId)
            .path("/photos/unique.jpg")
            .sha256(uniqueHash)
            .isDuplicate(false)
            .build();

        when(fileRepository.findById(fileId)).thenReturn(Optional.of(uniqueFile));
        when(fileRepository.findBySha256(uniqueHash)).thenReturn(List.of(uniqueFile));
        when(fileMapper.toDto(uniqueFile)).thenReturn(uniqueDto);

        // When
        List<FileDto> result = fileService.findDuplicates(fileId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(fileId, result.get(0).getId());
        assertFalse(result.get(0).getIsDuplicate());

        verify(fileRepository).findById(fileId);
        verify(fileRepository).findBySha256(uniqueHash);
    }

    @Test
    void testFindDuplicates_FileNotFound() {
        // Given: non-existent file ID
        UUID fileId = UUID.randomUUID();
        when(fileRepository.findById(fileId)).thenReturn(Optional.empty());

        // When/Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> fileService.findDuplicates(fileId)
        );

        assertTrue(exception.getMessage().contains("File not found"));
        verify(fileRepository).findById(fileId);
        verify(fileRepository, never()).findBySha256(any());
    }
}
