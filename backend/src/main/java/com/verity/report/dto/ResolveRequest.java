package com.verity.report.dto;

import jakarta.validation.constraints.NotNull;

/** A moderator's decision on a report: confirm the claim, or reject it. */
public record ResolveRequest(
        @NotNull(message = "decision is required")
        Decision decision) {

    public enum Decision {
        CONFIRM,
        REJECT
    }

    public boolean confirm() {
        return decision == Decision.CONFIRM;
    }
}
