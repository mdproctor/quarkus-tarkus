package io.quarkiverse.workitems.runtime.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies WorkItemsConfig routing sub-group is correctly configured.
 * Issue #116, Epics #100/#102.
 */
@QuarkusTest
class WorkItemsConfigRoutingTest {

    @Inject
    WorkItemsConfig config;

    @Test
    void routingStrategy_defaultsToLeastLoaded() {
        assertThat(config.routing().strategy()).isEqualTo("least-loaded");
    }

    @Test
    void routingConfig_isAccessible_fromInjectedConfig() {
        assertThat(config.routing()).isNotNull();
    }
}
