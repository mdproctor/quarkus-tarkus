package io.quarkiverse.work.api;

import java.util.List;
import java.util.UUID;

/**
 * Result of a spawn operation.
 *
 * @param groupId UUID of the {@code WorkItemSpawnGroup} tracking this spawn batch
 * @param children one entry per child created, in the same order as the request
 * @param created true if a new group was created; false if the idempotencyKey matched
 *        an existing group (the existing group is returned, no children created)
 */
public record SpawnResult(UUID groupId, List<SpawnedChild> children, boolean created) {
}
