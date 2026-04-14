# Quarkus Tarkus

Human-scale WorkItem lifecycle management for Quarkus applications.

Add `quarkus-tarkus` as a dependency and get a human task inbox with **expiry, delegation, escalation, priority, and audit trail** — usable standalone or with optional integrations for Quarkus-Flow, CaseHub, and Qhorus.

## The WorkItem

A `WorkItem` is a unit of work requiring human attention or judgment. It is deliberately *not* called a `Task` — the CNCF Serverless Workflow SDK (used by Quarkus-Flow) and CaseHub both have their own `Task` concepts. `WorkItem` is Tarkus's term for work that waits for a human.

```
Task (machine-controlled)  →  WorkItem (human-controlled)
milliseconds                   minutes to days
no assignee                    named assignee
no expiry                      deadline + escalation
no delegation                  delegation chain + audit
```

## Status

Early development — Phase 1 (core data model) not yet started.

See `docs/specs/2026-04-14-tarkus-design.md` for the full design.
See `HANDOFF.md` for where to start.

## Build

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn clean install
JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl runtime
```

## License

Apache 2.0
