package io.quarkiverse.workitems.runtime.service;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.workitems.spi.WorkerCandidate;
import io.quarkiverse.workitems.spi.WorkerRegistry;

/**
 * Default WorkerRegistry — returns an empty list for every group.
 *
 * <p>
 * With this default, {@code candidateGroups} on a WorkItem does not trigger
 * pre-assignment. The group stays open; whoever claims first wins.
 *
 * <p>
 * Replace with an {@code @Alternative @Priority(1) @ApplicationScoped} bean
 * that resolves group membership from LDAP, Keycloak, CaseHub, or a hard-coded map.
 */
@ApplicationScoped
public class NoOpWorkerRegistry implements WorkerRegistry {

    @Override
    public List<WorkerCandidate> resolveGroup(final String groupName) {
        return List.of();
    }
}
