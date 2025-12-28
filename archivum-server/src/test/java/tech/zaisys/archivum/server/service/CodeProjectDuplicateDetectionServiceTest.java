package tech.zaisys.archivum.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.zaisys.archivum.api.enums.ProjectType;
import tech.zaisys.archivum.server.domain.CodeProject;
import tech.zaisys.archivum.server.domain.CodeProjectDuplicateGroup;
import tech.zaisys.archivum.server.domain.CodeProjectDuplicateGroup.DiffComplexity;
import tech.zaisys.archivum.server.domain.CodeProjectDuplicateGroup.DuplicateType;
import tech.zaisys.archivum.server.repository.CodeProjectRepository;
import tech.zaisys.archivum.server.service.CodeProjectDuplicateDetectionService.SimilarityResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for CodeProjectDuplicateDetectionService.
 */
@ExtendWith(MockitoExtension.class)
class CodeProjectDuplicateDetectionServiceTest {

    @Mock
    private CodeProjectRepository repository;

    private CodeProjectDuplicateDetectionService service;

    @BeforeEach
    void setUp() {
        service = new CodeProjectDuplicateDetectionService(repository);
    }

    @Test
    void testCalculateSimilarity_ExactDuplicates() {
        // Given: Two projects with same content hash
        CodeProject a = createProject("com.example:my-api:1.0.0", "hash123", 100);
        CodeProject b = createProject("com.example:my-api:1.0.0", "hash123", 100);

        // When
        SimilarityResult result = service.calculateSimilarity(a, b);

        // Then
        assertEquals(100.0, result.jaccardSimilarity());
        assertEquals(0, result.filesOnlyInA());
        assertEquals(0, result.filesOnlyInB());
        assertEquals(100, result.filesInBoth());
        assertEquals(DiffComplexity.TRIVIAL, result.complexity());
    }

    @Test
    void testCalculateSimilarity_DifferentContent() {
        // Given: Two projects with different content hashes
        CodeProject a = createProject("com.example:my-api:1.0.0", "hash123", 100);
        CodeProject b = createProject("com.example:my-api:1.0.0", "hash456", 150);

        // When
        SimilarityResult result = service.calculateSimilarity(a, b);

        // Then
        // Similarity is estimated as min/max ratio
        double expectedSimilarity = (100.0 / 150.0) * 100;
        assertEquals(expectedSimilarity, result.jaccardSimilarity(), 0.01);
        assertEquals(0, result.filesOnlyInA());
        assertEquals(50, result.filesOnlyInB());
    }

    @Test
    void testEstimateDiffComplexity_Trivial() {
        // Given: Projects with < 5% difference
        CodeProject a = createProject("test", "hash1", 100);
        CodeProject b = createProject("test", "hash2", 103);

        // When
        SimilarityResult result = service.calculateSimilarity(a, b);

        // Then
        assertEquals(DiffComplexity.TRIVIAL, result.complexity());
    }

    @Test
    void testEstimateDiffComplexity_Simple() {
        // Given: Projects with 5-15% difference
        CodeProject a = createProject("test", "hash1", 100);
        CodeProject b = createProject("test", "hash2", 110);

        // When
        SimilarityResult result = service.calculateSimilarity(a, b);

        // Then
        assertEquals(DiffComplexity.SIMPLE, result.complexity());
    }

    @Test
    void testEstimateDiffComplexity_Medium() {
        // Given: Projects with 15-30% difference
        CodeProject a = createProject("test", "hash1", 100);
        CodeProject b = createProject("test", "hash2", 125);

        // When
        SimilarityResult result = service.calculateSimilarity(a, b);

        // Then
        assertEquals(DiffComplexity.MEDIUM, result.complexity());
    }

    @Test
    void testEstimateDiffComplexity_Complex() {
        // Given: Projects with > 30% difference
        CodeProject a = createProject("test", "hash1", 100);
        CodeProject b = createProject("test", "hash2", 200);

        // When
        SimilarityResult result = service.calculateSimilarity(a, b);

        // Then
        assertEquals(DiffComplexity.COMPLEX, result.complexity());
    }

    @Test
    void testDetectAllDuplicates_ExactDuplicates() {
        // Given: Two projects with same content hash
        CodeProject p1 = createProject("com.example:my-api:1.0.0", "hash123", 100);
        CodeProject p2 = createProject("com.example:my-api:1.0.0", "hash123", 100);

        when(repository.findAll()).thenReturn(List.of(p1, p2));

        // When
        List<CodeProjectDuplicateGroup> groups = service.detectAllDuplicates();

        // Then
        assertEquals(1, groups.size());
        CodeProjectDuplicateGroup group = groups.get(0);
        assertEquals(DuplicateType.EXACT, group.getDuplicateType());
        assertEquals(2, group.getMembers().size());
    }

    @Test
    void testDetectAllDuplicates_NoDuplicates() {
        // Given: Two completely different projects
        CodeProject p1 = createProject("com.example:project1:1.0.0", "hash123", 100);
        CodeProject p2 = createProject("com.other:project2:1.0.0", "hash456", 150);

        when(repository.findAll()).thenReturn(List.of(p1, p2));

        // When
        List<CodeProjectDuplicateGroup> groups = service.detectAllDuplicates();

        // Then
        assertEquals(0, groups.size());
    }

