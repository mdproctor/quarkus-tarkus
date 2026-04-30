package io.casehub.work.api;

/**
 * Minimal WorkItem context passed to {@link WorkerSelectionStrategy#select}.
 *
 * <p>
 * Decouples strategies from the WorkItem JPA entity. CaseHub constructs this from
 * {@code TaskRequest}; WorkItems constructs it from the {@code WorkItem} entity.
 *
 * @param category WorkItem category (may be null)
 * @param priority WorkItemPriority name e.g. "HIGH" (may be null)
 * @param requiredCapabilities comma-separated capability tags (may be null)
 * @param candidateGroups comma-separated group names (may be null)
 * @param candidateUsers comma-separated user IDs (may be null)
 * @param title work item title — used by semantic matchers (may be null)
 * @param description work item description — used by semantic matchers (may be null)
 */
public record SelectionContext(
        String category,
        String priority,
        String requiredCapabilities,
        String candidateGroups,
        String candidateUsers,
        String title,
        String description) {
}
