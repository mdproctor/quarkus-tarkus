package io.casehub.work.ai.skill;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.work.api.SkillProfile;
import io.casehub.work.api.SkillProfileProvider;

/**
 * Reads a worker's skill profile from the {@link WorkerSkillProfile} entity.
 *
 * <p>
 * Falls back to empty narrative when no profile exists.
 * Activate by declaring as {@code @Alternative @Priority(1)}.
 */
@ApplicationScoped
public class WorkerProfileSkillProfileProvider implements SkillProfileProvider {

    private final Function<String, Optional<WorkerSkillProfile>> finder;

    /** CDI constructor — uses Panache finder. */
    public WorkerProfileSkillProfileProvider() {
        this.finder = workerId -> Optional.ofNullable(WorkerSkillProfile.findById(workerId));
    }

    /** Test constructor — injectable finder for unit testing without Panache. */
    WorkerProfileSkillProfileProvider(final Function<String, Optional<WorkerSkillProfile>> finder) {
        this.finder = finder;
    }

    @Override
    public SkillProfile getProfile(final String workerId, final Set<String> capabilities) {
        return finder.apply(workerId)
                .map(p -> SkillProfile.ofNarrative(p.narrative != null ? p.narrative : ""))
                .orElse(SkillProfile.ofNarrative(""));
    }
}
