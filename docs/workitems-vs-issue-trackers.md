# WorkItems vs Issue Trackers — Why You Need Both

## The question developers ask

> "GitHub Issues already tracks tasks and bugs. Why would I also run WorkItems in my application?"

It's a fair question. Both systems manage units of work. Both have titles, descriptions, and a lifecycle. Both can be assigned to people. The overlap is real — and that's exactly why the distinction matters.

The short answer: **issue trackers track *what* needs to be done; WorkItems track *who is doing it, whether they'll finish in time, and whether that's auditable*.** They operate at different layers, for different audiences, on different timescales.

---

## What issue trackers do

GitHub Issues, Jira, Linear, and their peers are **project management and collaboration tools**:

- They capture requirements, bug reports, and feature requests — often from external contributors or customers
- They are the permanent record of *why* a decision was made, with threaded discussion
- They link to pull requests, milestones, and releases — they live in development time
- They are typically **visible to stakeholders outside your running application** — the community, your product team, your QA team
- Lifecycle is simple: open → (in progress) → closed. No delegation, no expiry, no SLA.
- They have **no runtime presence**. Your application doesn't fire CDI events when a Jira ticket moves to "In Review".

Issue trackers answer: *"What are we building and what's wrong with what we built?"*

---

## What WorkItems do

WorkItems are an **operational, runtime concern** — they live inside your running application alongside your business logic:

- They represent tasks that require **human attention within your system's transaction boundary** — approvals, reviews, escalations, delegations
- They have a **rich lifecycle**: `PENDING → ASSIGNED → IN_PROGRESS → COMPLETED | REJECTED | DELEGATED | SUSPENDED | EXPIRED | ESCALATED`
- They have **SLA enforcement**: claim deadlines, completion deadlines, escalation policies that fire automatically when breached
- They fire **CDI events** on every transition — your application reacts in real time
- They carry a **full audit trail** with actor, timestamp, rationale, and cryptographic hash chain (with the ledger module)
- They support **delegation chains** — Alice assigns to Bob, Bob delegates to Carol, the chain is preserved
- They handle **group routing** — `candidateGroups`, `candidateUsers`, label-based queue routing via filters
- They contain **private business context** — PII, contract terms, financial amounts — that should not be in a public issue tracker

WorkItems answer: *"Who is handling this right now, have they started, will they finish before the deadline, and can we prove it?"*

---

## The key differences

| Dimension | Issue Tracker | WorkItem |
|---|---|---|
| **Audience** | Developers, community, stakeholders | Internal application users and agents |
| **Timescale** | Days to months | Minutes to days |
| **SLA** | None | Claim deadline + completion deadline + escalation |
| **Runtime presence** | None | CDI events, scheduled jobs, CDI observers |
| **Lifecycle richness** | Open / Closed | 10 states + delegation + suspension |
| **Audit** | Comments (mutable) | Append-only log + optional hash chain |
| **Privacy** | Often public or semi-public | Internal; can contain PII and sensitive data |
| **Routing** | Manual assignment | Label-based queues, filter chains, candidate groups |
| **Delegation** | Reassign (no chain) | Full delegation chain with owner tracking |
| **Expiry** | Never | Automatic expiry + escalation policy |
| **Compliance** | No | GDPR Art.22 rationale, plan reference, evidence |

---

## How they complement each other

The most powerful pattern is treating the issue tracker and WorkItems as **complementary layers of the same process**, not competing alternatives.

### Pattern 1 — Issue spawns WorkItem

A GitHub Issue is filed for a security vulnerability. Your application automatically creates a WorkItem for the internal triage team:

```
GitHub Issue #482 "CVE-2026-1234 affects our crypto dependency"
  → creates WorkItem "Security triage: CVE-2026-1234"
      assignee: security-team (candidateGroup)
      claimDeadline: +4h (SLA: must be claimed within 4 hours)
      expiresAt: +24h (SLA: must be triaged within 24 hours)
      payload: {"issueUrl": "...", "cvssScore": 9.1, "affectedVersions": ["2.x"]}
      linkedIssues: [github:mdproctor/myapp#482]
```

The WorkItem carries the operational urgency (deadlines, escalation, delegation). The GitHub Issue carries the community discussion, PR references, and public disclosure timeline.

### Pattern 2 — WorkItem closes Issue

When the WorkItem is completed (triage finished, patch deployed), the issue tracker integration automatically closes the GitHub Issue with a comment:

