package io.quarkiverse.workitems.ai.suggestion;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.workitems.ai.config.WorkItemsAiConfig;
import io.quarkiverse.workitems.runtime.model.WorkItem;
import io.quarkiverse.workitems.runtime.model.WorkItemStatus;
import io.quarkiverse.workitems.runtime.repository.WorkItemQuery;
import io.quarkiverse.workitems.runtime.repository.WorkItemStore;

/**
 * Finds similar completed WorkItems and calls a {@link ChatModel} to suggest
 * a resolution for the given WorkItem.
 *
 * <p>
 * When no {@code ChatModel} is configured, returns {@code null} as the suggestion
 * so the endpoint can report {@code modelAvailable: false} without failing.
 *
 * <p>
 * Example selection strategy: same category, most recently completed, up to
 * {@code quarkus.workitems.ai.suggestion.history-limit} items (default 5).
 * Falls back to all completed items when category is null or yields no results.
 */
@ApplicationScoped
public class ResolutionSuggestionService {

    private static final Logger LOG = Logger.getLogger(ResolutionSuggestionService.class);

    private final WorkItemStore workItemStore;
    private final ChatModel chatModel;
    private final int historyLimit;

    @Inject
    public ResolutionSuggestionService(
            final WorkItemStore workItemStore,
            final Instance<ChatModel> chatModelInstance,
            final WorkItemsAiConfig config) {
        this.workItemStore = workItemStore;
        this.chatModel = chatModelInstance.isResolvable() ? chatModelInstance.get() : null;
        this.historyLimit = config.suggestion().historyLimit();
    }

    /** Package-private constructor for unit tests. */
    ResolutionSuggestionService(final WorkItemStore workItemStore,
            final ChatModel chatModel, final int historyLimit) {
        this.workItemStore = workItemStore;
        this.chatModel = chatModel;
        this.historyLimit = historyLimit;
    }

    /**
     * Returns {@code true} when a {@link ChatModel} bean is configured.
     *
     * @return true if a chat model is available
     */
    public boolean isModelAvailable() {
        return chatModel != null;
    }

    /**
     * Suggest a resolution for {@code workItem} based on similar past completions.
     *
     * @param workItem the WorkItem to generate a suggestion for
     * @return suggested resolution JSON string, or {@code null} if no suggestion could be produced
     */
    public String suggest(final WorkItem workItem) {
        if (chatModel == null) {
            return null;
        }
        final List<WorkItem> examples = findExamples(workItem);
        if (examples.isEmpty()) {
            LOG.debugf("No completed examples found for WorkItem %s — skipping suggestion", workItem.id);
            return null;
        }
        try {
            final String prompt = buildPrompt(workItem, examples);
            return chatModel.chat(prompt);
        } catch (final Exception e) {
            LOG.warnf("ChatModel call failed for WorkItem %s: %s", workItem.id, e.getMessage());
            return null;
        }
    }

    /**
     * Returns the number of past examples that would be used for a suggestion,
     * without calling the model. Useful for the {@code basedOn} response field.
     *
     * @param workItem the WorkItem to find examples for
     * @return number of examples found
     */
    public int exampleCount(final WorkItem workItem) {
        return findExamples(workItem).size();
    }

    private List<WorkItem> findExamples(final WorkItem workItem) {
        // First try: same category (most informative)
        if (workItem.category != null && !workItem.category.isBlank()) {
            final List<WorkItem> byCat = completedWithResolution(workItem.category);
            if (!byCat.isEmpty()) {
                return byCat;
            }
        }
        // Fallback: any completed item with a resolution
        return completedWithResolution(null);
    }

    private List<WorkItem> completedWithResolution(final String category) {
        final WorkItemQuery query = WorkItemQuery.builder()
                .status(WorkItemStatus.COMPLETED)
                .category(category)
                .build();
        return workItemStore.scan(query).stream()
                .filter(wi -> wi.resolution != null && !wi.resolution.isBlank())
                .filter(wi -> wi.completedAt != null)
                .sorted(Comparator.comparing((WorkItem wi) -> wi.completedAt).reversed())
                .limit(historyLimit)
                .collect(Collectors.toList());
    }

    private String buildPrompt(final WorkItem current, final List<WorkItem> examples) {
        final StringBuilder sb = new StringBuilder();
        sb.append("You are a work item resolution assistant.\n\n");

        if (current.category != null && !current.category.isBlank()) {
            sb.append("Category: ").append(current.category).append("\n\n");
        }

        sb.append("The following are past resolutions for similar work items:\n\n");
        IntStream.range(0, examples.size()).forEach(i -> {
            final WorkItem ex = examples.get(i);
            sb.append("Example ").append(i + 1).append(":\n");
            sb.append("Title: ").append(ex.title).append("\n");
            if (ex.description != null && !ex.description.isBlank()) {
                sb.append("Description: ").append(ex.description).append("\n");
            }
            sb.append("Resolution: ").append(ex.resolution).append("\n\n");
        });

        sb.append("Current work item:\n");
        sb.append("Title: ").append(current.title).append("\n");
        if (current.description != null && !current.description.isBlank()) {
            sb.append("Description: ").append(current.description).append("\n");
        }
        if (current.payload != null && !current.payload.isBlank()) {
            sb.append("Payload: ").append(current.payload).append("\n");
        }

        sb.append("\nBased on the examples above, suggest a resolution for this work item. ")
                .append("Respond with only valid JSON matching the structure of the example resolutions. ")
                .append("Do not include any explanation outside the JSON.");

        return sb.toString();
    }
}
