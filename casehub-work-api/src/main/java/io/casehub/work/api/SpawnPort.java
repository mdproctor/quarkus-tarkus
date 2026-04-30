package io.casehub.work.api;

import java.util.UUID;

/**
 * SPI for spawning child work units from a parent.
 * Implementations live in domain-specific modules (quarkus-work runtime for WorkItems).
 * quarkus-work fires events and wires PART_OF relations; it makes no decisions
 * about what child completion means.
 */
public interface SpawnPort {
    SpawnResult spawn(SpawnRequest request);

    void cancelGroup(UUID groupId, boolean cascadeChildren);
}
