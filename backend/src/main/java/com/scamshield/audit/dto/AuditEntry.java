package com.scamshield.audit.dto;

import java.time.Instant;

/** One row of the append-only audit log, as exposed to admins. */
public record AuditEntry(
        Long id,
        Long actorId,
        String action,
        String targetType,
        String targetId,
        String ip,
        Instant createdAt) {
}
