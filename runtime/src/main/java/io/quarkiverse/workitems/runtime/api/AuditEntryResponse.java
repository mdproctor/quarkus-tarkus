package io.quarkiverse.tarkus.runtime.api;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryResponse(
        UUID id,
        String event,
        String actor,
        String detail,
        Instant occurredAt) {
}
