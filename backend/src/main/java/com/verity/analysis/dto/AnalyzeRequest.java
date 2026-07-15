package com.verity.analysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public analyze request. {@code kind} is POSTING (default) or MESSAGE; anything else is
 * normalized to POSTING by the service.
 */
public record AnalyzeRequest(
        @NotBlank(message = "text is required")
        @Size(max = 50_000, message = "text must be at most 50000 characters")
        String text,
        String kind) {
}
