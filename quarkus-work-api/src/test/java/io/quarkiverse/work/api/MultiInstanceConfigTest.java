package io.quarkiverse.work.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MultiInstanceConfigTest {

    @Test
    void defaultParentRoleIsCoordinator() {
        var config = new MultiInstanceConfig(5, 3, null, null, null, false, null);
        assertThat(config.effectiveParentRole()).isEqualTo(ParentRole.COORDINATOR);
    }

    @Test
    void defaultOnThresholdReachedIsCancel() {
        var config = new MultiInstanceConfig(5, 3, null, null, null, false, null);
        assertThat(config.effectiveOnThresholdReached()).isEqualTo(OnThresholdReached.CANCEL);
    }

    @Test
    void defaultAssignmentStrategyIsPool() {
        var config = new MultiInstanceConfig(5, 3, null, null, null, false, null);
        assertThat(config.effectiveAssignmentStrategyName()).isEqualTo("pool");
    }

    @Test
    void explicitAssigneesRequiredWhenStrategyIsExplicit() {
        var config = new MultiInstanceConfig(2, 1, null, "explicit", null, false, null);
        assertThat(config.validate()).contains("explicitAssignees required when strategy is 'explicit'");
    }

    @Test
    void explicitAssigneesSizeMustMatchInstanceCount() {
        var config = new MultiInstanceConfig(3, 2, null, "explicit", null, false, List.of("alice", "bob"));
        assertThat(config.validate()).contains("explicitAssignees size (2) must match instanceCount (3)");
    }

    @Test
    void validConfigReturnsNoErrors() {
        var config = new MultiInstanceConfig(5, 3, ParentRole.COORDINATOR, "pool", OnThresholdReached.CANCEL, false, null);
        assertThat(config.validate()).isEmpty();
    }
}
