package io.quarkiverse.workitems.queues.model;

public record FilterAction(String type, String labelPath) {
    public static FilterAction applyLabel(final String labelPath) {
        return new FilterAction("APPLY_LABEL", labelPath);
    }
}