    @Test
    void testDetectAllDuplicates_DifferentVersions_Maven() {
        // Given: Same Maven project (groupId:artifactId), different versions
        CodeProject p1 = createProject("com.example:my-api:1.0.0", "hash123", 100);
        CodeProject p2 = createProject("com.example:my-api:2.0.0", "hash456", 105);

        when(repository.findAll()).thenReturn(List.of(p1, p2));

        // When
        List<CodeProjectDuplicateGroup> groups = service.detectAllDuplicates();

        // Then: Should detect as DIFFERENT_VERSION duplicates
        assertEquals(1, groups.size());
        CodeProjectDuplicateGroup group = groups.get(0);
        assertEquals(DuplicateType.DIFFERENT_VERSION, group.getDuplicateType());
        assertEquals(2, group.getMembers().size());
    }

    @Test
    void testDetectAllDuplicates_DifferentVersions_NPM() {
        // Given: Same NPM package, different versions
        CodeProject p1 = createProject("my-package:1.0.0", "hash123", 50);
        CodeProject p2 = createProject("my-package:2.0.0", "hash456", 55);

        when(repository.findAll()).thenReturn(List.of(p1, p2));

        // When
        List<CodeProjectDuplicateGroup> groups = service.detectAllDuplicates();

        // Then: Should detect as DIFFERENT_VERSION duplicates
        assertEquals(1, groups.size());
        CodeProjectDuplicateGroup group = groups.get(0);
        assertEquals(DuplicateType.DIFFERENT_VERSION, group.getDuplicateType());
    }

    @Test
    void testDetectAllDuplicates_DifferentVersions_NPMScoped() {
        // Given: Same NPM scoped package, different versions
        CodeProject p1 = createProject("@scope/my-package:1.0.0", "hash123", 50);
        CodeProject p2 = createProject("@scope/my-package:2.0.0", "hash456", 55);

        when(repository.findAll()).thenReturn(List.of(p1, p2));

        // When
        List<CodeProjectDuplicateGroup> groups = service.detectAllDuplicates();

        // Then: Should detect as DIFFERENT_VERSION duplicates
        assertEquals(1, groups.size());
        CodeProjectDuplicateGroup group = groups.get(0);
        assertEquals(DuplicateType.DIFFERENT_VERSION, group.getDuplicateType());
    }

    @Test
    void testDetectAllDuplicates_DifferentVersions_Gradle() {
        // Given: Same Gradle project (group:name), different versions
        CodeProject p1 = createProject("com.example:my-lib:1.0.0", "hash123", 75);
        CodeProject p2 = createProject("com.example:my-lib:2.0.0", "hash456", 80);

        when(repository.findAll()).thenReturn(List.of(p1, p2));

        // When
        List<CodeProjectDuplicateGroup> groups = service.detectAllDuplicates();

        // Then: Should detect as DIFFERENT_VERSION duplicates
        assertEquals(1, groups.size());
        CodeProjectDuplicateGroup group = groups.get(0);
        assertEquals(DuplicateType.DIFFERENT_VERSION, group.getDuplicateType());
    }

    @Test
    void testDetectAllDuplicates_SameProject_DifferentContent() {
        // Given: Same identifier (version), different content
        CodeProject p1 = createProject("com.example:my-api:1.0.0", "hash123", 100);
        CodeProject p2 = createProject("com.example:my-api:1.0.0", "hash456", 100);

        when(repository.findAll()).thenReturn(List.of(p1, p2));

        // When
        List<CodeProjectDuplicateGroup> groups = service.detectAllDuplicates();

        // Then: Should detect as SAME_PROJECT_DIFF_CONTENT
        assertEquals(1, groups.size());
        CodeProjectDuplicateGroup group = groups.get(0);
        assertEquals(DuplicateType.SAME_PROJECT_DIFF_CONTENT, group.getDuplicateType());
    }

    @Test
    void testDetectAllDuplicates_MultipleVersionsOfSameProject() {
        // Given: Three versions of the same project
        CodeProject v1 = createProject("com.example:api:1.0.0", "hash1", 100);
        CodeProject v2 = createProject("com.example:api:2.0.0", "hash2", 105);
        CodeProject v3 = createProject("com.example:api:3.0.0", "hash3", 110);

        when(repository.findAll()).thenReturn(List.of(v1, v2, v3));

        // When
        List<CodeProjectDuplicateGroup> groups = service.detectAllDuplicates();

        // Then: Should create one group with all three versions
        assertEquals(1, groups.size());
        CodeProjectDuplicateGroup group = groups.get(0);
        assertEquals(DuplicateType.DIFFERENT_VERSION, group.getDuplicateType());
        assertEquals(3, group.getMembers().size());
    }

    @Test
    void testDetectAllDuplicates_DifferentProjectsSameGroupId() {
        // Given: Different Maven projects from same group (different artifactId)
        CodeProject p1 = createProject("com.example:api:1.0.0", "hash123", 100);
        CodeProject p2 = createProject("com.example:web:1.0.0", "hash456", 150);

        when(repository.findAll()).thenReturn(List.of(p1, p2));

        // When
        List<CodeProjectDuplicateGroup> groups = service.detectAllDuplicates();

        // Then: Should NOT be detected as duplicates (different artifactId)
        assertEquals(0, groups.size());
    }

    private CodeProject createProject(String identifier, String contentHash, int fileCount) {
        return CodeProject.builder()
            .id(UUID.randomUUID())
            .sourceId(UUID.randomUUID())
            .rootPath("/test/path")
            .projectType(ProjectType.MAVEN)
            .name("test")
            .version("1.0.0")
            .identifier(identifier)
            .contentHash(contentHash)
            .sourceFileCount(fileCount)
            .totalFileCount(fileCount * 2)
            .totalSizeBytes(1000000L)
            .scannedAt(Instant.now())
            .build();
    }
}
