package io.casehub.work.api;

import java.util.UUID;

/**
 * Result for a single spawned child.
 *
 * @param workItemId the UUID of the created child WorkItem
 * @param callerRef echoed from {@link ChildSpec#callerRef()} — confirms the
 *        association between the caller's routing key and the new WorkItem
 */
public record SpawnedChild(UUID workItemId, String callerRef) {
}
