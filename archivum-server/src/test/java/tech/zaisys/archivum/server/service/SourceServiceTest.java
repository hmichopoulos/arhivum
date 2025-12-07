package tech.zaisys.archivum.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.zaisys.archivum.api.dto.CreateSourceRequest;
import tech.zaisys.archivum.api.dto.PhysicalId;
import tech.zaisys.archivum.api.dto.SourceDto;
import tech.zaisys.archivum.api.enums.ScanStatus;
import tech.zaisys.archivum.api.enums.SourceType;
import tech.zaisys.archivum.server.domain.Source;
import tech.zaisys.archivum.server.repository.SourceRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SourceService.
 */
@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock
    private SourceRepository sourceRepository;

    @InjectMocks
    private SourceService sourceService;

    private UUID testId;
    private Source testSource;
    private CreateSourceRequest testRequest;

    @BeforeEach
    void setUp() {
        testId = UUID.randomUUID();

        testSource = Source.builder()
            .id(testId)
            .name("Test Disk")
            .type(SourceType.DISK)
            .rootPath("/mnt/test")
            .status(ScanStatus.PENDING)
            .postponed(false)
            .totalFiles(0L)
            .totalSize(0L)
            .processedFiles(0L)
            .processedSize(0L)
            .build();

        testRequest = CreateSourceRequest.builder()
            .id(testId)
            .name("Test Disk")
            .type(SourceType.DISK)
            .rootPath("/mnt/test")
            .postponed(false)
            .build();
    }

    @Test
    void testCreateSource_Success() {
        // Given
        when(sourceRepository.save(any(Source.class))).thenReturn(testSource);

        // When
        SourceDto result = sourceService.createSource(testRequest);

        // Then
        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals("Test Disk", result.getName());
        assertEquals(SourceType.DISK, result.getType());
        assertEquals("/mnt/test", result.getRootPath());
        verify(sourceRepository).save(any(Source.class));
    }

    @Test
    void testCreateSource_WithParent() {
        // Given
        UUID parentId = UUID.randomUUID();
        Source parent = Source.builder()
            .id(parentId)
            .name("Parent Disk")
            .type(SourceType.DISK)
            .rootPath("/mnt/parent")
            .status(ScanStatus.PENDING)
            .build();

        testRequest.setParentSourceId(parentId);

        when(sourceRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(sourceRepository.save(any(Source.class))).thenAnswer(invocation -> {
            Source saved = invocation.getArgument(0);
            saved.setParent(parent);
            return saved;
        });

        // When
        SourceDto result = sourceService.createSource(testRequest);

        // Then
        assertNotNull(result);
        assertEquals(parentId, result.getParentSourceId());
        verify(sourceRepository).findById(parentId);
    }

    @Test
    void testCreateSource_ParentNotFound() {
        // Given
        UUID parentId = UUID.randomUUID();
        testRequest.setParentSourceId(parentId);
        when(sourceRepository.findById(parentId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            sourceService.createSource(testRequest);
        });
    }

    @Test
    void testFindById_Found() {
        // Given
        when(sourceRepository.findById(testId)).thenReturn(Optional.of(testSource));

        // When
        Optional<SourceDto> result = sourceService.findById(testId);

        // Then
        assertTrue(result.isPresent());
        assertEquals(testId, result.get().getId());
        assertEquals("Test Disk", result.get().getName());
    }

    @Test
    void testFindById_NotFound() {
        // Given
        when(sourceRepository.findById(testId)).thenReturn(Optional.empty());

        // When
        Optional<SourceDto> result = sourceService.findById(testId);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testFindAll() {
        // Given
        when(sourceRepository.findAllByOrderByCreatedAtDesc())
            .thenReturn(List.of(testSource));

        // When
        List<SourceDto> results = sourceService.findAll();

        // Then
        assertEquals(1, results.size());
        assertEquals("Test Disk", results.get(0).getName());
    }

    @Test
    void testFindRootSources() {
        // Given
        when(sourceRepository.findByParentIsNull())
            .thenReturn(List.of(testSource));

        // When
        List<SourceDto> results = sourceService.findRootSources();

        // Then
        assertEquals(1, results.size());
        assertNull(results.get(0).getParentSourceId());
    }

    @Test
    void testFindChildren() {
        // Given
        UUID parentId = UUID.randomUUID();
        Source child = Source.builder()
            .id(UUID.randomUUID())
            .name("Child Partition")
            .type(SourceType.PARTITION)
            .rootPath("/mnt/test/partition1")
            .status(ScanStatus.PENDING)
            .build();

        when(sourceRepository.findByParentId(parentId)).thenReturn(List.of(child));

        // When
        List<SourceDto> results = sourceService.findChildren(parentId);

        // Then
        assertEquals(1, results.size());
        assertEquals("Child Partition", results.get(0).getName());
    }

    @Test
    void testLinkParentChild_Success() {
        // Given
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        Source parent = Source.builder()
            .id(parentId)
            .name("Parent")
            .build();

        Source child = Source.builder()
            .id(childId)
            .name("Child")
            .build();

        when(sourceRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(sourceRepository.findById(childId)).thenReturn(Optional.of(child));
        when(sourceRepository.save(parent)).thenReturn(parent);

        // When
        sourceService.linkParentChild(parentId, childId);

        // Then
        verify(sourceRepository).save(parent);
        assertEquals(parent, child.getParent());
    }

    @Test
    void testLinkParentChild_ParentNotFound() {
        // Given
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        when(sourceRepository.findById(parentId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            sourceService.linkParentChild(parentId, childId);
        });
    }

    @Test
    void testDeleteSource() {
        // Given
        when(sourceRepository.findById(testId)).thenReturn(Optional.of(testSource));

        // When
        sourceService.deleteSource(testId);

        // Then
        verify(sourceRepository).delete(testSource);
    }

    @Test
    void testDeleteSource_NotFound() {
        // Given
        when(sourceRepository.findById(testId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            sourceService.deleteSource(testId);
        });
    }
}
