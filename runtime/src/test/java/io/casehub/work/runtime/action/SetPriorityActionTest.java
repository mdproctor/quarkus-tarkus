package io.casehub.work.runtime.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.casehub.work.runtime.model.WorkItem;
import io.casehub.work.runtime.model.WorkItemPriority;

class SetPriorityActionTest {

    private final SetPriorityAction action = new SetPriorityAction();

    @Test
    void type_isSET_PRIORITY() {
        assertThat(action.type()).isEqualTo("SET_PRIORITY");
    }

    @Test
    void apply_setsPriority() {
        final var wi = new WorkItem();
        wi.id = UUID.randomUUID();
        action.apply(wi, Map.of("priority", "CRITICAL"));
        assertThat(wi.priority).isEqualTo(WorkItemPriority.CRITICAL);
    }

    @Test
    void apply_invalidPriority_noChange() {
        final var wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.priority = WorkItemPriority.NORMAL;
        action.apply(wi, Map.of("priority", "NOT_VALID"));
        assertThat(wi.priority).isEqualTo(WorkItemPriority.NORMAL);
    }
}
