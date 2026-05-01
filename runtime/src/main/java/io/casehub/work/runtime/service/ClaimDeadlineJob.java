package io.casehub.work.runtime.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class ClaimDeadlineJob {

    @Inject
    ExpiryLifecycleService expiryLifecycleService;

    @Scheduled(every = "${casehub.work.cleanup.expiry-check-seconds}s")
    public void checkUnclaimedPastDeadline() {
        expiryLifecycleService.checkClaimDeadlines();
    }
}
