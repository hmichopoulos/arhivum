package tech.zaisys.archivum.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.zaisys.archivum.server.domain.CodeProject;
import tech.zaisys.archivum.server.domain.CodeProjectDuplicateGroup;
import tech.zaisys.archivum.server.domain.CodeProjectDuplicateGroup.DiffComplexity;
import tech.zaisys.archivum.server.domain.CodeProjectDuplicateGroup.DuplicateType;
import tech.zaisys.archivum.server.domain.CodeProjectDuplicateMember;
import tech.zaisys.archivum.server.repository.CodeProjectRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for detecting and analyzing code project duplicates.
 * Implements smart duplicate detection with similarity calculation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CodeProjectDuplicateDetectionService {

    private final CodeProjectRepository codeProjectRepository;

    /**
     * Similarity calculation result.
     */
    public record SimilarityResult(
        double jaccardSimilarity,
        int filesOnlyInA,
        int filesOnlyInB,
        int filesInBoth,
        DiffComplexity complexity
    ) {}

    /**
     * Detect all duplicates and create duplicate groups.
     * This scans all projects and groups them by various criteria.
     *
     * @return List of duplicate groups found
     */
    @Transactional
    public List<CodeProjectDuplicateGroup> detectAllDuplicates() {
        log.info("Starting duplicate detection for all code projects");

        List<CodeProject> allProjects = codeProjectRepository.findAll();
        List<CodeProjectDuplicateGroup> groups = new ArrayList<>();

        // Track processed projects to avoid duplicate grouping
        Set<UUID> processed = new HashSet<>();

        for (CodeProject project : allProjects) {
            if (processed.contains(project.getId())) {
                continue;
            }

            // Find potential duplicates for this project
            List<CodeProject> candidates = findDuplicateCandidates(project, allProjects);

            if (!candidates.isEmpty()) {
                CodeProjectDuplicateGroup group = analyzeAndCreateGroup(project, candidates);
                groups.add(group);

                // Mark all as processed
                processed.add(project.getId());
                candidates.forEach(c -> processed.add(c.getId()));
            }
        }

        log.info("Duplicate detection complete. Found {} duplicate groups", groups.size());
        return groups;
    }

    /**
     * Find duplicate candidates for a given project.
     * Looks for projects with same identifier or content hash.
     */
    private List<CodeProject> findDuplicateCandidates(CodeProject project, List<CodeProject> allProjects) {
        return allProjects.stream()
            .filter(p -> !p.getId().equals(project.getId()))
            .filter(p ->
                // Same identifier (different versions or content)
                p.getIdentifier().equals(project.getIdentifier()) ||
                // Same content hash (exact duplicates)
                p.getContentHash().equals(project.getContentHash()) ||
                // Similar identifier (e.g., same name different version)
                isSimilarIdentifier(p.getIdentifier(), project.getIdentifier())
            )
            .collect(Collectors.toList());
    }

    /**
     * Check if two identifiers are similar (e.g., same project different version).
     */
    private boolean isSimilarIdentifier(String id1, String id2) {
        // Extract base identifier without version
        String base1 = extractBaseIdentifier(id1);
        String base2 = extractBaseIdentifier(id2);
        return base1.equals(base2) && !id1.equals(id2);
    }

    /**
     * Extract base identifier (without version).
     * For "com.example:my-api:1.0.0" returns "com.example:my-api"
     */
    private String extractBaseIdentifier(String identifier) {
        int lastColon = identifier.lastIndexOf(':');
        if (lastColon > 0) {
            return identifier.substring(0, lastColon);
        }
        return identifier;
    }

    /**
     * Analyze a project and its duplicates, creating a duplicate group.
     */
    private CodeProjectDuplicateGroup analyzeAndCreateGroup(
        CodeProject primary,
        List<CodeProject> candidates
    ) {
        // Determine duplicate type
        DuplicateType type = determineDuplicateType(primary, candidates);

        CodeProjectDuplicateGroup.CodeProjectDuplicateGroupBuilder groupBuilder =
            CodeProjectDuplicateGroup.builder()
                .identifier(primary.getIdentifier())
                .duplicateType(type);

        // For exact duplicates, no need for similarity calculation
        if (type == DuplicateType.EXACT) {
            groupBuilder
                .similarityPercent(new BigDecimal("100.00"))
                .diffComplexity(DiffComplexity.TRIVIAL);
        } else {
            // Calculate similarity with first candidate
            SimilarityResult similarity = calculateSimilarity(primary, candidates.get(0));
            groupBuilder
                .similarityPercent(BigDecimal.valueOf(similarity.jaccardSimilarity())
                    .setScale(2, RoundingMode.HALF_UP))
                .filesOnlyInPrimary(similarity.filesOnlyInA())
                .filesOnlyInSecondary(similarity.filesOnlyInB())
                .filesInBoth(similarity.filesInBoth())
                .diffComplexity(similarity.complexity());
        }

        CodeProjectDuplicateGroup group = groupBuilder.build();

        // Add members
        List<CodeProjectDuplicateMember> members = new ArrayList<>();

        // Add primary
        members.add(CodeProjectDuplicateMember.builder()
            .duplicateGroup(group)
            .codeProject(primary)
            .isPrimary(true)
            .build());

        // Add candidates
        for (CodeProject candidate : candidates) {
            members.add(CodeProjectDuplicateMember.builder()
                .duplicateGroup(group)
                .codeProject(candidate)
                .isPrimary(false)
                .build());
        }

        group.setMembers(members);
        return group;
    }

    /**
     * Determine the type of duplicate relationship.
     */
    private DuplicateType determineDuplicateType(CodeProject primary, List<CodeProject> candidates) {
        // Check if all have same content hash (exact duplicates)
        boolean allSameHash = candidates.stream()
            .allMatch(c -> c.getContentHash().equals(primary.getContentHash()));

        if (allSameHash) {
            return DuplicateType.EXACT;
        }

        // Check if same identifier but different content
        boolean sameIdentifier = candidates.stream()
            .anyMatch(c -> c.getIdentifier().equals(primary.getIdentifier()));

        if (sameIdentifier) {
            return DuplicateType.SAME_PROJECT_DIFF_CONTENT;
        }

        // Otherwise, it's different versions
        return DuplicateType.DIFFERENT_VERSION;
    }

    /**
     * Calculate similarity between two code projects.
     * Uses Jaccard similarity on source file counts as a proxy.
     *
     * Note: This is a simplified implementation. In a real system,
     * you would need the actual file hashes to compute true similarity.
     */
    public SimilarityResult calculateSimilarity(CodeProject a, CodeProject b) {
        // In a real implementation, we would:
        // 1. Get list of file hashes for project A
        // 2. Get list of file hashes for project B
        // 3. Compute intersection and union
        // 4. Calculate Jaccard similarity

        // For now, we'll estimate based on file counts and content hash
        int filesA = a.getSourceFileCount();
        int filesB = b.getSourceFileCount();

        // Estimate similarity based on file count ratio
        int minFiles = Math.min(filesA, filesB);
        int maxFiles = Math.max(filesA, filesB);

        double estimatedSimilarity = (double) minFiles / maxFiles * 100;

        // If content hashes are same, similarity is 100%
        if (a.getContentHash().equals(b.getContentHash())) {
            return new SimilarityResult(
                100.0,
                0,
                0,
                filesA,
                DiffComplexity.TRIVIAL
            );
        }

        // Estimate file distribution
        int filesInBoth = minFiles;
        int filesOnlyInA = filesA - minFiles;
        int filesOnlyInB = filesB - minFiles;

        // Determine complexity based on difference ratio
        DiffComplexity complexity = estimateDiffComplexity(filesA, filesB);

        return new SimilarityResult(
            estimatedSimilarity,
            filesOnlyInA,
            filesOnlyInB,
            filesInBoth,
            complexity
        );
    }

    /**
     * Estimate diff complexity based on file count differences.
     */
    private DiffComplexity estimateDiffComplexity(int filesA, int filesB) {
        int totalFiles = Math.max(filesA, filesB);
        int diffFiles = Math.abs(filesA - filesB);
        double diffRatio = (double) diffFiles / totalFiles;

        if (diffRatio < 0.05) return DiffComplexity.TRIVIAL;  // < 5% different
        if (diffRatio < 0.15) return DiffComplexity.SIMPLE;   // < 15% different
        if (diffRatio < 0.30) return DiffComplexity.MEDIUM;   // < 30% different
        return DiffComplexity.COMPLEX;                         // > 30% different
    }

    /**
     * Find duplicate groups for a specific project.
     */
    public List<CodeProjectDuplicateGroup> findDuplicatesFor(UUID projectId) {
        CodeProject project = codeProjectRepository.findById(projectId)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

        List<CodeProject> allProjects = codeProjectRepository.findAll();
        List<CodeProject> candidates = findDuplicateCandidates(project, allProjects);

        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        return Collections.singletonList(analyzeAndCreateGroup(project, candidates));
    }
}
