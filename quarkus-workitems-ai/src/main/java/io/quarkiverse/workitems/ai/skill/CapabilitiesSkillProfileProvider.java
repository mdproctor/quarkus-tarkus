package io.quarkiverse.workitems.ai.skill;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.work.api.SkillProfile;
import io.quarkiverse.work.api.SkillProfileProvider;

/**
 * Builds a {@link SkillProfile} by joining the worker's declared capability tags.
 *
 * <p>
 * Example output: {@code "legal, nda-review, gdpr"}.
 * Zero DB access — useful as a baseline when no richer profile data is available.
 * Activate by declaring as {@code @Alternative @Priority(1)}.
 */
@ApplicationScoped
public class CapabilitiesSkillProfileProvider implements SkillProfileProvider {

    @Override
    public SkillProfile getProfile(final String workerId, final Set<String> capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            return SkillProfile.ofNarrative("");
        }
        return SkillProfile.ofNarrative(String.join(", ", capabilities));
    }
}
