package io.quarkiverse.work.runtime.service;

import java.util.UUID;

/**
 * Thrown when a MANUAL label with the given path is not found on a WorkItem.
 */
public class LabelNotFoundException extends RuntimeException {

    public final UUID workItemId;
    public final String labelPath;

    public LabelNotFoundException(final UUID workItemId, final String labelPath) {
        super("MANUAL label '" + labelPath + "' not found on WorkItem " + workItemId);
        this.workItemId = workItemId;
        this.labelPath = labelPath;
    }
}
