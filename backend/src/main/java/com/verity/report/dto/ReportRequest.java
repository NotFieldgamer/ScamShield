package com.verity.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/** A user's dispute of a verdict. {@code claim} keys on the posting, not the verdict. */
public record ReportRequest(
        @NotNull(message = "postingId is required")
        UUID postingId,
        @NotNull(message = "claim is required")
        @Pattern(regexp = "FALSE_POSITIVE|CONFIRMED_SCAM",
                message = "claim must be FALSE_POSITIVE or CONFIRMED_SCAM")
        String claim) {
}
