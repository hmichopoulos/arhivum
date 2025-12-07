package tech.zaisys.archivum.scanner.service;

import lombok.extern.slf4j.Slf4j;
import tech.zaisys.archivum.api.dto.ProjectIdentityDto;
import tech.zaisys.archivum.api.enums.ProjectType;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Detects Maven projects by looking for pom.xml.
 * Extracts groupId, artifactId, and version.
 */
@Slf4j
public class MavenProjectDetector implements ProjectDetector {

    private static final String MARKER_FILE = "pom.xml";

    @Override
    public boolean canDetect(Path folder) {
        return Files.exists(folder.resolve(MARKER_FILE));
    }

    @Override
    public Optional<ProjectIdentityDto> detect(Path folder) {
        Path pomPath = folder.resolve(MARKER_FILE);
        if (!Files.exists(pomPath)) {
            return Optional.empty();
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomPath.toFile());
            doc.getDocumentElement().normalize();

            Element root = doc.getDocumentElement();

            // Extract groupId, artifactId, version
            String groupId = getElementText(root, "groupId");
            String artifactId = getElementText(root, "artifactId");
            String version = getElementText(root, "version");

            // If groupId or version is missing, try parent
            if (groupId == null || groupId.isEmpty()) {
                groupId = getParentElementText(root, "groupId");
            }
            if (version == null || version.isEmpty()) {
                version = getParentElementText(root, "version");
            }

            // Fallback for missing values
            if (artifactId == null || artifactId.isEmpty()) {
                log.warn("Maven project at {} missing artifactId", folder);
                return Optional.empty();
            }

            if (groupId == null || groupId.isEmpty()) {
                groupId = "unknown";
                log.warn("Maven project at {} missing groupId, using 'unknown'", folder);
            }

            if (version == null || version.isEmpty()) {
                version = "unknown";
                log.warn("Maven project at {} missing version, using 'unknown'", folder);
            }

            String identifier = groupId + ":" + artifactId + ":" + version;

            ProjectIdentityDto identity = ProjectIdentityDto.builder()
                .type(ProjectType.MAVEN)
                .name(artifactId)
                .version(version)
                .groupId(groupId)
                .identifier(identifier)
                .build();

            log.debug("Detected Maven project: {}", identifier);
            return Optional.of(identity);

        } catch (Exception e) {
            log.warn("Failed to parse pom.xml at {}: {}", folder, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public int getPriority() {
        return 10; // Higher than generic detectors
    }

    /**
     * Get text content of a direct child element.
     */
    private String getElementText(Element parent, String tagName) {
        NodeList nodeList = parent.getElementsByTagName(tagName);
        if (nodeList.getLength() > 0) {
            Element element = (Element) nodeList.item(0);
            // Check if this is a direct child (not nested)
            if (element.getParentNode() == parent) {
                return element.getTextContent().trim();
            }
        }
        return null;
    }

    /**
     * Get text content from parent element.
     */
    private String getParentElementText(Element root, String tagName) {
        NodeList parentList = root.getElementsByTagName("parent");
        if (parentList.getLength() > 0) {
            Element parent = (Element) parentList.item(0);
            return getElementText(parent, tagName);
        }
        return null;
    }
}