```
WorkItem completed by alice (rationale: "Patch applied in v2.1.1, deployed to prod")
  → closes GitHub Issue #482 with comment:
    "Resolved in v2.1.1. Internal triage complete. /cc @security-team"
```

### Pattern 3 — WorkItem references Issue without automation

A compliance review WorkItem references the Jira epic it was spawned from — no automatic sync, just a traceable link for auditors:

```
WorkItem "GDPR data deletion review — customer request #44291"
  linkedIssues: [jira:GDPR-1042]  ← the Jira epic governing data deletion policy
```

---

## What information goes where

### Always in the issue tracker

- Bug description and reproduction steps
- Feature requirements and acceptance criteria
- Community discussion and external feedback
- Linked pull requests and release tags
- Public-facing status ("under investigation", "fixed in v2.1.1")
- Milestones and roadmap context

### Always in WorkItems

- SLA clocks and deadlines
- Delegation chain ("Alice → Bob → Carol")
- Candidate assignment groups ("who CAN pick this up")
- Private payload — contract terms, financial amounts, PII, internal incident details
- Escalation history and policy
- Audit log with actor identities and timestamps
- GDPR Art.22 decision rationale and evidence
- Internal resolution details that should not be public

### Either, depending on your policy

| Information | Issue tracker if... | WorkItem if... |
|---|---|---|
| Task title | It's a public bug or feature | It's an internal approval or review |
| Assignee | Assignment is permanent project ownership | Assignment is operational (may delegate/expire) |
| Status | For external stakeholders | For internal SLA and routing |
| Comments/notes | For community discussion | For internal audit detail field |
| Priority | For release planning | For queue routing and SLA tier |

---

## Best practices

### 1. One WorkItem per operational unit, one Issue per concern

Don't create a GitHub Issue for every individual WorkItem — that defeats the purpose of having both. Create one Issue per *problem or feature*, and one WorkItem per *operational execution* of that problem.

Example: one GitHub Issue "Roll out 2FA to enterprise customers" spawns WorkItems for each customer account that needs a human touchpoint.

### 2. Keep sensitive data out of issue trackers

If the WorkItem's payload contains a customer name, contract value, or security finding, do not copy it to the GitHub Issue body. Link the two, but let the issue contain only what you would tell the public.

### 3. Use links for traceability, not duplication

The link (`workItemId → github:owner/repo#42`) is the relationship. The title in `WorkItemIssueLink.title` is a cached display label — not the source of truth. The issue tracker is the source of truth for its own data; the WorkItem is the source of truth for its own operational state.

### 4. Automate the bridge at boundaries, not throughout

Automate issue → WorkItem creation (when an issue is filed, create a triage task). Automate WorkItem → issue closure (when triage is done, close the issue). Don't try to keep every field in sync at every step — that creates coupling without value.

### 5. Let WorkItems expire; let issues stay open

A WorkItem that expires fires an escalation event. The linked GitHub Issue does not close just because the SLA was breached — it stays open as the permanent record. The WorkItem's expiry is an internal signal; the issue's state is a public one.

---

## Using the integration

Add the module:

```xml
<dependency>
  <groupId>io.quarkiverse.workitems</groupId>
  <artifactId>quarkus-workitems-issue-tracker</artifactId>
  <version>${workitems.version}</version>
</dependency>
```

Configure GitHub:

```properties
quarkus.workitems.issue-tracker.github.token=ghp_...
quarkus.workitems.issue-tracker.github.default-repository=myorg/myapp
quarkus.workitems.issue-tracker.github.auto-close-on-complete=true
```

Link an existing issue:

```bash
curl -X POST /workitems/{id}/issues \
  -d '{"trackerType":"github","externalRef":"myorg/myapp#42","linkedBy":"alice"}'
```

Create a new GitHub issue and link it in one step:

```bash
curl -X POST /workitems/{id}/issues/create \
  -d '{"title":"Security triage required","body":"CVE-2026-1234...","linkedBy":"system"}'
```

Plug in your own tracker (Jira, Linear, etc.):

```java
@ApplicationScoped
@Alternative
@Priority(1)
public class JiraIssueTrackerProvider implements IssueTrackerProvider {
    @Override public String trackerType() { return "jira"; }
    // ...
}
```

---

## Summary

Issue trackers and WorkItems solve different problems. Issue trackers are the long-lived public record of what was decided and why. WorkItems are the short-lived operational machinery that makes sure humans complete their tasks on time, with accountability.

Used together: an issue captures the *requirement*, a WorkItem enforces the *execution*.
