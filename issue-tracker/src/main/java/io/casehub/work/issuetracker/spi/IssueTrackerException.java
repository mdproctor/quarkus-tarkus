package io.casehub.work.issuetracker.spi;

/**
 * Thrown by {@link IssueTrackerProvider} implementations when a remote call fails.
 *
 * <p>
 * Callers should treat this as a recoverable error unless
 * {@link #isNotFound()} is true, in which case the referenced issue does not exist.
 */
public class IssueTrackerException extends RuntimeException {

    private final boolean notFound;

    /** Create an exception for a generic remote failure. */
    public IssueTrackerException(final String message) {
        super(message);
        this.notFound = false;
    }

    /** Create an exception with an underlying cause. */
    public IssueTrackerException(final String message, final Throwable cause) {
        super(message, cause);
        this.notFound = false;
    }

    /** Create an exception indicating the referenced issue does not exist. */
    public static IssueTrackerException notFound(final String externalRef) {
        return new IssueTrackerException("Issue not found: " + externalRef, true);
    }

    private IssueTrackerException(final String message, final boolean notFound) {
        super(message);
        this.notFound = notFound;
    }

    /** Returns {@code true} if the remote issue does not exist. */
    public boolean isNotFound() {
        return notFound;
    }
}
