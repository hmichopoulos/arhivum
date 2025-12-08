package tech.zaisys.archivum.server.exception;

/**
 * Exception thrown when a folder tree exceeds maximum size limits.
 * This prevents OutOfMemoryError for sources with millions of files.
 */
public class TreeSizeLimitExceededException extends RuntimeException {

    private final int actualSize;
    private final int maxSize;

    public TreeSizeLimitExceededException(int actualSize, int maxSize) {
        super(String.format("Tree size limit exceeded: %d nodes (max: %d). " +
            "Consider using file filtering or viewing individual folders.",
            actualSize, maxSize));
        this.actualSize = actualSize;
        this.maxSize = maxSize;
    }

    public int getActualSize() {
        return actualSize;
    }

    public int getMaxSize() {
        return maxSize;
    }
}
