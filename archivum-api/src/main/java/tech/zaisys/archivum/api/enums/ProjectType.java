package tech.zaisys.archivum.api.enums;

/**
 * Types of code projects that can be detected.
 */
public enum ProjectType {

    /**
     * Maven project (pom.xml)
     */
    MAVEN("Maven", "Java"),

    /**
     * Gradle project (build.gradle, build.gradle.kts)
     */
    GRADLE("Gradle", "Java/Kotlin"),

    /**
     * NPM project (package.json)
     */
    NPM("NPM", "JavaScript/TypeScript"),

    /**
     * Go module (go.mod)
     */
    GO("Go", "Go"),

    /**
     * Python project (setup.py, pyproject.toml)
     */
    PYTHON("Python", "Python"),

    /**
     * Rust project (Cargo.toml)
     */
    RUST("Rust", "Rust"),

    /**
     * Git repository (.git/)
     */
    GIT("Git", "Various"),

    /**
     * Generic/unknown code project
     */
    GENERIC("Generic", "Unknown");

    private final String displayName;
    private final String language;

    ProjectType(String displayName, String language) {
        this.displayName = displayName;
        this.language = language;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getLanguage() {
        return language;
    }
}
