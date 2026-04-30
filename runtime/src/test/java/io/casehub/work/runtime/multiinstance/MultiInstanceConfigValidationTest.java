package io.casehub.work.runtime.multiinstance;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItemTemplate;
import io.casehub.work.runtime.service.WorkItemTemplateValidationService;

/**
 * Unit tests for {@link WorkItemTemplateValidationService}.
 * Tests multi-instance template configuration validation rules.
 */
class MultiInstanceConfigValidationTest {

    private static WorkItemTemplate validMultiInstance() {
        WorkItemTemplate t = new WorkItemTemplate();
        t.name = "Test";
        t.createdBy = "test";
        t.instanceCount = 5;
        t.requiredCount = 3;
        return t;
    }

    @Test
    void validMultiInstanceTemplatePassesValidation() {
        assertThatCode(() -> WorkItemTemplateValidationService.validate(validMultiInstance()))
                .doesNotThrowAnyException();
    }

    @Test
    void instanceCountZeroIsRejected() {
        WorkItemTemplate t = validMultiInstance();
        t.instanceCount = 0;
        assertThatThrownBy(() -> WorkItemTemplateValidationService.validate(t))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("instanceCount must be at least 1");
    }

    @Test
    void requiredCountZeroIsRejected() {
        WorkItemTemplate t = validMultiInstance();
        t.requiredCount = 0;
        assertThatThrownBy(() -> WorkItemTemplateValidationService.validate(t))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requiredCount must be at least 1");
    }

    @Test
    void requiredCountExceedingInstanceCountIsRejected() {
        WorkItemTemplate t = validMultiInstance();
        t.instanceCount = 3;
        t.requiredCount = 5;
        assertThatThrownBy(() -> WorkItemTemplateValidationService.validate(t))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requiredCount (5) cannot exceed instanceCount (3)");
    }

    @Test
    void coordinatorWithClaimDeadlineIsRejected() {
        WorkItemTemplate t = validMultiInstance();
        t.parentRole = "COORDINATOR";
        t.defaultClaimHours = 24;
        assertThatThrownBy(() -> WorkItemTemplateValidationService.validate(t))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimDeadline has no meaning in COORDINATOR mode");
    }

    @Test
    void coordinatorWithClaimBusinessHoursIsRejected() {
        WorkItemTemplate t = validMultiInstance();
        t.parentRole = "COORDINATOR";
        t.defaultClaimBusinessHours = 8;
        assertThatThrownBy(() -> WorkItemTemplateValidationService.validate(t))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimDeadline has no meaning in COORDINATOR mode");
    }

    @Test
    void oneOfOneIsValid() {
        WorkItemTemplate t = validMultiInstance();
        t.instanceCount = 1;
        t.requiredCount = 1;
        assertThatCode(() -> WorkItemTemplateValidationService.validate(t))
                .doesNotThrowAnyException();
    }

    @Test
    void nonMultiInstanceTemplateSkipsValidation() {
        WorkItemTemplate t = new WorkItemTemplate();
        t.name = "Simple";
        t.createdBy = "test";
        // instanceCount null — not multi-instance
        assertThatCode(() -> WorkItemTemplateValidationService.validate(t))
                .doesNotThrowAnyException();
    }
}
