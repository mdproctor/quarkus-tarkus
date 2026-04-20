package io.quarkiverse.workitems.runtime.model;

import java.util.List;

/**
 * Well-known link type constants for {@link WorkItemLink}.
 *
 * <h2>Extensibility — this is not an enum</h2>
 * <p>
 * Link types are plain strings stored in the {@code relation_type} column.
 * This class provides named constants for discoverability, but consuming
 * applications can use <em>any non-blank string</em> without schema changes:
 *
 * <pre>{@code
 * "design-spec"       // use the constant
 * "internal-wiki"     // custom — no registration needed
 * "runbook"           // custom — just use the string
 * }</pre>
 *
 * <h2>Convention</h2>
 * <p>
 * Well-known types use lowercase kebab-case. This is convention, not enforcement —
 * the system accepts any non-blank string.
 *
 * <h2>Attachments</h2>
 * <p>
 * {@link #ATTACHMENT} covers files stored externally (S3, GCS, MinIO, SharePoint).
 * WorkItems stores the <em>reference</em> (URL + title), not the file content.
 * This is an intentional design constraint — WorkItems is not a file store.
 */
public final class WorkItemLinkType {

    private WorkItemLinkType() {
    }

    /** General reference to a related resource. */
    public static final String REFERENCE = "reference";

    /**
     * A design specification document — architecture decisions, API specs,
     * wireframes, or any document that describes how something should be built.
     */
    public static final String DESIGN_SPEC = "design-spec";

    /**
     * A policy, regulation, or governance document — GDPR articles, internal
     * policies, ISO standards, regulatory guidance.
     */
    public static final String POLICY = "policy";

    /**
     * Supporting evidence for a decision — model outputs, audit logs, test results,
     * confidence scores, screenshots. Complements the ledger module's evidence field
     * with a direct URL to the artifact.
     */
    public static final String EVIDENCE = "evidence";

    /**
     * The source material this WorkItem was created from — the original request,
     * report, data file, or document that triggered this work.
     */
    public static final String SOURCE_DOCUMENT = "source-document";

    /**
     * A file stored externally (S3, GCS, MinIO, SharePoint, Confluence attachment).
     * WorkItems stores the URL reference only — the file content lives elsewhere.
     * This is intentional: WorkItems is not a file store.
     */
    public static final String ATTACHMENT = "attachment";

    /**
     * All well-known types, for validation and documentation.
     * Not a closed set — custom types are equally valid.
     */
    public static final List<String> WELL_KNOWN = List.of(
            REFERENCE, DESIGN_SPEC, POLICY, EVIDENCE, SOURCE_DOCUMENT, ATTACHMENT);
}
