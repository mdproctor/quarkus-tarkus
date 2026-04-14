package io.quarkiverse.tarkus.runtime.service;

import io.quarkiverse.tarkus.runtime.model.WorkItem;

public interface EscalationPolicy {

    /** Called when a WorkItem's expiresAt has passed without resolution. */
    void onExpired(WorkItem workItem);

    /** Called when a WorkItem's claimDeadline has passed without being claimed. */
    void onUnclaimedPastDeadline(WorkItem workItem);
}
