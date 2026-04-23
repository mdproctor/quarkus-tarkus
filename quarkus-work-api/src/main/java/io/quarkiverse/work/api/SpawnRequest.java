package io.quarkiverse.work.api;

import java.util.List;
import java.util.UUID;

/**
 * Request to spawn a group of child work units from a parent.
 *
 * @param parentId the UUID of the parent WorkItem
 * @param idempotencyKey caller-supplied deduplication key — a second call with the
 *        same key on the same parent returns the existing group (HTTP 200)
 *        without creating duplicate children
 * @param children one entry per child to spawn; must not be null or empty
 */
public record SpawnRequest(
        UUID parentId,
        String idempotencyKey,
        List<ChildSpec> children) {
}
