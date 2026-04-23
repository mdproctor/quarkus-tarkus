package io.quarkiverse.work.runtime.filter;

import java.util.Map;

/**
 * Descriptor for a single action within a filter rule.
 * {@code type} matches the CDI bean name of a {@link FilterAction} implementation.
 * {@code params} are passed verbatim to {@link FilterAction#apply}.
 */
public record ActionDescriptor(String type, Map<String, Object> params) {

    public static ActionDescriptor of(final String type, final Map<String, Object> params) {
        return new ActionDescriptor(type, params);
    }
}
