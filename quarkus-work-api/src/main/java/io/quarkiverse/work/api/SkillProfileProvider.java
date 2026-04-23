package io.quarkiverse.work.api;

import java.util.Set;

/**
 * SPI for building a worker's {@link SkillProfile}.
 *
 * <p>
 * Implement as {@code @ApplicationScoped @Alternative @Priority(1)} to override
 * the active built-in. Built-in implementations (in quarkus-workitems-ai):
 * {@code CapabilitiesSkillProfileProvider}, {@code WorkerProfileSkillProfileProvider},
 * {@code ResolutionHistorySkillProfileProvider}.
 *
 * @see SkillProfile
 */
@FunctionalInterface
public interface SkillProfileProvider {

    /**
     * Build a skill profile for the given worker.
     *
     * @param workerId the worker identifier
     * @param capabilities the worker's declared capabilities (from {@link WorkerCandidate})
     * @return the worker's skill profile; never null
     */
    SkillProfile getProfile(String workerId, Set<String> capabilities);
}
