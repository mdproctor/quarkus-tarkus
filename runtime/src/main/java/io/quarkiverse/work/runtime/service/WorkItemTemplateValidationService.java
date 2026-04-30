package io.casehub.work.runtime.service;

import java.util.ArrayList;
import java.util.List;

import io.casehub.work.runtime.model.WorkItemTemplate;

/**
 * Validates {@link WorkItemTemplate} configuration for multi-instance rules.
 *
 * <p>
 * This service enforces invariants on multi-instance template configuration:
 * <ul>
 * <li>instanceCount must be at least 1 when set</li>
 * <li>requiredCount must be at least 1 when instanceCount is set</li>
 * <li>requiredCount cannot exceed instanceCount</li>
 * <li>COORDINATOR mode templates cannot have claim deadlines</li>
 * </ul>
 *
 * <p>
 * Non-multi-instance templates (instanceCount = null) skip validation.
 */
public final class WorkItemTemplateValidationService {

    private WorkItemTemplateValidationService() {
    }

    /**
     * Validates a WorkItemTemplate for multi-instance consistency.
     * Throws {@link IllegalArgumentException} with a descriptive message if invalid.
     * No-op for templates without instanceCount (non-multi-instance).
     *
     * @param template the template to validate
     * @throws IllegalArgumentException if any multi-instance rule is violated
     */
    public static void validate(final WorkItemTemplate template) {
        if (template.instanceCount == null) {
            return; // Not a multi-instance template; skip validation
        }

        final List<String> errors = new ArrayList<>();

        if (template.instanceCount < 1) {
            errors.add("instanceCount must be at least 1");
        }
        if (template.requiredCount == null || template.requiredCount < 1) {
            errors.add("requiredCount must be at least 1");
        }
        if (template.instanceCount >= 1 && template.requiredCount != null
                && template.requiredCount > template.instanceCount) {
            errors.add("requiredCount (" + template.requiredCount
                    + ") cannot exceed instanceCount (" + template.instanceCount + ")");
        }

        final boolean isCoordinator = template.parentRole == null
                || "COORDINATOR".equals(template.parentRole);
        if (isCoordinator
                && (template.defaultClaimHours != null || template.defaultClaimBusinessHours != null)) {
            errors.add("claimDeadline has no meaning in COORDINATOR mode; "
                    + "remove defaultClaimHours and defaultClaimBusinessHours");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid multi-instance template: " + String.join("; ", errors));
        }
    }
}
