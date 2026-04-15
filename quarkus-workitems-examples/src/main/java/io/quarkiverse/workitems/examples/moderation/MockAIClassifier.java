package io.quarkiverse.workitems.examples.moderation;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Stub AI content classifier. Returns a hardcoded classification — no external API call.
 */
@ApplicationScoped
public class MockAIClassifier {

    /** Represents the output of the AI content classification model. */
    public record ContentFlag(String flagReason, double confidence, String modelVersion) {

        /**
         * Serialise the flag fields to a JSON evidence string for ledger capture.
         *
         * @return JSON string suitable for storing as ledger entry evidence
         */
        public String toEvidenceJson() {
            return String.format(
                    "{\"flagReason\":\"%s\",\"confidence\":%.2f,\"modelVersion\":\"%s\"}",
                    flagReason, confidence, modelVersion);
        }
    }

    /**
     * Analyse the given content and return a classification result.
     *
     * @param content the text content to classify
     * @return a {@link ContentFlag} with flag reason, confidence, and model version
     */
    public ContentFlag analyse(final String content) {
        return new ContentFlag("hate-speech", 0.73, "mod-v3");
    }
}
