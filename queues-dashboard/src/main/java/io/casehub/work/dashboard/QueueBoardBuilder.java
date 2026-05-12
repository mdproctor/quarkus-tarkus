package io.casehub.work.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.casehub.work.runtime.model.LabelPersistence;
import io.casehub.work.runtime.model.WorkItem;

/**
 * Pure static utility: converts a list of WorkItems into a 3×3 queue grid.
 * No CDI, no Quarkus — fully testable with plain JUnit.
 */
public final class QueueBoardBuilder {

    public static final String[] TIERS = { "review/urgent", "review/standard", "review/routine" };
    public static final String[] TIER_LABELS = { "Urgent", "Standard", "Routine" };
    public static final String[] STATES = { "unassigned", "claimed", "active" };

    private QueueBoardBuilder() {
    }

    /**
     * Returns the review tier label from a WorkItem's INFERRED labels,
     * or {@code null} if none.
     */
    public static String tier(final WorkItem wi) {
        if (wi.labels == null) {
            return null;
        }
        return wi.labels.stream()
                .filter(l -> l.persistence == LabelPersistence.INFERRED)
                .map(l -> l.path)
                .filter(p -> p.equals("review/urgent") || p.equals("review/standard")
                        || p.equals("review/routine"))
                .findFirst().orElse(null);
    }

    /**
     * Returns the state sub-label (unassigned/claimed/active) from a WorkItem's
     * INFERRED labels, or {@code null} if none.
     */
    public static String state(final WorkItem wi) {
        if (wi.labels == null) {
            return null;
        }
        return wi.labels.stream()
                .filter(l -> l.persistence == LabelPersistence.INFERRED)
                .map(l -> l.path)
                .filter(p -> p.endsWith("/unassigned") || p.endsWith("/claimed") || p.endsWith("/active"))
                .map(p -> p.substring(p.lastIndexOf('/') + 1))
                .findFirst().orElse(null);
    }

    /**
     * Builds a grid: tier → state → list of item titles.
     * Items without a tier or state label are omitted.
     */
    public static Map<String, Map<String, List<String>>> build(final List<WorkItem> items) {
        final Map<String, Map<String, List<String>>> grid = new LinkedHashMap<>();
        for (final String tier : TIERS) {
            final Map<String, List<String>> stateMap = new LinkedHashMap<>();
            for (final String state : STATES) {
                stateMap.put(state, new ArrayList<>());
            }
            grid.put(tier, stateMap);
        }
        for (final WorkItem wi : items) {
            final String tier = tier(wi);
            final String state = state(wi);
            if (tier != null && state != null && grid.containsKey(tier) && grid.get(tier).containsKey(state)) {
                grid.get(tier).get(state).add(wi.title != null ? wi.title : "(no title)");
            }
        }
        return grid;
    }

    /** Formats a cell: "—" if empty, first title if one item, "title (+N more)" if multiple. */
    public static String formatCell(final List<String> titles) {
        if (titles == null || titles.isEmpty()) {
            return "\u2014";
        }
        if (titles.size() == 1) {
            return truncate(titles.get(0), 28);
        }
        return truncate(titles.get(0), 20) + " (+" + (titles.size() - 1) + " more)";
    }

    private static String truncate(final String s, final int max) {
        return s == null ? "" : (s.length() <= max ? s : s.substring(0, max - 1) + "\u2026");
    }
}
