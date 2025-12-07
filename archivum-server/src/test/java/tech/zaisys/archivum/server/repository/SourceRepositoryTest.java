package tech.zaisys.archivum.server.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.zaisys.archivum.api.dto.PhysicalId;
import tech.zaisys.archivum.api.enums.ScanStatus;
import tech.zaisys.archivum.api.enums.SourceType;
import tech.zaisys.archivum.server.domain.Source;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SourceRepository using Testcontainers.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SourceRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("archivum_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SourceRepository sourceRepository;

    private Source parentSource;
    private Source childSource;

    @BeforeEach
    void setUp() {
        sourceRepository.deleteAll();

        parentSource = Source.builder()
            .name("Parent Disk")
            .type(SourceType.DISK)
            .rootPath("/mnt/parent")
            .status(ScanStatus.PENDING)
            .postponed(false)
            .totalFiles(0L)
            .totalSize(0L)
            .processedFiles(0L)
            .processedSize(0L)
            .build();

        childSource = Source.builder()
            .name("Child Partition")
            .type(SourceType.PARTITION)
            .rootPath("/mnt/parent/partition1")
            .status(ScanStatus.PENDING)
            .postponed(false)
            .totalFiles(0L)
            .totalSize(0L)
            .processedFiles(0L)
            .processedSize(0L)
            .build();
    }

    @Test
    void testSaveAndFind() {
        // Given/When
        Source saved = sourceRepository.save(parentSource);
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<Source> found = sourceRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Parent Disk", found.get().getName());
        assertEquals(SourceType.DISK, found.get().getType());
    }

    @Test
    void testPhysicalIdJsonStorage() {
        // Given
        PhysicalId physicalId = PhysicalId.builder()
            .diskUuid("disk-123")
            .partitionUuid("part-456")
            .volumeLabel("TEST-DISK")
            .serialNumber("SN12345")
            .mountPoint("/mnt/test")
            .filesystemType("ext4")
            .capacity(1000000000L)
            .usedSpace(500000000L)
            .build();

        parentSource.setPhysicalId(physicalId);

        // When
        Source saved = sourceRepository.save(parentSource);
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<Source> found = sourceRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertNotNull(found.get().getPhysicalId());
        assertEquals("disk-123", found.get().getPhysicalId().getDiskUuid());
        assertEquals("TEST-DISK", found.get().getPhysicalId().getVolumeLabel());
    }

    @Test
    void testParentChildRelationship() {
        // Given
        Source savedParent = sourceRepository.save(parentSource);
        parentSource.addChild(childSource);
        sourceRepository.save(parentSource);
        entityManager.flush();
        entityManager.clear();

        // Then
        Optional<Source> foundParent = sourceRepository.findById(savedParent.getId());
        assertTrue(foundParent.isPresent());
        assertEquals(1, foundParent.get().getChildren().size());

        Source foundChild = foundParent.get().getChildren().get(0);
        assertEquals("Child Partition", foundChild.getName());
        assertNotNull(foundChild.getParent());
        assertEquals(savedParent.getId(), foundChild.getParent().getId());
    }

    @Test
    void testFindByParentIsNull() {
        // Given
        sourceRepository.save(parentSource);
        parentSource.addChild(childSource);
        sourceRepository.save(parentSource);
        entityManager.flush();

        // When
        List<Source> rootSources = sourceRepository.findByParentIsNull();

        // Then
        assertEquals(1, rootSources.size());
        assertEquals("Parent Disk", rootSources.get(0).getName());
    }

    @Test
    void testFindByParentId() {
        // Given
        Source savedParent = sourceRepository.save(parentSource);
        parentSource.addChild(childSource);
        sourceRepository.save(parentSource);
        entityManager.flush();

        // When
        List<Source> children = sourceRepository.findByParentId(savedParent.getId());

        // Then
        assertEquals(1, children.size());
        assertEquals("Child Partition", children.get(0).getName());
    }

    @Test
    void testFindByIdWithChildren() {
        // Given
        Source savedParent = sourceRepository.save(parentSource);
        parentSource.addChild(childSource);
        sourceRepository.save(parentSource);
        entityManager.flush();
        entityManager.clear();

        // When
        Optional<Source> found = sourceRepository.findByIdWithChildren(savedParent.getId());

        // Then
        assertTrue(found.isPresent());
        assertEquals(1, found.get().getChildren().size());
        // Children should be eagerly fetched, no lazy loading exception
        assertEquals("Child Partition", found.get().getChildren().get(0).getName());
    }

    @Test
    void testCascadeDelete() {
        // Given
        Source savedParent = sourceRepository.save(parentSource);
        parentSource.addChild(childSource);
        sourceRepository.save(parentSource);
        entityManager.flush();

        // When
        sourceRepository.delete(parentSource);
        entityManager.flush();

        // Then
        Optional<Source> foundParent = sourceRepository.findById(savedParent.getId());
        assertFalse(foundParent.isPresent());

        // Child should also be deleted (cascade)
        List<Source> allSources = sourceRepository.findAll();
        assertTrue(allSources.isEmpty());
    }

    @Test
    void testFindByType() {
        // Given
        sourceRepository.save(parentSource);
        parentSource.addChild(childSource);
        sourceRepository.save(parentSource);
        entityManager.flush();

        // When
        List<Source> disks = sourceRepository.findByType(SourceType.DISK);
        List<Source> partitions = sourceRepository.findByType(SourceType.PARTITION);

        // Then
        assertEquals(1, disks.size());
        assertEquals("Parent Disk", disks.get(0).getName());

        assertEquals(1, partitions.size());
        assertEquals("Child Partition", partitions.get(0).getName());
    }
}
