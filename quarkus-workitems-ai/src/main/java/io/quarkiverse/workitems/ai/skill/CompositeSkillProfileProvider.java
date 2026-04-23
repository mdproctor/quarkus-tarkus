package io.quarkiverse.workitems.ai.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import io.quarkiverse.work.api.SkillProfile;
import io.quarkiverse.work.api.SkillProfileProvider;

/**
 * Combines the output of all currently-active {@link SkillProfileProvider} beans
 * into a single {@link SkillProfile} by concatenating their narratives and unioning
 * their attributes.
 *
 * <h2>Design: narrative concatenation</h2>
 *
 * <p>
 * Each active provider contributes a narrative fragment. This implementation joins
 * them with a newline separator:
 *
 * <pre>
 *   "Expert in NDA review, contract negotiation, intellectual property law"   ← WorkerProfileSkillProfileProvider
 *   "legal, nda-review, gdpr"                                                 ← CapabilitiesSkillProfileProvider
 *   "Approved NDA for Acme Corp; reviewed IP clause for Beta Ltd"             ← ResolutionHistorySkillProfileProvider
 * </pre>
 *
 * <p>
 * The combined narrative is then embedded by {@link EmbeddingSkillMatcher} as a
 * single vector. Concatenation is the simplest approach and generally effective —
 * embedding models handle multi-sentence inputs well.
 *
 * <h2>Why not something more sophisticated?</h2>
 *
 * <p>
 * Two alternatives exist but are left for future evaluation if use cases require it:
 *
 * <ul>
 * <li><strong>Provider-weighted merge</strong> — each provider carries a configured
 * contribution weight (e.g. {@code history: 0.6, capabilities: 0.4}). Weights are
 * applied to the embedding vectors before cosine similarity. More control, but
 * requires exposing vectors from {@link EmbeddingSkillMatcher}, making the
 * {@link io.quarkiverse.work.api.SkillMatcher} SPI stateful.</li>
 * <li><strong>Per-provider embeddings, averaged vectors</strong> — each provider's
 * narrative is embedded separately; the resulting vectors are averaged before
 * scoring. Better signal isolation when narratives are semantically orthogonal
 * (e.g. structured capability tags + free-text history), but doubles or triples
 * the number of embedding API calls per candidate per WorkItem.</li>
 * </ul>
 *
 * <h2>Activation</h2>
 *
 * <p>
 * Activate this provider via {@code @Alternative @Priority(n)} in your application.
 * {@code @Priority(2)} is used here so it takes precedence over the built-in
 * alternatives ({@code @Priority(1)}). Active providers are collected via
 * {@code Instance<SkillProfileProvider>} — whichever providers are active when
 * {@code CompositeSkillProfileProvider} is enabled will contribute to the composite.
 *
 * <p>
 * Example: activating {@code CompositeSkillProfileProvider} together with
 * {@code CapabilitiesSkillProfileProvider} yields a narrative from both
 * {@code WorkerProfileSkillProfileProvider} (always active as default) and capability tags.
 */
@ApplicationScoped
@Alternative
@Priority(2)
public class CompositeSkillProfileProvider implements SkillProfileProvider {

    private final Instance<SkillProfileProvider> allProviders;

    @Inject
    public CompositeSkillProfileProvider(final Instance<SkillProfileProvider> allProviders) {
        this.allProviders = allProviders;
    }

    /** Test constructor — supply delegates directly. */
    CompositeSkillProfileProvider(final List<SkillProfileProvider> delegates) {
        this.allProviders = null;
        this.delegates = delegates;
    }

    private List<SkillProfileProvider> delegates;

    @Override
    public SkillProfile getProfile(final String workerId, final Set<String> capabilities) {
        final List<SkillProfileProvider> sources = resolveDelegates();
        final List<String> narratives = new ArrayList<>();
        final Map<String, Object> attrs = new LinkedHashMap<>();

        for (final SkillProfileProvider provider : sources) {
            final SkillProfile profile = provider.getProfile(workerId, capabilities);
            if (profile.narrative() != null && !profile.narrative().isBlank()) {
                narratives.add(profile.narrative());
            }
            if (profile.attributes() != null) {
                attrs.putAll(profile.attributes());
            }
        }

        final String combined = narratives.stream()
                .collect(Collectors.joining("\n"));
        return new SkillProfile(combined.isBlank() ? null : combined, Map.copyOf(attrs));
    }

    private List<SkillProfileProvider> resolveDelegates() {
        if (delegates != null) {
            return delegates; // test path
        }
        return allProviders.stream()
                .filter(p -> !(p instanceof CompositeSkillProfileProvider))
                .collect(Collectors.toList());
    }
}
