package tech.zaisys.archivum.server.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tech.zaisys.archivum.api.dto.PhysicalId;
import tech.zaisys.archivum.api.dto.SourceDto;
import tech.zaisys.archivum.api.enums.ScanStatus;
import tech.zaisys.archivum.api.enums.SourceScanType;
import tech.zaisys.archivum.api.enums.SourceType;
import tech.zaisys.archivum.server.domain.Source;
import tech.zaisys.archivum.server.repository.SourceRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for source creation endpoint with real PostgreSQL database.
 * Tests that all SourceScanType enum values can be persisted correctly.
 *
 * TODO: Enable when Docker environment is properly configured for tests
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class SourceCreationIntegrationTest {

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
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SourceRepository sourceRepository;

    @BeforeEach
    void setUp() {
        sourceRepository.deleteAll();
    }

    @Test
    void shouldCreateSourceWithDiscoveryType() throws Exception {
        // Given
        SourceDto sourceDto = createSourceDto("Discovery Disk", SourceScanType.DISCOVERY);

        // When
        mockMvc.perform(post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sourceDto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Discovery Disk"))
            .andExpect(jsonPath("$.sourceScanType").value("DISCOVERY"));

        // Then
        List<Source> sources = sourceRepository.findAll();
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getSourceScanType()).isEqualTo(SourceScanType.DISCOVERY);
    }

    @Test
    void shouldCreateSourceWithDestinationType() throws Exception {
        // Given
        SourceDto sourceDto = createSourceDto("Destination Disk", SourceScanType.DESTINATION);

        // When
        mockMvc.perform(post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sourceDto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sourceScanType").value("DESTINATION"));

        // Then
        List<Source> sources = sourceRepository.findAll();
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getSourceScanType()).isEqualTo(SourceScanType.DESTINATION);
    }

    @Test
    void shouldCreateSourceWithWarehouseType() throws Exception {
        // Given
        SourceDto sourceDto = createSourceDto("Warehouse Disk", SourceScanType.WAREHOUSE);

        // When
        mockMvc.perform(post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sourceDto)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sourceScanType").value("WAREHOUSE"));

        // Then
        List<Source> sources = sourceRepository.findAll();
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getSourceScanType()).isEqualTo(SourceScanType.WAREHOUSE);
    }

    @Test
    void shouldCreateMultipleSourcesWithDifferentTypes() throws Exception {
        // Given
        SourceScanType[] types = {SourceScanType.DISCOVERY, SourceScanType.DESTINATION, SourceScanType.WAREHOUSE};

        // When
        for (int i = 0; i < types.length; i++) {
            SourceDto sourceDto = createSourceDto("Disk " + i, types[i]);
            mockMvc.perform(post("/api/sources")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sourceDto)))
                .andExpect(status().isCreated());
        }

        // Then
        List<Source> sources = sourceRepository.findAll();
        assertThat(sources).hasSize(types.length);
        assertThat(sources)
            .extracting(Source::getSourceScanType)
            .containsExactlyInAnyOrder(types);
    }

    @Test
    void shouldDefaultToDiscoveryWhenNotSpecified() throws Exception {
        // Given - SourceDto without explicit sourceScanType
        SourceDto sourceDto = SourceDto.builder()
            .name("Default Type Disk")
            .type(SourceType.DISK)
            .rootPath("/test")
            .status(ScanStatus.SCANNING)
            .totalFiles(0L)
            .totalSize(0L)
            .processedFiles(0L)
            .processedSize(0L)
            .postponed(false)
            .scanStartedAt(Instant.now())
            .build();

        // When
        mockMvc.perform(post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(sourceDto)))
            .andExpect(status().isCreated());

        // Then - Should default to DISCOVERY
        List<Source> sources = sourceRepository.findAll();
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getSourceScanType()).isEqualTo(SourceScanType.DISCOVERY);
    }

    private SourceDto createSourceDto(String name, SourceScanType scanType) {
        return SourceDto.builder()
            .name(name)
            .type(SourceType.DISK)
            .rootPath("/test/" + name)
            .status(ScanStatus.SCANNING)
            .sourceScanType(scanType)
            .totalFiles(0L)
            .totalSize(0L)
            .processedFiles(0L)
            .processedSize(0L)
            .postponed(false)
            .scanStartedAt(Instant.now())
            .build();
    }
}
