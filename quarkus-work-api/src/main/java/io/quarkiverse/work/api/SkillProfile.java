package io.quarkiverse.work.api;

import java.util.Map;

/**
 * A worker's skill description in two forms:
 * <ul>
 * <li>{@link #narrative} — prose for embedding-based matchers</li>
 * <li>{@link #attributes} — structured data for numerical matchers</li>
 * </ul>
 */
public record SkillProfile(String narrative, Map<String, Object> attributes) {

    /** Convenience factory — prose only, no structured attributes. */
    public static SkillProfile ofNarrative(final String narrative) {
        return new SkillProfile(narrative, Map.of());
    }
}
