package io.casehub.work.ai.suggestion;

import java.util.UUID;

/**
 * Response from {@code GET /workitems/{id}/resolution-suggestion}.
 *
 * @param workItemId the WorkItem the suggestion is for
 * @param suggestion suggested resolution as a JSON string; {@code null} when no suggestion
 *        could be produced (model unavailable, no examples, or model call failed)
 * @param basedOn number of past completed WorkItems used as few-shot examples
 * @param modelAvailable {@code true} if a {@code ChatLanguageModel} CDI bean is configured
 */
public record ResolutionSuggestionResponse(
        UUID workItemId,
        String suggestion,
        int basedOn,
        boolean modelAvailable) {

    /** Convenience factory for the no-model case. */
    public static ResolutionSuggestionResponse noModel(final UUID workItemId) {
        return new ResolutionSuggestionResponse(workItemId, null, 0, false);
    }

    /** Convenience factory when model is available but no suggestion could be produced. */
    public static ResolutionSuggestionResponse noSuggestion(final UUID workItemId,
            final int basedOn) {
        return new ResolutionSuggestionResponse(workItemId, null, basedOn, true);
    }
}
