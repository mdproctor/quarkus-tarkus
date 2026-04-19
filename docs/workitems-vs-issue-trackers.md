# Why WorkItems? The case against "just use GitHub Issues"

The question comes up early: *"My team already has GitHub Issues. Why add another system?"*

It is a fair question. Both track units of work. Both have assignees, labels, and a lifecycle. The overlap is real. But the question contains a category error — GitHub Issues are a *project management* tool that lives outside your application. WorkItems are an *operational runtime* component that lives inside it. Once you see the distinction, the question dissolves.

---

## The argument that closes the debate: transaction boundaries

This is the one that matters most, because it is not a feature comparison — it is a fundamental architectural constraint.

When a loan officer approves a loan, two things must happen together:
1. The loan record in your database is marked `APPROVED`.
2. The WorkItem representing the approval task is marked `COMPLETED`.

If the database write fails, the WorkItem must not be marked complete. If the WorkItem cannot be saved, the approval must not proceed. They must be **atomic**.

WorkItems live inside your application's transaction boundary. Your service method persists the approval and calls `workItemService.complete()` in the same `@Transactional` context. If anything fails, both roll back.

GitHub Issues are external. There is no way to close a GitHub Issue inside a database transaction. You close it via HTTP call — after the fact, best-effort, with no rollback if your application crashes mid-operation. For any business-critical workflow, this is not a minor limitation. It is a disqualifying one.

**This alone makes GitHub Issues categorically unsuitable for anything that touches business logic.**

---

## The other arguments

Once you accept the transaction boundary argument, these follow naturally.

### 1. GitHub Issues have no runtime presence

When a WorkItem's SLA is breached, a `WorkItemLifecycleEvent` fires synchronously in your JVM. Your `@Observes` handler runs immediately — alerting a team, escalating to a supervisor, auto-rejecting a stale task. The entire escalation chain is code you write, running inside your application, with full access to your domain.

When a GitHub Issue's implicit deadline passes: nothing. There is no event. There is no automatic escalation. To approximate this you would need:
- A GitHub Action on a cron schedule
- An API call to GitHub to check issue age
- Rate limit handling (GitHub API: 5,000 requests/hour authenticated)
- Error handling for GitHub outages
- A webhook listener to receive the response
- Your own escalation logic, outside your application

WorkItems has a built-in expiry job, three escalation policies (`notify`, `reassign`, `auto-reject`), and a pluggable `EscalationPolicy` SPI. It runs inside your JVM. It never hits a rate limit.

### 2. Your data cannot leave your database

A WorkItem payload can contain anything: a customer's financial situation, a patient's medication record, a contract's commercial terms, an AI model's confidence breakdown, personally identifying information. All of it lives in your database, under your control, subject to your data residency requirements.

GitHub Issues, even in private repositories, store data on GitHub's servers. For GDPR, HIPAA, SOC 2, or any jurisdiction with data residency requirements, this is not a policy question — it is a legal one. "We put the loan application details in a GitHub Issue" is not a sentence that survives a compliance audit.

### 3. The lifecycle is completely different

| | GitHub Issues | WorkItems |
|---|---|---|
| **States** | Open, Closed | 10 states with enforced transitions |
| **Assignment** | Anyone can assign to anyone | PENDING → ASSIGNED requires claiming; guards enforced |
| **Deadlines** | No native concept | `claimDeadline` + `expiresAt`; automatic enforcement |
| **Escalation** | Manual | Automatic, configurable, code-pluggable |
| **Delegation** | Reassign (no history) | Full chain: owner → Alice → Bob → Carol, preserved |
| **Suspension** | Close and reopen | SUSPENDED with priorStatus, resumes correctly |
| **Group routing** | Assignees only | `candidateGroups` pool; self-service claiming |
| **Audit trail** | Mutable comments | Append-only log; optional cryptographic hash chain |

GitHub Issues are open or closed. WorkItems has a 10-state machine with guarded transitions, delegation chains, and suspension semantics. These are not the same abstraction with different UIs — they model different things.

### 4. The audit requirement is real, and mutable systems cannot meet it

A GitHub Issue's history is mutable. Comments can be edited. The edit history is visible to admins, but the original text is gone. For financial services, healthcare, or any regulated domain, an audit trail that can be edited — even with history — is not an audit trail. It is a log.

WorkItems' `AuditEntry` table is append-only. With the ledger module, every entry carries a SHA-256 digest chained to the previous entry (Certificate Transparency pattern). Tampering is detectable. Each entry can carry the actor's stated rationale, the policy version that governed their decision, and structured evidence — satisfying GDPR Article 22, EU AI Act Article 12, and financial audit requirements.

GitHub Issues cannot provide this. They were not designed to.

### 5. AI agent workflows need structured waiting

GitHub Issues were designed for human-to-human communication. AI agents need something different: a structured boundary where a machine hands work to a non-deterministic actor — human or agent — and then waits, with a deadline, for a response it can act on.

WorkItems was built for this. An AI pipeline creates a WorkItem and returns a `Uni<String>` that resolves when the WorkItem is completed via REST. The pipeline can set `expiresAt`, configure escalation, specify `candidateGroups`, and inspect the `resolution` JSON when the actor responds. The WorkItem is the contract between the deterministic machine and the non-deterministic actor.

There is no equivalent primitive in GitHub Issues.

---

## What GitHub Issues are genuinely good at

This is not an argument that GitHub Issues are bad. They are excellent at what they were designed for:

- **Developer workflow** — tracking bugs, feature requests, and tasks in the context of code
- **Community communication** — external contributors discussing changes with maintainers
- **PR linkage** — "this PR closes #42" is native, not bolted on
- **Project planning** — milestones, roadmaps, project boards
- **Public visibility** — the community sees what's being worked on

For all of this, GitHub Issues are superior to WorkItems. WorkItems has no milestone concept, no PR linkage, no project board. It is not trying to compete here.

---

## The decision rule

**Use GitHub Issues when:** the work is about what to build — bugs, features, technical debt, community requests.

**Use WorkItems when:** the work is a runtime obligation inside your running application — approvals, reviews, escalations, compliance checks, AI-to-human handoffs.

Most non-trivial applications need both. The integration module (`quarkus-workitems-issue-tracker`) lets you link them: a GitHub Issue spawns a WorkItem, and the WorkItem's completion closes the issue. The tracker carries the external context; the WorkItem enforces the operational SLA.

---

## The one-sentence version

GitHub Issues track what your team needs to build. WorkItems track what your running application needs from humans.
