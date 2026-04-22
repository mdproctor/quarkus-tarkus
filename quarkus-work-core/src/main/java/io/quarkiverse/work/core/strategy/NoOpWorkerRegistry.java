package io.quarkiverse.work.core.strategy;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.work.api.WorkerCandidate;
import io.quarkiverse.work.api.WorkerRegistry;

/**
 * Default WorkerRegistry — returns empty list for all groups.
 * Groups remain claim-first until the application registers a real resolver.
 */
@ApplicationScoped
public class NoOpWorkerRegistry implements WorkerRegistry {

    @Override
    public List<WorkerCandidate> resolveGroup(final String groupName) {
        return List.of();
    }
}
