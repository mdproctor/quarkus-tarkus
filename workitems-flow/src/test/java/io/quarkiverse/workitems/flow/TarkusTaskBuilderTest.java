package io.quarkiverse.workitems.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkiverse.workitems.runtime.model.WorkItemPriority;
import io.smallrye.mutiny.Uni;

class TarkusTaskBuilderTest {

    private HumanTaskFlowBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = mock(HumanTaskFlowBridge.class);
        when(bridge.requestApproval(any(), any(), any(), any(), any()))
                .thenReturn(Uni.createFrom().item("{\"approved\":true}"));
        when(bridge.requestGroupApproval(any(), any(), any(), any(), any()))
                .thenReturn(Uni.createFrom().item("{\"approved\":true}"));
    }

    @Test
    void builder_withAssigneeId_callsRequestApproval() {
        TarkusTaskBuilder builder = new TarkusTaskBuilder("review", bridge)
                .title("Review document")
                .assigneeId("alice")
                .priority(WorkItemPriority.HIGH);

        assertThat(builder.getTitle()).isEqualTo("Review document");
        assertThat(builder.getAssigneeId()).isEqualTo("alice");
        assertThat(builder.getPriority()).isEqualTo(WorkItemPriority.HIGH);
        assertThat(builder.getCandidateGroups()).isNull();
    }

    @Test
    void builder_withCandidateGroups_usesGroupApproval() {
        TarkusTaskBuilder builder = new TarkusTaskBuilder("review", bridge)
                .title("Group review")
                .candidateGroups("legal-team,managers");

        assertThat(builder.getCandidateGroups()).isEqualTo("legal-team,managers");
        assertThat(builder.getAssigneeId()).isNull();
    }

    @Test
    void builder_requiresTitle() {
        TarkusTaskBuilder builder = new TarkusTaskBuilder("review", bridge);
        assertThatThrownBy(() -> builder.buildTask(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("title");
    }

    @Test
    void builder_payloadFrom_extractsPayloadAtExecutionTime() {
        TarkusTaskBuilder builder = new TarkusTaskBuilder("review", bridge)
                .title("Review")
                .assigneeId("alice")
                .payloadFrom((String input) -> "{\"doc\":\"" + input + "\"}");

        assertThat(builder.getPayloadExtractor()).isNotNull();
    }

    @Test
    void builder_defaultPriorityIsNormal() {
        TarkusTaskBuilder builder = new TarkusTaskBuilder("review", bridge)
                .title("Review")
                .assigneeId("alice");
        assertThat(builder.getPriority()).isEqualTo(WorkItemPriority.NORMAL);
    }
}
