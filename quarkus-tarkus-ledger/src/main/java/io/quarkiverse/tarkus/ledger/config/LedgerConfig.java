package io.quarkiverse.tarkus.ledger.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the Tarkus Ledger module.
 *
 * <p>
 * All keys are under the {@code quarkus.tarkus.ledger} prefix and are invisible
 * unless the {@code quarkus-tarkus-ledger} module is on the classpath.
 */
@ConfigMapping(prefix = "quarkus.tarkus.ledger")
public interface LedgerConfig {

    /**
     * Master switch. When {@code false}, no ledger entries are written regardless of other settings.
     *
     * @return {@code true} if the ledger is enabled (default)
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Hash chain configuration.
     *
     * @return the hash chain sub-configuration
     */
    HashChainConfig hashChain();

    /**
     * Decision context snapshot configuration.
     *
     * @return the decision context sub-configuration
     */
    DecisionContextConfig decisionContext();

    /**
     * Evidence capture configuration.
     *
     * @return the evidence sub-configuration
     */
    EvidenceConfig evidence();

    /**
     * Attestation endpoint configuration.
     *
     * @return the attestations sub-configuration
     */
    AttestationConfig attestations();

    /**
     * Trust score computation configuration.
     *
     * @return the trust score sub-configuration
     */
    TrustScoreConfig trustScore();

    /** Hash chain tamper-evidence settings. */
    interface HashChainConfig {

        /**
         * When {@code true}, each {@code LedgerEntry} carries a SHA-256 digest chained
         * to the digest of the previous entry for the same WorkItem.
         *
         * @return {@code true} if hash chaining is enabled (default)
         */
        @WithDefault("true")
        boolean enabled();
    }

    /** Decision context snapshot settings. */
    interface DecisionContextConfig {

        /**
         * When {@code true}, a JSON snapshot of the WorkItem state is captured at each
         * lifecycle transition and stored in {@code LedgerEntry.decisionContext}.
         * Required for GDPR Article 22 and EU AI Act Article 12 compliance.
         *
         * @return {@code true} if decision context snapshots are enabled (default)
         */
        @WithDefault("true")
        boolean enabled();
    }

    /** Structured evidence capture settings. */
    interface EvidenceConfig {

        /**
         * When {@code true}, structured evidence fields are accepted and stored per ledger entry.
         * Off by default — enabling without caller cooperation produces null evidence fields.
         *
         * @return {@code true} if evidence capture is enabled; {@code false} by default
         */
        @WithDefault("false")
        boolean enabled();
    }

    /** Peer attestation endpoint settings. */
    interface AttestationConfig {

        /**
         * When {@code true}, the {@code POST .../attestations} endpoint is active and
         * accepts peer attestations on ledger entries.
         *
         * @return {@code true} if attestations are enabled (default)
         */
        @WithDefault("true")
        boolean enabled();
    }

    /** EigenTrust reputation computation settings. */
    interface TrustScoreConfig {

        /**
         * When {@code true}, a nightly scheduled job computes EigenTrust-inspired trust
         * scores from ledger history. Off by default — trust scores require accumulated
         * history to be meaningful; enabling on a new deployment produces unreliable early scores.
         *
         * @return {@code true} if trust score computation is enabled; {@code false} by default
         */
        @WithDefault("false")
        boolean enabled();

        /**
         * Exponential decay half-life in days for historical decision weighting.
         * Decisions older than this are down-weighted relative to recent ones.
         *
         * @return half-life in days (default 90)
         */
        @WithDefault("90")
        int decayHalfLifeDays();

        /**
         * When {@code true}, trust scores influence WorkItem routing suggestions via CDI events.
         * Separate from score computation — scores must be enabled and accumulated first.
         *
         * @return {@code true} if trust-score-based routing suggestions are enabled; {@code false} by default
         */
        @WithDefault("false")
        boolean routingEnabled();
    }
}
