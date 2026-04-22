package io.quarkiverse.workitems.ai.skill;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkiverse.work.api.SkillProfile;
import io.quarkiverse.work.api.SkillProfileProvider;
import io.quarkiverse.workitems.runtime.model.WorkItemStatus;
import io.quarkiverse.workitems.runtime.repository.WorkItemQuery;
import io.quarkiverse.workitems.runtime.repository.WorkItemStore;

/**
 * Builds a {@link SkillProfile} from a worker's completed WorkItem history.
 *
 * <p>
 * Aggregates category frequencies from the most recent N completed items.
 * Example narrative: {@code "Completed work: legal×23, nda×18, finance×4"}.
 * Activate by declaring as {@code @Alternative @Priority(1)}.
 */
@ApplicationScoped
public class ResolutionHistorySkillProfileProvider implements SkillProfileProvider {

    private final WorkItemStore workItemStore;
    private final int historyLimit;

    /**
     * CDI constructor — uses Panache-backed store and config-supplied limit.
     * Also used directly in unit tests (bypassing CDI) by passing the store and limit
     * as plain constructor arguments.
     */
    @Inject
    public ResolutionHistorySkillProfileProvider(
            final WorkItemStore workItemStore,
            @ConfigProperty(name = "quarkus.workitems.ai.semantic.history-limit", defaultValue = "50") final int historyLimit) {
        this.workItemStore = workItemStore;
        this.historyLimit = historyLimit;
    }

    @Override
    public SkillProfile getProfile(final String workerId, final Set<String> capabilities) {
        final Map<String, Long> frequencies = workItemStore
                .scan(WorkItemQuery.builder()
                        .assigneeId(workerId)
                        .statusIn(List.of(WorkItemStatus.COMPLETED))
                        .build())
                .stream()
                .filter(wi -> workerId.equals(wi.assigneeId)
                        && wi.status == WorkItemStatus.COMPLETED
                        && wi.category != null)
                .sorted(Comparator.comparing(
                        wi -> wi.completedAt != null ? wi.completedAt : Instant.EPOCH,
                        Comparator.reverseOrder()))
                .limit(historyLimit)
                .collect(Collectors.groupingBy(wi -> wi.category, Collectors.counting()));

        if (frequencies.isEmpty()) {
            return SkillProfile.ofNarrative("");
        }

        final String narrative = "Completed work: " + frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Long> comparingByValue().reversed())
                .map(e -> e.getKey() + "×" + e.getValue())
                .collect(Collectors.joining(", "));

        final Map<String, Object> attributes = new LinkedHashMap<>(frequencies);
        return new SkillProfile(narrative, attributes);
    }
}
