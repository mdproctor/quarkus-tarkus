package io.quarkiverse.work.runtime.multiinstance;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

import io.quarkiverse.work.api.InstanceAssignmentStrategy;
import io.quarkiverse.work.api.MultiInstanceContext;
import io.quarkiverse.work.runtime.model.WorkItem;

/**
 * Assigns each child instance to the corresponding named user from {@code explicitAssignees}.
 *
 * <p>
 * The list must be exactly the same size as {@code instanceCount}. The first assignee
 * goes to the first instance, the second to the second, and so on. Use this strategy
 * when the caller knows exactly who should handle each instance.
 *
 * <p>
 * Throws {@link IllegalArgumentException} if the {@code explicitAssignees} list is null
 * or does not match the number of instances.
 */
@ApplicationScoped
@Named("explicit")
public class ExplicitListAssignmentStrategy implements InstanceAssignmentStrategy {

    /**
     * Assigns each instance to the corresponding user in {@code explicitAssignees}.
     *
     * @param instances ordered list of child WorkItems, not yet persisted by this call
     * @param context parent WorkItem and resolved MultiInstanceConfig
     * @throws IllegalArgumentException if explicitAssignees is null or size does not match instances
     */
    @Override
    public void assign(final List<Object> instances, final MultiInstanceContext context) {
        final List<String> assignees = context.config().explicitAssignees();
        if (assignees == null || assignees.size() != instances.size()) {
            throw new IllegalArgumentException(
                    "explicitAssignees size must match instanceCount; got "
                            + (assignees == null ? "null" : assignees.size())
                            + ", expected " + instances.size());
        }
        for (int i = 0; i < instances.size(); i++) {
            ((WorkItem) instances.get(i)).assigneeId = assignees.get(i);
        }
    }
}
