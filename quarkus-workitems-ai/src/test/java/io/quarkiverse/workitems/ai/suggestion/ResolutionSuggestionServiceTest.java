package io.quarkiverse.workitems.ai.suggestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemStatus;
import io.quarkiverse.workitems.testing.InMemoryWorkItemStore;

class ResolutionSuggestionServiceTest {

    private InMemoryWorkItemStore store;
    private ChatModel mockModel;
    private ResolutionSuggestionService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryWorkItemStore();
        mockModel = mock(ChatModel.class);
        service = new ResolutionSuggestionService(store, mockModel, 5);
    }

    @Test
    void isModelAvailable_trueWhenModelPresent() {
        assertThat(service.isModelAvailable()).isTrue();
    }

    @Test
    void isModelAvailable_falseWhenModelNull() {
        final var s = new ResolutionSuggestionService(store, null, 5);
        assertThat(s.isModelAvailable()).isFalse();
    }

    @Test
    void suggest_noModel_returnsNull() {
        final var s = new ResolutionSuggestionService(store, null, 5);
        final WorkItem wi = workItem("legal", WorkItemStatus.IN_PROGRESS, null);
        assertThat(s.suggest(wi)).isNull();
        verify(mockModel, never()).chat(anyString());
    }

    @Test
    void suggest_noExamples_returnsNull() {
        // No completed WorkItems in store
        when(mockModel.chat(anyString())).thenReturn("{\"decision\":\"APPROVED\"}");
        final WorkItem wi = workItem("legal", WorkItemStatus.IN_PROGRESS, null);
        assertThat(service.suggest(wi)).isNull();
        verify(mockModel, never()).chat(anyString());
    }

    @Test
    void suggest_withMatchingCategoryExamples_callsModelAndReturnsSuggestion() {
        final WorkItem past = completed("legal", "{\"decision\":\"APPROVED\"}");
        store.put(past);
        when(mockModel.chat(anyString())).thenReturn("{\"decision\":\"APPROVED\"}");

        final WorkItem current = workItem("legal", WorkItemStatus.IN_PROGRESS, null);
        final String result = service.suggest(current);

        assertThat(result).isEqualTo("{\"decision\":\"APPROVED\"}");
        verify(mockModel).chat(anyString());
    }

    @Test
    void suggest_categoryMismatch_fallsBackToAllCompleted() {
        final WorkItem past = completed("finance", "{\"approved\":true}");
        store.put(past);
        when(mockModel.chat(anyString())).thenReturn("{\"approved\":true}");

        // Current item has a different category
        final WorkItem current = workItem("legal", WorkItemStatus.IN_PROGRESS, null);
        final String result = service.suggest(current);

        // Falls back to all completed — finds the finance example
        assertThat(result).isEqualTo("{\"approved\":true}");
    }

    @Test
    void suggest_modelThrows_returnsNull() {
        final WorkItem past = completed("legal", "{\"decision\":\"APPROVED\"}");
        store.put(past);
        when(mockModel.chat(anyString())).thenThrow(new RuntimeException("model unavailable"));

        final WorkItem current = workItem("legal", WorkItemStatus.IN_PROGRESS, null);
        assertThat(service.suggest(current)).isNull();
    }

    @Test
    void suggest_historyLimitRespected() {
        for (int i = 0; i < 10; i++) {
            store.put(completed("legal", "{\"n\":" + i + "}"));
        }
        final var limitedService = new ResolutionSuggestionService(store, mockModel, 3);
        when(mockModel.chat(anyString())).thenAnswer(inv -> {
            final String prompt = inv.getArgument(0, String.class);
            // prompt should contain at most 3 examples
            final long count = prompt.chars().filter(c -> c == '\n').count();
            assertThat(prompt).contains("Example 1").contains("Example 2").contains("Example 3")
                    .doesNotContain("Example 4");
            return "{}";
        });

        limitedService.suggest(workItem("legal", WorkItemStatus.IN_PROGRESS, null));
        verify(mockModel).chat(anyString());
    }

    @Test
    void exampleCount_returnsCountWithoutCallingModel() {
        store.put(completed("legal", "{\"ok\":true}"));
        store.put(completed("legal", "{\"ok\":false}"));
        final WorkItem wi = workItem("legal", WorkItemStatus.IN_PROGRESS, null);
        assertThat(service.exampleCount(wi)).isEqualTo(2);
        verify(mockModel, never()).chat(anyString());
    }

    @Test
    void suggest_completedWithNullResolution_notIncludedInExamples() {
        // Completed but no resolution — should not be used as example
        final WorkItem noResolution = completed("legal", null);
        noResolution.resolution = null;
        store.put(noResolution);
        when(mockModel.chat(anyString())).thenReturn("{}");

        final WorkItem current = workItem("legal", WorkItemStatus.IN_PROGRESS, null);
        assertThat(service.suggest(current)).isNull(); // no usable examples
        verify(mockModel, never()).chat(anyString());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private WorkItem workItem(final String category, final WorkItemStatus status,
            final String resolution) {
        final WorkItem wi = new WorkItem();
        wi.id = UUID.randomUUID();
        wi.title = "Test work item";
        wi.category = category;
        wi.status = status;
        wi.resolution = resolution;
        wi.createdAt = Instant.now();
        wi.updatedAt = Instant.now();
        return wi;
    }

    private WorkItem completed(final String category, final String resolution) {
        final WorkItem wi = workItem(category, WorkItemStatus.COMPLETED, resolution);
        wi.completedAt = Instant.now();
        return wi;
    }
}
