package tech.zaisys.archivum.scanner.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.zaisys.archivum.api.dto.CodeProjectDto;
import tech.zaisys.archivum.api.dto.ProjectIdentityDto;
import tech.zaisys.archivum.api.dto.SourceDto;
import tech.zaisys.archivum.api.enums.ProjectType;
import tech.zaisys.archivum.api.enums.SourceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UploadCommand, focusing on code project upload functionality.
 * Uses MockWebServer to simulate HTTP server responses.
 */
class UploadCommandTest {

    @TempDir
    Path outputDir;

    private MockWebServer mockServer;
    private ObjectMapper objectMapper;
    private UploadCommand uploadCommand;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

        uploadCommand = new UploadCommand();

        // Use reflection to set fields since they're package-private
        setField(uploadCommand, "outputDir", outputDir);
        setField(uploadCommand, "serverUrl", mockServer.url("/").toString().replaceAll("/$", ""));
        setField(uploadCommand, "verbose", false);
        setField(uploadCommand, "timeoutSeconds", 10);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void uploadCodeProjects_withValidProjects_uploadsSuccessfully() throws Exception {
        // Given: source.json and code-projects.json with 2 projects
        UUID oldSourceId = UUID.randomUUID();
        UUID newSourceId = UUID.randomUUID();

        SourceDto source = createTestSource(oldSourceId);
        createSourceJson(source);

        List<CodeProjectDto> projects = List.of(
            createTestProject(oldSourceId, "/projects/app1", "app1-hash"),
            createTestProject(oldSourceId, "/projects/app2", "app2-hash")
        );
        createCodeProjectsJson(projects);

        // Mock server responses
        mockServer.enqueue(new MockResponse()
            .setResponseCode(201)
            .setBody(objectMapper.writeValueAsString(
                createTestSource(newSourceId)))
            .addHeader("Content-Type", "application/json"));

        mockServer.enqueue(new MockResponse()
            .setResponseCode(201)
            .setBody("[]")
            .addHeader("Content-Type", "application/json"));

        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{}")
            .addHeader("Content-Type", "application/json"));

        // When: run upload command
        Integer exitCode = uploadCommand.call();

        // Then: command succeeds
        assertEquals(0, exitCode);

        // Verify HTTP requests
        RecordedRequest createSourceReq = mockServer.takeRequest();
        assertEquals("/api/sources", createSourceReq.getPath());
        assertEquals("POST", createSourceReq.getMethod());

        RecordedRequest uploadProjectsReq = mockServer.takeRequest();
        assertEquals("/api/code-projects/bulk", uploadProjectsReq.getPath());
        assertEquals("POST", uploadProjectsReq.getMethod());

        // Verify request body has updated source IDs
        String projectsBody = uploadProjectsReq.getBody().readUtf8();
        List<CodeProjectDto> uploadedProjects = objectMapper.readValue(
            projectsBody,
            objectMapper.getTypeFactory().constructCollectionType(List.class, CodeProjectDto.class)
        );

        assertEquals(2, uploadedProjects.size());
        uploadedProjects.forEach(p -> assertEquals(newSourceId, p.getSourceId()));

