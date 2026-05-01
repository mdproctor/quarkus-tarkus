package io.casehub.work.runtime.event;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkus.test.component.QuarkusComponentTest;

/**
 * Verifies that injecting {@link WorkItemEventBroadcaster} (the SPI interface)
 * resolves to {@link LocalWorkItemEventBroadcaster} via {@code @DefaultBean}.
 */
@QuarkusComponentTest(value = { LocalWorkItemEventBroadcaster.class })
class WorkItemEventBroadcasterWiringTest {

    @Inject
    WorkItemEventBroadcaster broadcaster;

    @Test
    void defaultBean_resolvesToLocalImpl() {
        assertThat(broadcaster).isInstanceOf(LocalWorkItemEventBroadcaster.class);
    }
}