        // Verify other fields are preserved
        assertEquals("/projects/app1", uploadedProjects.get(0).getRootPath());
        assertEquals("app1-hash", uploadedProjects.get(0).getContentHash());
        assertEquals("/projects/app2", uploadedProjects.get(1).getRootPath());
        assertEquals("app2-hash", uploadedProjects.get(1).getContentHash());
    }

    @Test
    void uploadCodeProjects_noProjectsFile_skipsUpload() throws Exception {
        // Given: source.json exists but NO code-projects.json
        UUID sourceId = UUID.randomUUID();
        SourceDto source = createTestSource(sourceId);
        createSourceJson(source);

        // Mock server responses (only source creation and completion)
        mockServer.enqueue(new MockResponse()
            .setResponseCode(201)
            .setBody(objectMapper.writeValueAsString(source))
            .addHeader("Content-Type", "application/json"));

        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{}")
            .addHeader("Content-Type", "application/json"));

        // When: run upload command
        Integer exitCode = uploadCommand.call();

        // Then: command succeeds
        assertEquals(0, exitCode);

        // Verify only 2 requests (create source + complete scan, NO project upload)
        assertEquals(2, mockServer.getRequestCount());

        RecordedRequest createSourceReq = mockServer.takeRequest();
        assertEquals("/api/sources", createSourceReq.getPath());

        RecordedRequest completeScanReq = mockServer.takeRequest();
        assertTrue(completeScanReq.getPath().contains("/complete"));
    }

    @Test
    void uploadCodeProjects_emptyProjectsArray_skipsUpload() throws Exception {
        // Given: code-projects.json exists but is empty array
        UUID sourceId = UUID.randomUUID();
        SourceDto source = createTestSource(sourceId);
        createSourceJson(source);
        createCodeProjectsJson(List.of()); // empty array

        // Mock server responses
        mockServer.enqueue(new MockResponse()
            .setResponseCode(201)
            .setBody(objectMapper.writeValueAsString(source))
            .addHeader("Content-Type", "application/json"));

        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{}")
            .addHeader("Content-Type", "application/json"));

        // When: run upload command
        Integer exitCode = uploadCommand.call();

        // Then: command succeeds
        assertEquals(0, exitCode);

        // Verify only 2 requests (create source + complete, NO project upload)
        assertEquals(2, mockServer.getRequestCount());
    }

    @Test
    void uploadCodeProjects_serverError_failsWithException() throws Exception {
        // Given: valid projects but server returns error
        UUID sourceId = UUID.randomUUID();
        SourceDto source = createTestSource(sourceId);
        createSourceJson(source);

        List<CodeProjectDto> projects = List.of(
            createTestProject(sourceId, "/projects/app1", "hash1")
        );
        createCodeProjectsJson(projects);

        // Mock server responses
        mockServer.enqueue(new MockResponse()
            .setResponseCode(201)
            .setBody(objectMapper.writeValueAsString(source))
            .addHeader("Content-Type", "application/json"));

        mockServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("{\"error\": \"Internal server error\"}")
            .addHeader("Content-Type", "application/json"));

        // When: run upload command
        Integer exitCode = uploadCommand.call();

        // Then: command fails
        assertEquals(1, exitCode);
    }

    @Test
    void uploadCodeProjects_preservesAllProjectFields() throws Exception {
        // Given: project with all fields populated
        UUID oldSourceId = UUID.randomUUID();
        UUID newSourceId = UUID.randomUUID();

        SourceDto source = createTestSource(oldSourceId);
        createSourceJson(source);

        ProjectIdentityDto identity = ProjectIdentityDto.builder()
            .type(ProjectType.MAVEN)
            .name("test-app")
            .groupId("com.example")
            .version("1.0.0")
            .build();

        CodeProjectDto project = CodeProjectDto.builder()
            .sourceId(oldSourceId)
            .rootPath("/projects/test-app")
            .identity(identity)
            .contentHash("abc123")
            .sourceFileCount(50)
            .totalFileCount(150)
            .totalSizeBytes(1024000L)
            .scannedAt(Instant.parse("2025-01-01T12:00:00Z"))
            .build();

        createCodeProjectsJson(List.of(project));

        // Mock server responses
        mockServer.enqueue(new MockResponse()
            .setResponseCode(201)
            .setBody(objectMapper.writeValueAsString(
                createTestSource(newSourceId)))
            .addHeader("Content-Type", "application/json"));

        mockServer.enqueue(new MockResponse()
            .setResponseCode(201)
            .setBody("[]")
            .addHeader("Content-Type", "application/json"));

        mockServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("{}")
            .addHeader("Content-Type", "application/json"));

        // When: run upload command
        Integer exitCode = uploadCommand.call();

        // Then: command succeeds
        assertEquals(0, exitCode);

        // Verify all fields are preserved (except sourceId which is updated)
        RecordedRequest uploadProjectsReq = mockServer.takeRequest(); // skip source creation
        uploadProjectsReq = mockServer.takeRequest(); // get project upload

        String projectsBody = uploadProjectsReq.getBody().readUtf8();
        List<CodeProjectDto> uploadedProjects = objectMapper.readValue(
            projectsBody,
            objectMapper.getTypeFactory().constructCollectionType(List.class, CodeProjectDto.class)
        );

        assertEquals(1, uploadedProjects.size());
        CodeProjectDto uploaded = uploadedProjects.get(0);

        assertEquals(newSourceId, uploaded.getSourceId());
        assertEquals("/projects/test-app", uploaded.getRootPath());
        assertEquals("abc123", uploaded.getContentHash());
        assertEquals(50, uploaded.getSourceFileCount());
        assertEquals(150, uploaded.getTotalFileCount());
        assertEquals(1024000L, uploaded.getTotalSizeBytes());
        assertEquals(Instant.parse("2025-01-01T12:00:00Z"), uploaded.getScannedAt());

        assertNotNull(uploaded.getIdentity());
        assertEquals(ProjectType.MAVEN, uploaded.getIdentity().getType());
        assertEquals("test-app", uploaded.getIdentity().getName());
        assertEquals("com.example", uploaded.getIdentity().getGroupId());
        assertEquals("1.0.0", uploaded.getIdentity().getVersion());
    }

    // Helper methods

    private SourceDto createTestSource(UUID sourceId) {
        return SourceDto.builder()
            .id(sourceId)
            .name("Test Source")
            .type(SourceType.DISK)
            .rootPath("/test/path")
            .totalFiles(0L)
            .totalSize(0L)
            .build();
    }

    private CodeProjectDto createTestProject(UUID sourceId, String rootPath, String contentHash) {
        return CodeProjectDto.builder()
            .sourceId(sourceId)
            .rootPath(rootPath)
            .identity(ProjectIdentityDto.builder()
                .type(ProjectType.MAVEN)
                .name("test-project")
                .build())
            .contentHash(contentHash)
            .sourceFileCount(10)
            .totalFileCount(50)
            .totalSizeBytes(102400L)
            .scannedAt(Instant.now())
            .build();
    }

    private void createSourceJson(SourceDto source) throws IOException {
        Path sourceJson = outputDir.resolve("source.json");
        objectMapper.writeValue(sourceJson.toFile(), source);
    }

    private void createCodeProjectsJson(List<CodeProjectDto> projects) throws IOException {
        Path projectsFile = outputDir.resolve("code-projects.json");
        objectMapper.writeValue(projectsFile.toFile(), projects);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
